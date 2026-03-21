package com.aiaps.service.mrp;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.base.BasBomDetail;
import com.aiaps.domain.base.BasBomHead;
import com.aiaps.domain.base.BasCategoryBom;
import com.aiaps.domain.base.BasMaterial;
import com.aiaps.domain.base.BasSpecFormula;
import com.aiaps.domain.demand.DemDemandLine;
import com.aiaps.domain.mrp.MrpPegging;
import com.aiaps.domain.mrp.MrpPlanOrder;
import com.aiaps.domain.mrp.MrpRunLog;
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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            MrpContext context = buildContext();

            List<DemDemandLine> demands = collectDemands();
            runLog.setDemandCount(demands.size());

            Map<String, Integer> llcMap = calculateLlc(context);

            Map<String, List<DemDemandLine>> demandGroups = demands.stream()
                    .collect(Collectors.groupingBy(d ->
                            d.getPrdtId() + "|" + nullSafe(d.getPatName()) + "|" + nullSafe(d.getPaName())));

            List<Map.Entry<String, List<DemDemandLine>>> sortedGroups = demandGroups.entrySet().stream()
                    .sorted((a, b) -> {
                        String keyA = "M:" + a.getValue().get(0).getPrdtId();
                        String keyB = "M:" + b.getValue().get(0).getPrdtId();
                        int llcA = llcMap.getOrDefault(keyA, 0);
                        int llcB = llcMap.getOrDefault(keyB, 0);
                        return Integer.compare(llcA, llcB);
                    })
                    .collect(Collectors.toList());

            for (Map.Entry<String, List<DemDemandLine>> entry : sortedGroups) {
                List<DemDemandLine> groupDemands = entry.getValue();
                DemDemandLine sample = groupDemands.get(0);

                BigDecimal totalRequiredWeight = groupDemands.stream()
                        .map(d -> d.getRequiredWeight() != null ? d.getRequiredWeight() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal availableWeight = BigDecimal.ZERO;
                BigDecimal rawAvailable = stockMapper.selectAvailableWeight(
                        sample.getPrdtId(), sample.getPatName(), sample.getPaName());
                if (rawAvailable != null) {
                    availableWeight = rawAvailable;
                }

                BigDecimal netWeight = totalRequiredWeight.subtract(availableWeight);
                if (netWeight.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BasMaterial material = context.getMaterial(sample.getPrdtId());

                for (DemDemandLine demand : groupDemands) {
                    MrpContext.PlannedOrderDto order = new MrpContext.PlannedOrderDto();
                    order.setRunId(runLog.getRunId());
                    order.setPrdtId(demand.getPrdtId());
                    order.setPatName(demand.getPatName());
                    order.setPaName(demand.getPaName());
                    order.setGradeFlexible(demand.getGradeFlexible());
                    order.setOriginFlexible(demand.getOriginFlexible());
                    order.setOrderType("MFG");
                    order.setPlannedWeight(demand.getRequiredWeight());
                    order.setPlannedQty(demand.getRequiredQty());
                    order.setContractNo(demand.getContractNo());
                    order.setSourceDemandId(demand.getDemandId());
                    order.setSourceDemandLine(demand.getDemandLineId());
                    order.setDemandSource("DEMAND");
                    order.setOrderStatus("PLANNED");
                    order.setIsFirmed(false);
                    order.setCreatedTime(new Date());

                    explodeBom(context, order, material, runLog.getRunId());
                    context.addPlannedOrder(order);
                }
            }

            saveMrpResults(runLog.getRunId(), context.getPlannedOrders());

            runLog.setPlanOrderCount(context.getPlannedOrders().size());
            runLog.setRunStatus("COMPLETED");
            runLog.setEndTime(new Date());
            runLogMapper.updateById(runLog);

            log.info("MRP运算完成, runId={}, 需求数={}, 计划订单数={}",
                    runLog.getRunId(), demands.size(), planOrderCount);

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
                    MrpContext.PlannedOrderDto childOrder = new MrpContext.PlannedOrderDto();
                    childOrder.setRunId(runId);
                    childOrder.setPrdtId(detail.getChildPrdtId());
                    childOrder.setPatName(parentOrder.getPatName());
                    childOrder.setPaName(parentOrder.getPaName());
                    childOrder.setOrderType("MFG");
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
                    BigDecimal parentQty = parentOrder.getPlannedQty() != null ? parentOrder.getPlannedQty() : BigDecimal.ZERO;
                    childOrder.setPlannedQty(parentQty.multiply(qtyPer)
                            .multiply(BigDecimal.ONE.add(scrapRate))
                            .setScale(3, RoundingMode.HALF_UP));

                    context.addPlannedOrder(childOrder);
                }
            }
        }

        List<BasCategoryBom> catBoms = context.getCategoryBoms(material.getCategoryCode());
        if (catBoms != null && !catBoms.isEmpty()) {
            for (BasCategoryBom catBom : catBoms) {
                String formulaCode = catBom.getCalcFormulaCode();
                BasSpecFormula formula = context.getFormulaMap().get(formulaCode);

                MrpContext.PlannedOrderDto childOrder = new MrpContext.PlannedOrderDto();
                childOrder.setRunId(runId);
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
                childOrder.setRawCategoryCode(catBom.getChildCategory());
                childOrder.setBomLevel((parentOrder.getBomLevel() != null ? parentOrder.getBomLevel() : 0) + 1);
                childOrder.setCreatedTime(new Date());

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

                context.addPlannedOrder(childOrder);
            }
        }
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
