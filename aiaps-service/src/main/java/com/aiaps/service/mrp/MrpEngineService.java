package com.aiaps.service.mrp;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.aps.ApsNestingPool;
import com.aiaps.domain.base.BasBomDetail;
import com.aiaps.domain.base.BasBomHead;
import com.aiaps.domain.base.BasCategoryBom;
import com.aiaps.domain.base.BasMaterial;
import com.aiaps.domain.base.BasSpecFormula;
import com.aiaps.domain.demand.DemDemandLine;
import com.aiaps.domain.inventory.InvStock;
import com.aiaps.domain.mrp.MrpPegging;
import com.aiaps.domain.mrp.MrpPlanOrder;
import com.aiaps.domain.mrp.MrpRunLog;
import com.aiaps.mapper.aps.ApsNestingPoolMapper;
import com.aiaps.mapper.base.BasBomDetailMapper;
import com.aiaps.mapper.base.BasBomHeadMapper;
import com.aiaps.mapper.base.BasCategoryBomMapper;
import com.aiaps.mapper.base.BasMaterialMapper;
import com.aiaps.mapper.base.BasSpecFormulaMapper;
import com.aiaps.mapper.demand.DemDemandLineMapper;
import com.aiaps.mapper.inventory.InvStockMapper;
import com.aiaps.mapper.mrp.MrpPeggingMapper;
import com.aiaps.mapper.mrp.MrpPlanOrderMapper;
import com.aiaps.mapper.mrp.MrpRunLogMapper;
import com.aiaps.service.aps.NestingAutoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MrpEngineService {

    private final MrpRunLogMapper runLogMapper;
    private final MrpPlanOrderMapper planOrderMapper;
    private final MrpPeggingMapper peggingMapper;
    private final BasMaterialMapper materialMapper;
    private final BasBomHeadMapper bomHeadMapper;
    private final BasBomDetailMapper bomDetailMapper;
    private final BasCategoryBomMapper categoryBomMapper;
    private final BasSpecFormulaMapper specFormulaMapper;
    private final InvStockMapper stockMapper;
    private final DemDemandLineMapper demandLineMapper;
    private final DualBomLlcCalculator dualBomLlcCalculator;
    private final SpecCalculationEngine specCalculationEngine;
    private final ApsNestingPoolMapper nestingPoolMapper;

    @Autowired
    private NestingAutoService nestingAutoService;

    @Async
    public void runMrp(String runType, Integer horizonDays, String runBy) {
        MrpRunLog runLog = new MrpRunLog();
        runLog.setRunNo("MRP" + System.currentTimeMillis());
        runLog.setRunType(runType);
        runLog.setPlanHorizonDays(horizonDays);
        runLog.setRunStatus("RUNNING");
        runLog.setStartTime(new Date());
        runLog.setRunBy(runBy);
        runLog.setCreatedTime(new Date());
        runLogMapper.insert(runLog);

        try {
            // 1. Build context (materials, BOMs, formulas)
            MrpContext context = buildContext();

            // 2. Collect demands (OPEN/PARTIAL)
            List<DemDemandLine> demands = collectDemands();
            runLog.setDemandCount(demands.size());

            // 3. Calculate LLC
            Map<String, Integer> llcMap = calculateLlc(context);

            // 4. Group demands by (prdtId + patName + paName)
            Map<String, List<DemDemandLine>> demandGroups = demands.stream()
                    .collect(Collectors.groupingBy(d ->
                            d.getPrdtId() + "|" + nullSafe(d.getPatName()) + "|" + nullSafe(d.getPaName())));

            // 5. Sort groups by LLC
            List<Map.Entry<String, List<DemDemandLine>>> sortedGroups = demandGroups.entrySet().stream()
                    .sorted((a, b) -> {
                        String keyA = "M:" + a.getValue().get(0).getPrdtId();
                        String keyB = "M:" + b.getValue().get(0).getPrdtId();
                        int llcA = llcMap.getOrDefault(keyA, 0);
                        int llcB = llcMap.getOrDefault(keyB, 0);
                        return Integer.compare(llcA, llcB);
                    })
                    .collect(Collectors.toList());

            // 6. Process each group
            for (Map.Entry<String, List<DemDemandLine>> entry : sortedGroups) {
                List<DemDemandLine> groupDemands = entry.getValue();
                DemDemandLine sample = groupDemands.get(0);

                // 6a. Sum total required weight
                BigDecimal totalRequiredWeight = groupDemands.stream()
                        .map(d -> d.getRequiredWeight() != null ? d.getRequiredWeight() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // 6b. Get available stock weight
                BigDecimal availableWeight = BigDecimal.ZERO;
                BigDecimal rawAvailable = stockMapper.selectAvailableWeight(
                        sample.getPrdtId(), sample.getPatName(), sample.getPaName());
                if (rawAvailable != null) {
                    availableWeight = rawAvailable;
                }

                // Try to match specific stock
                if (availableWeight.compareTo(BigDecimal.ZERO) > 0) {
                    List<InvStock> matchedStocks = stockMapper.selectAvailable(
                        sample.getPrdtId(), sample.getPatName(), sample.getPaName());
                    if (matchedStocks != null && !matchedStocks.isEmpty()) {
                        InvStock firstMatch = matchedStocks.get(0);
                        log.debug("Matched stock {} for prdtId={}, patName={}", 
                            firstMatch.getStockId(), sample.getPrdtId(), sample.getPatName());
                    }
                }

                // 6c. Calculate net = total - available
                BigDecimal netWeight = totalRequiredWeight.subtract(availableWeight).max(BigDecimal.ZERO);

                if (netWeight.compareTo(BigDecimal.ZERO) <= 0) {
                    // Fully covered by stock — create pegging for stock coverage
                    for (DemDemandLine demand : groupDemands) {
                        BigDecimal demandWeight = demand.getRequiredWeight() != null
                                ? demand.getRequiredWeight() : BigDecimal.ZERO;
                        createPegging(runLog.getRunId(), demand.getDemandLineId(),
                                "STOCK", null, demandWeight);
                    }
                    continue;
                }

                BasMaterial material = context.getMaterial(sample.getPrdtId());

                // 6d. Apply lot sizing
                netWeight = applyLotSizing(netWeight, material);

                // 6e. Calculate lead time offset for start date
                Date requiredDate = sample.getRequiredDate();
                Date plannedStartDate = calcLeadTimeOffset(requiredDate, material);
                Date plannedEndDate = requiredDate != null ? requiredDate : new Date();

                // 6f. Determine order type
                String orderType = determineOrderType(material);

                // 6g-i. Distribute net proportionally to demands, create plan orders + pegging
                // If stock partially covers, create pegging for the stock-covered portion
                if (availableWeight.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal stockCoverRemaining = availableWeight;
                    for (DemDemandLine demand : groupDemands) {
                        BigDecimal demandWeight = demand.getRequiredWeight() != null
                                ? demand.getRequiredWeight() : BigDecimal.ZERO;
                        BigDecimal coveredByStock = demandWeight.min(stockCoverRemaining);
                        if (coveredByStock.compareTo(BigDecimal.ZERO) > 0) {
                            createPegging(runLog.getRunId(), demand.getDemandLineId(),
                                    "STOCK", null, coveredByStock);
                            stockCoverRemaining = stockCoverRemaining.subtract(coveredByStock);
                        }
                    }
                }

                for (DemDemandLine demand : groupDemands) {
                    BigDecimal demandWeight = demand.getRequiredWeight() != null
                            ? demand.getRequiredWeight() : BigDecimal.ZERO;
                    BigDecimal proportion = totalRequiredWeight.compareTo(BigDecimal.ZERO) > 0
                            ? demandWeight.divide(totalRequiredWeight, 6, RoundingMode.HALF_UP)
                            : BigDecimal.ONE;
                    BigDecimal orderWeight = netWeight.multiply(proportion)
                            .setScale(3, RoundingMode.HALF_UP);
                    if (orderWeight.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }

                    int seq = context.getPlannedOrders().size() + 1;
                    String planOrderNo = generatePlanOrderNo(runLog, seq);

                    MrpContext.PlannedOrderDto order = new MrpContext.PlannedOrderDto();
                    order.setRunId(runLog.getRunId());
                    order.setPlanOrderNo(planOrderNo);
                    order.setPrdtId(demand.getPrdtId());
                    order.setPatName(demand.getPatName());
                    order.setPaName(demand.getPaName());
                    order.setGradeFlexible(demand.getGradeFlexible());
                    order.setOriginFlexible(demand.getOriginFlexible());
                    order.setOrderType(orderType);
                    order.setPlannedWeight(orderWeight);
                    order.setPlannedQty(demand.getRequiredQty());
                    order.setPlannedStartDate(plannedStartDate);
                    order.setPlannedEndDate(plannedEndDate);
                    order.setContractNo(demand.getContractNo());
                    order.setSourceDemandId(demand.getDemandId());
                    order.setSourceDemandLine(demand.getDemandLineId());
                    order.setDemandSource("DEMAND");
                    order.setOrderStatus("PLANNED");
                    order.setIsFirmed(false);
                    order.setCreatedTime(new Date());

                    context.addPlannedOrder(order);

                    // Create pegging for the plan order supply
                    createPegging(runLog.getRunId(), demand.getDemandLineId(),
                            "PLAN_ORDER", null, orderWeight);

                    // 6j. Explode BOM for MFG orders
                    if ("MFG".equals(orderType)) {
                        explodeBom(context, order, material, runLog.getRunId());
                    }
                }
            }

            // 7. Save MRP results + pegging
            saveMrpResults(runLog.getRunId(), context.getPlannedOrders());

            // Step 3.5: 套料自动合并优化
            try {
                List<String> groupKeys = nestingAutoService.getPoolGroups("PENDING").stream()
                        .map(g -> g.getGroupKey()).distinct().collect(Collectors.toList());
                int nestingCount = 0;
                for (String gk : groupKeys) {
                    try {
                        nestingAutoService.autoOptimize(gk);
                        nestingCount++;
                    } catch (Exception ne) {
                        log.warn("套料优化异常, groupKey={}: {}", gk, ne.getMessage());
                    }
                }
                log.info("Step 3.5: 套料优化完成, {}个组", nestingCount);
            } catch (Exception ne) {
                log.warn("套料自动优化阶段异常: {}", ne.getMessage());
            }

            // 8. Check safety stock
            checkSafetyStock(context, runLog.getRunId());

            // 9. Check exceptions
            List<String> exceptions = checkExceptions(context);
            if (!exceptions.isEmpty()) {
                log.warn("MRP exceptions ({}): {}", exceptions.size(),
                        String.join("; ", exceptions));
                String excMsg = "异常数: " + exceptions.size() + "; " +
                        String.join("; ", exceptions);
                if (excMsg.length() > 500) {
                    excMsg = excMsg.substring(0, 500);
                }
                runLog.setErrorMessage(excMsg);
            }

            // 10. Update run log with counts
            runLog.setPlanOrderCount(context.getPlannedOrders().size());
            runLog.setRunStatus("COMPLETED");
            runLog.setEndTime(new Date());
            runLogMapper.updateById(runLog);

            log.info("MRP运算完成, runId={}, 需求数={}, 计划订单数={}",
                    runLog.getRunId(), demands.size(), context.getPlannedOrders().size());

        } catch (Exception e) {
            log.error("MRP运算异常, runId={}", runLog.getRunId(), e);
            runLog.setRunStatus("ERROR");
            runLog.setErrorMessage(e.getMessage() != null
                    ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500))
                    : "Unknown error");
            runLog.setEndTime(new Date());
            runLogMapper.updateById(runLog);
        }
    }

    public MrpRunLog getRunProgress(Long runId) {
        return runLogMapper.selectById(runId);
    }

    @Transactional
    public void cancelRun(Long runId) {
        MrpRunLog runLog = runLogMapper.selectById(runId);
        if (runLog != null) {
            runLog.setRunStatus("CANCELLED");
            runLog.setEndTime(new Date());
            runLogMapper.updateById(runLog);
        }
    }

    @Transactional
    public void confirmPlanOrders(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("请选择要确认的计划订单");
        }
        for (Long id : ids) {
            MrpPlanOrder order = planOrderMapper.selectById(id);
            if (order == null) continue;
            if (!"PLANNED".equals(order.getOrderStatus())) {
                throw new BizException("计划订单 " + order.getPlanOrderNo() + " 状态不是PLANNED，无法确认");
            }
            order.setOrderStatus("CONFIRMED");
            planOrderMapper.updateById(order);
        }
    }

    @Transactional
    public void cancelPlanOrders(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("请选择要取消的计划订单");
        }
        for (Long id : ids) {
            MrpPlanOrder order = planOrderMapper.selectById(id);
            if (order == null) continue;
            order.setOrderStatus("CANCELLED");
            planOrderMapper.updateById(order);
        }
    }

    @Transactional
    public void saveMrpResults(Long runId, List<MrpContext.PlannedOrderDto> plannedOrders) {
        for (MrpContext.PlannedOrderDto dto : plannedOrders) {
            MrpPlanOrder order = new MrpPlanOrder();
            order.setRunId(runId);
            order.setPlanOrderNo(dto.getPlanOrderNo());
            order.setPrdtId(dto.getPrdtId());
            order.setPatName(dto.getPatName());
            order.setPaName(dto.getPaName());
            order.setOrderType(dto.getOrderType());
            order.setPlannedQty(dto.getPlannedQty());
            order.setPlannedWeight(dto.getPlannedWeight());
            order.setPlannedStartDate(dto.getPlannedStartDate());
            order.setPlannedEndDate(dto.getPlannedEndDate());
            order.setDemandSource(dto.getDemandSource());
            order.setSourceDemandId(dto.getSourceDemandId());
            order.setSourceDemandLine(dto.getSourceDemandLine());
            order.setContractNo(dto.getContractNo());
            order.setIsCategoryBom(dto.getIsCategoryBom());
            order.setRawCategoryCode(dto.getRawCategoryCode());
            order.setRawWidthMin(dto.getRawWidthMin());
            order.setRawWidthMax(dto.getRawWidthMax());
            order.setRawThicknessMin(dto.getRawThicknessMin());
            order.setRawThicknessMax(dto.getRawThicknessMax());
            order.setRawGradeCode(dto.getRawGradeCode());
            order.setRawOriginCode(dto.getRawOriginCode());
            order.setMatchedStockId(dto.getMatchedStockId());
            order.setMatchedCoilNo(dto.getMatchedCoilNo());
            order.setMatchedGradeCode(dto.getMatchedGradeCode());
            order.setMatchedOriginCode(dto.getMatchedOriginCode());
            order.setMatchStatus(dto.getMatchStatus());
            order.setOrderStatus("PLANNED");
            order.setIsFirmed(false);
            order.setCreatedTime(new Date());
            planOrderMapper.insert(order);
        }
    }

    private MrpContext buildContext() {
        MrpContext context = new MrpContext();

        List<BasMaterial> materials = materialMapper.selectList(
                new LambdaQueryWrapper<BasMaterial>().eq(BasMaterial::getIsActive, true));
        for (BasMaterial m : materials) {
            context.getMaterialMap().put(m.getPrdtId(), m);
        }

        List<BasBomHead> boms = bomHeadMapper.selectAllActiveWithDetails();
        for (BasBomHead bom : boms) {
            if (Boolean.TRUE.equals(bom.getIsDefault())) {
                context.getBomMap().put(bom.getPrdtId(), bom);
            }
        }

        context.setCategoryBoms(categoryBomMapper.selectAllActive());

        List<BasSpecFormula> formulas = specFormulaMapper.selectList(
                new LambdaQueryWrapper<BasSpecFormula>().eq(BasSpecFormula::getIsActive, true));
        for (BasSpecFormula f : formulas) {
            context.getFormulaMap().put(f.getFormulaCode(), f);
        }

        return context;
    }

    private List<DemDemandLine> collectDemands() {
        LambdaQueryWrapper<DemDemandLine> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DemDemandLine::getLineStatus, "OPEN", "PARTIAL");
        return demandLineMapper.selectList(wrapper);
    }

    private Map<String, Integer> calculateLlc(MrpContext context) {
        List<DualBomLlcCalculator.BomRelation> discreteRelations = new ArrayList<>();
        for (BasBomHead bom : context.getBomMap().values()) {
            if (bom.getDetails() != null) {
                for (BasBomDetail detail : bom.getDetails()) {
                    DualBomLlcCalculator.BomRelation rel = new DualBomLlcCalculator.BomRelation();
                    rel.parentMaterialId = bom.getPrdtId();
                    rel.childMaterialId = detail.getChildPrdtId();
                    discreteRelations.add(rel);
                }
            } else {
                List<BasBomDetail> details = bomDetailMapper.selectByBomId(bom.getBomId());
                for (BasBomDetail detail : details) {
                    DualBomLlcCalculator.BomRelation rel = new DualBomLlcCalculator.BomRelation();
                    rel.parentMaterialId = bom.getPrdtId();
                    rel.childMaterialId = detail.getChildPrdtId();
                    discreteRelations.add(rel);
                }
            }
        }

        Map<Long, String> materialCategoryMap = new HashMap<>();
        for (Map.Entry<Long, BasMaterial> entry : context.getMaterialMap().entrySet()) {
            materialCategoryMap.put(entry.getKey(), entry.getValue().getCategoryCode());
        }

        return dualBomLlcCalculator.calculate(discreteRelations, context.getCategoryBoms(), materialCategoryMap);
    }

    private void explodeBom(MrpContext context, MrpContext.PlannedOrderDto parentOrder,
                            BasMaterial material, Long runId) {
        if (material == null) {
            return;
        }

        BasBomHead bom = context.getDefaultBom(material.getPrdtId());
        if (bom != null) {
            List<BasBomDetail> details = bom.getDetails();
            if (details == null) {
                details = bomDetailMapper.selectByBomId(bom.getBomId());
            }
            if (details != null) {
                for (BasBomDetail detail : details) {
                    BasMaterial childMaterial = context.getMaterial(detail.getChildPrdtId());

                    MrpContext.PlannedOrderDto childOrder = new MrpContext.PlannedOrderDto();
                    childOrder.setRunId(runId);
                    childOrder.setPlanOrderNo(generatePlanOrderNo(
                            buildTempRunLog(runId), context.getPlannedOrders().size() + 1));
                    childOrder.setPrdtId(detail.getChildPrdtId());
                    childOrder.setPatName(parentOrder.getPatName());
                    childOrder.setPaName(parentOrder.getPaName());
                    childOrder.setOrderType(determineOrderType(childMaterial));
                    childOrder.setContractNo(parentOrder.getContractNo());
                    childOrder.setDemandSource("BOM_EXPLODE");
                    childOrder.setSourceDemandId(parentOrder.getSourceDemandId());
                    childOrder.setSourceDemandLine(parentOrder.getSourceDemandLine());
                    childOrder.setOrderStatus("PLANNED");
                    childOrder.setIsFirmed(false);
                    childOrder.setIsCategoryBom(false);
                    childOrder.setBomLevel((parentOrder.getBomLevel() != null ? parentOrder.getBomLevel() : 0) + 1);
                    childOrder.setCreatedTime(new Date());

                    BigDecimal qtyPer = detail.getQtyPer() != null ? detail.getQtyPer() : BigDecimal.ONE;
                    BigDecimal scrapRate = detail.getScrapRate() != null ? detail.getScrapRate() : BigDecimal.ZERO;
                    BigDecimal parentQty = parentOrder.getPlannedQty() != null
                            ? parentOrder.getPlannedQty() : BigDecimal.ZERO;
                    childOrder.setPlannedQty(parentQty.multiply(qtyPer)
                            .multiply(BigDecimal.ONE.add(scrapRate))
                            .setScale(3, RoundingMode.HALF_UP));

                    BigDecimal parentWeight = parentOrder.getPlannedWeight() != null
                            ? parentOrder.getPlannedWeight() : BigDecimal.ZERO;
                    BigDecimal childWeight = parentWeight.multiply(qtyPer)
                            .multiply(BigDecimal.ONE.add(scrapRate))
                            .setScale(3, RoundingMode.HALF_UP);
                    childOrder.setPlannedWeight(childWeight);

                    childOrder.setPlannedStartDate(calcLeadTimeOffset(
                            parentOrder.getPlannedStartDate(), childMaterial));
                    childOrder.setPlannedEndDate(parentOrder.getPlannedStartDate());

                    context.addPlannedOrder(childOrder);
                }
            }
        }

        List<BasCategoryBom> catBoms = context.getCategoryBoms(material.getCategoryCode());
        if (catBoms != null && !catBoms.isEmpty()) {
            for (BasCategoryBom catBom : catBoms) {
                String childCategoryCode = catBom.getChildCategory();
                String formulaCode = catBom.getCalcFormulaCode();
                BasSpecFormula formula = context.getFormulaMap().get(formulaCode);

                BigDecimal childThickness = null;
                BigDecimal childWidth = null;
                BigDecimal childWeight = null;
                BigDecimal childQty = parentOrder.getPlannedQty();

                if (formula != null) {
                    BigDecimal qty = parentOrder.getPlannedWeight() != null
                            ? parentOrder.getPlannedWeight() : BigDecimal.ZERO;
                    SpecCalculationEngine.RawMaterialSpec spec =
                            specCalculationEngine.calculate(material, qty, formula, catBom);
                    childThickness = spec.getThicknessMin();
                    childWidth = spec.getWidthMin();
                    childWeight = spec.getWeightPerUnit();
                }

                if (nestingAutoService != null && nestingAutoService.isNestingCandidate(childCategoryCode)) {
                    ApsNestingPool pool = new ApsNestingPool();
                    pool.setPoolNo("NP-" + System.currentTimeMillis());
                    pool.setDemandLineId(parentOrder.getSourceDemandLine());
                    pool.setCategoryCode(childCategoryCode);
                    pool.setPatName(parentOrder.getPatName());
                    pool.setPaName(parentOrder.getPaName());
                    pool.setThickness(childThickness);
                    pool.setWidth(childWidth);
                    pool.setRequiredWeight(childWeight);
                    pool.setRequiredQty(childQty);
                    pool.setRemainingWeight(childWeight);
                    pool.setRemainingQty(childQty);
                    pool.setContractNo(parentOrder.getContractNo());
                    pool.setAllowMerge(true);
                    pool.setPoolStatus("PENDING");
                    pool.setRunId(runId);
                    pool.setCreatedTime(new Date());
                    String groupKey = "CUT_PART".equals(childCategoryCode)
                            ? "CUT|" + childThickness + "|" + parentOrder.getPatName()
                            : ("STRIP".equals(childCategoryCode) ? "SLIT" : "LEVEL")
                              + "|" + childThickness + "|" + parentOrder.getPatName() + "|1500";
                    pool.setMergeGroupKey(groupKey);
                    nestingPoolMapper.insert(pool);
                } else {
                    MrpContext.PlannedOrderDto childOrder = new MrpContext.PlannedOrderDto();
                    childOrder.setRunId(runId);
                    childOrder.setPlanOrderNo(generatePlanOrderNo(
                            buildTempRunLog(runId), context.getPlannedOrders().size() + 1));
                    childOrder.setPatName(parentOrder.getPatName());
                    childOrder.setPaName(parentOrder.getPaName());
                    childOrder.setOrderType("MFG");
                    childOrder.setContractNo(parentOrder.getContractNo());
                    childOrder.setDemandSource("CAT_BOM_EXPLODE");
                    childOrder.setSourceDemandId(parentOrder.getSourceDemandId());
                    childOrder.setSourceDemandLine(parentOrder.getSourceDemandLine());
                    childOrder.setOrderStatus("PLANNED");
                    childOrder.setIsFirmed(false);
                    childOrder.setIsCategoryBom(true);
                    childOrder.setRawCategoryCode(childCategoryCode);
                    childOrder.setBomLevel((parentOrder.getBomLevel() != null ? parentOrder.getBomLevel() : 0) + 1);
                    childOrder.setCreatedTime(new Date());
                    childOrder.setPlannedStartDate(parentOrder.getPlannedStartDate());
                    childOrder.setPlannedEndDate(parentOrder.getPlannedEndDate());

                    if (formula != null) {
                        BigDecimal qty = parentOrder.getPlannedWeight() != null
                                ? parentOrder.getPlannedWeight() : BigDecimal.ZERO;
                        SpecCalculationEngine.RawMaterialSpec spec =
                                specCalculationEngine.calculate(material, qty, formula, catBom);
                        childOrder.setRawWidthMin(spec.getWidthMin());
                        childOrder.setRawWidthMax(spec.getWidthMax());
                        childOrder.setRawThicknessMin(spec.getThicknessMin());
                        childOrder.setRawThicknessMax(spec.getThicknessMax());
                        childOrder.setPlannedWeight(spec.getWeightPerUnit());
                    }

                    // For category BOM child orders, try to match stock
                    if (Boolean.TRUE.equals(childOrder.getIsCategoryBom()) && childOrder.getRawCategoryCode() != null) {
                        List<InvStock> potentialMatches = stockMapper.selectByMaterialWithAnyGradeOrigin(childOrder.getPrdtId());
                        if (potentialMatches != null) {
                            for (InvStock ps : potentialMatches) {
                                if (childOrder.getPatName() != null && childOrder.getPatName().equals(ps.getPatName())) {
                                    childOrder.setMatchedStockId(ps.getStockId());
                                    childOrder.setMatchedCoilNo(ps.getResNo());
                                    childOrder.setMatchedGradeCode(ps.getPatName());
                                    childOrder.setMatchedOriginCode(ps.getPaName());
                                    childOrder.setMatchStatus("MATCHED");
                                    break;
                                }
                            }
                        }
                        if (childOrder.getMatchStatus() == null) {
                            childOrder.setMatchStatus("UNMATCHED");
                        }
                    }

                    context.addPlannedOrder(childOrder);
                }
            }
        }
    }

    private BigDecimal applyLotSizing(BigDecimal netWeight, BasMaterial material) {
        if (material == null) return netWeight;
        String policy = material.getLotPolicy();
        if (policy == null || "LFL".equals(policy)) return netWeight;

        if ("FOQ".equals(policy) && material.getFixedLotQty() != null
                && material.getFixedLotQty().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal lots = netWeight.divide(material.getFixedLotQty(), 0, RoundingMode.CEILING);
            netWeight = lots.multiply(material.getFixedLotQty());
        }

        if (material.getMinOrderQty() != null && netWeight.compareTo(material.getMinOrderQty()) < 0) {
            netWeight = material.getMinOrderQty();
        }

        if (material.getLotMultiple() != null
                && material.getLotMultiple().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal multiples = netWeight.divide(material.getLotMultiple(), 0, RoundingMode.CEILING);
            netWeight = multiples.multiply(material.getLotMultiple());
        }

        return netWeight;
    }

    private Date calcLeadTimeOffset(Date requiredDate, BasMaterial material) {
        if (requiredDate == null) return new Date();
        int leadDays = (material != null && material.getLeadTimeDays() != null)
                ? material.getLeadTimeDays() : 0;
        int safetyDays = (material != null && material.getSafetyLeadDays() != null)
                ? material.getSafetyLeadDays() : 0;
        Calendar cal = Calendar.getInstance();
        cal.setTime(requiredDate);
        cal.add(Calendar.DAY_OF_MONTH, -(leadDays + safetyDays));
        return cal.getTime();
    }

    private String generatePlanOrderNo(MrpRunLog runLog, int seq) {
        return String.format("PO-%d-%04d", runLog.getRunId(), seq);
    }

    private String determineOrderType(BasMaterial material) {
        if (material == null) return "MFG";
        String procType = material.getProcurementType();
        if ("P".equals(procType)) return "PUR";
        if ("O".equals(procType)) return "SUB";
        return "MFG";
    }

    private void createPegging(Long runId, Long demandLineId, String supplyType,
                                Long supplyId, BigDecimal weight) {
        MrpPegging peg = new MrpPegging();
        peg.setRunId(runId);
        peg.setDemandType("DEMAND");
        peg.setDemandId(demandLineId);
        peg.setSupplyType(supplyType);
        peg.setSupplyId(supplyId);
        peg.setPeggedQty(weight);
        peg.setPeggedWeight(weight);
        peggingMapper.insert(peg);
    }

    private void checkSafetyStock(MrpContext context, Long runId) {
        for (BasMaterial material : context.getMaterialMap().values()) {
            if (material.getSafetyStockQty() == null
                    || material.getSafetyStockQty().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            // Simplified check — flag for future SSK plan order creation
        }
    }

    private List<String> checkExceptions(MrpContext context) {
        List<String> exceptions = new ArrayList<>();
        for (MrpContext.PlannedOrderDto order : context.getPlannedOrders()) {
            if (order.getPlannedStartDate() != null && order.getPlannedStartDate().before(new Date())) {
                exceptions.add("PO " + order.getPlanOrderNo() + ": 提前期不足, 计划开始日已过期");
            }
        }
        return exceptions;
    }

    private MrpRunLog buildTempRunLog(Long runId) {
        MrpRunLog temp = new MrpRunLog();
        temp.setRunId(runId);
        return temp;
    }

    private MrpPlanOrder convertToEntity(MrpContext.PlannedOrderDto dto) {
        MrpPlanOrder po = new MrpPlanOrder();
        po.setRunId(dto.getRunId());
        po.setPlanOrderNo(dto.getPlanOrderNo());
        po.setPrdtId(dto.getPrdtId());
        po.setPatName(dto.getPatName());
        po.setPaName(dto.getPaName());
        po.setGradeFlexible(dto.getGradeFlexible());
        po.setOriginFlexible(dto.getOriginFlexible());
        po.setOrderType(dto.getOrderType());
        po.setPlannedQty(dto.getPlannedQty());
        po.setPlannedWeight(dto.getPlannedWeight());
        po.setPlannedStartDate(dto.getPlannedStartDate());
        po.setPlannedEndDate(dto.getPlannedEndDate());
        po.setDemandSource(dto.getDemandSource());
        po.setSourceDemandId(dto.getSourceDemandId());
        po.setSourceDemandLine(dto.getSourceDemandLine());
        po.setParentPlanOrder(dto.getParentPlanOrder());
        po.setBomLevel(dto.getBomLevel());
        po.setOrderStatus(dto.getOrderStatus());
        po.setIsFirmed(dto.getIsFirmed());
        po.setContractNo(dto.getContractNo());
        po.setPlanLength(dto.getPlanLength());
        po.setLengthType(dto.getLengthType());
        po.setLengthDisplay(dto.getLengthDisplay());
        po.setIsCategoryBom(dto.getIsCategoryBom());
        po.setRawCategoryCode(dto.getRawCategoryCode());
        po.setRawWidthMin(dto.getRawWidthMin());
        po.setRawWidthMax(dto.getRawWidthMax());
        po.setRawThicknessMin(dto.getRawThicknessMin());
        po.setRawThicknessMax(dto.getRawThicknessMax());
        po.setRawGradeCode(dto.getRawGradeCode());
        po.setRawOriginCode(dto.getRawOriginCode());
        po.setRawGradeFlexible(dto.getRawGradeFlexible());
        po.setRawOriginFlexible(dto.getRawOriginFlexible());
        po.setMatchedMaterialId(dto.getMatchedMaterialId());
        po.setMatchedStockId(dto.getMatchedStockId());
        po.setMatchedCoilNo(dto.getMatchedCoilNo());
        po.setMatchedGradeCode(dto.getMatchedGradeCode());
        po.setMatchedOriginCode(dto.getMatchedOriginCode());
        po.setMatchStatus(dto.getMatchStatus());
        po.setRemark(dto.getRemark());
        po.setCreatedTime(dto.getCreatedTime());
        return po;
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
