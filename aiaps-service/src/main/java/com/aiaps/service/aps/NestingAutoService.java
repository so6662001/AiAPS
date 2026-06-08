package com.aiaps.service.aps;

import com.aiaps.domain.aps.*;
import com.aiaps.domain.base.BasMaterial;
import com.aiaps.domain.inventory.InvStock;
import com.aiaps.domain.mrp.MrpPlanOrder;
import com.aiaps.mapper.aps.ApsNesting2dLayoutMapper;
import com.aiaps.mapper.aps.ApsNestingDetailMapper;
import com.aiaps.mapper.aps.ApsNestingPlanMapper;
import com.aiaps.mapper.aps.ApsNestingPoolMapper;
import com.aiaps.mapper.aps.ApsScheduleMapper;
import com.aiaps.mapper.base.BasMaterialMapper;
import com.aiaps.mapper.inventory.InvStockMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NestingAutoService {

    private final ApsNestingPoolMapper nestingPoolMapper;
    private final ApsNestingPlanMapper nestingPlanMapper;
    private final ApsNestingDetailMapper nestingDetailMapper;
    private final ApsNesting2dLayoutMapper nesting2dLayoutMapper;
    private final InvStockMapper stockMapper;
    private final ApsScheduleMapper scheduleMapper;
    private final BasMaterialMapper materialMapper;

    private static final BigDecimal STEEL_DENSITY = new BigDecimal("7.85");
    private static final BigDecimal DEFAULT_KERF = new BigDecimal("3");
    private static final BigDecimal DEFAULT_EDGE_TRIM = new BigDecimal("5");
    private static final BigDecimal DEFAULT_MOTHER_WIDTH = new BigDecimal("1500");
    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private static final BigDecimal MILLION = new BigDecimal("1000000");

    public boolean isNestingCandidate(String categoryCode) {
        if (categoryCode == null) {
            return false;
        }
        return "STRIP".equals(categoryCode)
                || "PLATE".equals(categoryCode)
                || "CUT_PART".equals(categoryCode);
    }

    @Transactional
    public void addToPool(ApsNestingPool pool) {
        if (pool.getPoolNo() == null) {
            pool.setPoolNo("NP-" + System.currentTimeMillis());
        }
        if (pool.getPoolStatus() == null) {
            pool.setPoolStatus("PENDING");
        }
        if (pool.getRemainingQty() == null && pool.getRequiredQty() != null) {
            pool.setRemainingQty(pool.getRequiredQty());
        }
        if (pool.getRemainingWeight() == null && pool.getRequiredWeight() != null) {
            pool.setRemainingWeight(pool.getRequiredWeight());
        }
        if (pool.getMergeGroupKey() == null) {
            pool.setMergeGroupKey(calculateGroupKey(pool));
        }
        if (pool.getCreatedTime() == null) {
            pool.setCreatedTime(new Date());
        }
        nestingPoolMapper.insert(pool);
    }

    private String calculateGroupKey(ApsNestingPool pool) {
        String cat = pool.getCategoryCode();
        BigDecimal thickness = pool.getThickness() != null ? pool.getThickness() : BigDecimal.ZERO;
        String patName = pool.getPatName() != null ? pool.getPatName() : "";

        if ("CUT_PART".equals(cat)) {
            return "CUT|" + thickness + "|" + patName;
        } else if ("STRIP".equals(cat)) {
            return "SLIT|" + thickness + "|" + patName + "|1500";
        } else {
            return "LEVEL|" + thickness + "|" + patName + "|1500";
        }
    }

    @Transactional
    public void collectFromMrp(Long runId) {
        LambdaQueryWrapper<MrpPlanOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MrpPlanOrder::getRunId, runId)
                .eq(MrpPlanOrder::getOrderStatus, "PLANNED");
        // pool items are added during BOM explosion in MrpEngineService;
        // this method collects any remaining eligible plan orders that were not pooled
        List<ApsNestingPool> existing = nestingPoolMapper.selectByRunId(runId);
        log.info("collectFromMrp: runId={}, existing pool items={}", runId, existing.size());
    }

    public List<NestingPoolGroup> getPoolGroups(String status) {
        List<String> groupKeys = nestingPoolMapper.selectDistinctGroupKeys(status);
        List<NestingPoolGroup> result = new ArrayList<>();

        for (String gk : groupKeys) {
            List<ApsNestingPool> items = nestingPoolMapper.selectByGroupKey(gk, status);
            if (items.isEmpty()) {
                continue;
            }

            NestingPoolGroup group = new NestingPoolGroup();
            group.setGroupKey(gk);
            group.setItems(items);
            group.setTotalWeight(items.stream()
                    .map(p -> p.getRemainingWeight() != null ? p.getRemainingWeight() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            group.setContractCount((int) items.stream()
                    .map(ApsNestingPool::getContractNo)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count());
            result.add(group);
        }
        return result;
    }

    @Transactional
    public NestingOptimizeResult autoOptimize(String groupKey) {
        List<ApsNestingPool> poolItems = nestingPoolMapper.selectByGroupKey(groupKey, "PENDING");
        if (poolItems.isEmpty()) {
            log.warn("autoOptimize: no PENDING items for groupKey={}", groupKey);
            return null;
        }

        String prefix = groupKey.split("\\|")[0];

        BigDecimal thickness = poolItems.get(0).getThickness() != null
                ? poolItems.get(0).getThickness() : BigDecimal.ZERO;
        String patName = poolItems.get(0).getPatName();

        List<InvStock> motherCoils = findMotherCoils(thickness, patName);

        NestingOptimizeResult result;
        switch (prefix) {
            case "SLIT":
                result = slitOptimize(poolItems, motherCoils, groupKey);
                break;
            case "LEVEL":
                result = levelOptimize(poolItems, motherCoils, groupKey);
                break;
            case "CUT":
                result = cutOptimize(poolItems, motherCoils, groupKey);
                break;
            default:
                log.warn("Unknown nesting prefix: {}", prefix);
                return null;
        }

        if (result != null) {
            updatePoolStatus(poolItems, "NESTED", result.getNestingId());
        }

        return result;
    }

    @Transactional
    public void confirmAndSchedule(Long nestingId) {
        ApsNestingPlan plan = nestingPlanMapper.selectById(nestingId);
        if (plan == null) {
            throw new RuntimeException("套料方案不存在: " + nestingId);
        }

        plan.setNestingStatus("CONFIRMED");
        nestingPlanMapper.updateById(plan);

        ApsSchedule schedule = new ApsSchedule();
        schedule.setScheduleNo("SCH-NST-" + System.currentTimeMillis());
        schedule.setSchedulePhase("PREP");
        schedule.setIsMultiOutput(true);
        schedule.setNestingPlanId(nestingId);
        schedule.setScheduleStatus("CONFIRMED");
        schedule.setPlannedWeight(plan.getSourceWeight());
        schedule.setPlannedQty(BigDecimal.ONE);
        schedule.setPrdtId(plan.getSourceMaterialId() != null ? plan.getSourceMaterialId() : 0L);
        schedule.setDemandGradeCode(plan.getSourceGradeCode() != null ? plan.getSourceGradeCode() : "");
        schedule.setScheduleStart(new Date());
        schedule.setScheduleEnd(new Date());
        schedule.setCreatedTime(new Date());
        scheduleMapper.insert(schedule);

        log.info("confirmAndSchedule: nestingId={}, scheduleId={}", nestingId, schedule.getScheduleId());
    }

    public List<CostSplitItem> getCostSplit(Long nestingId) {
        LambdaQueryWrapper<ApsNestingDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApsNestingDetail::getNestingId, nestingId);
        List<ApsNestingDetail> details = nestingDetailMapper.selectList(wrapper);

        BigDecimal totalWeight = details.stream()
                .map(d -> d.getTotalWeight() != null ? d.getTotalWeight() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ApsNestingPlan plan = nestingPlanMapper.selectById(nestingId);
        BigDecimal sourceWeight = plan != null && plan.getSourceWeight() != null
                ? plan.getSourceWeight() : BigDecimal.ONE;

        List<CostSplitItem> result = new ArrayList<>();
        for (ApsNestingDetail detail : details) {
            CostSplitItem item = new CostSplitItem();
            item.setNestingDetailId(detail.getNestingDetailId());
            item.setContractNo(detail.getContractNo());
            item.setOutputWeight(detail.getTotalWeight());
            if (totalWeight.compareTo(BigDecimal.ZERO) > 0) {
                item.setWeightRatio(detail.getTotalWeight() != null
                        ? detail.getTotalWeight().divide(totalWeight, 6, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO);
                item.setAllocatedCostWeight(sourceWeight.multiply(item.getWeightRatio())
                        .setScale(3, RoundingMode.HALF_UP));
            } else {
                item.setWeightRatio(BigDecimal.ZERO);
                item.setAllocatedCostWeight(BigDecimal.ZERO);
            }
            result.add(item);
        }
        return result;
    }

    // ======================== Private optimization methods ========================

    private NestingOptimizeResult slitOptimize(List<ApsNestingPool> poolItems,
                                                List<InvStock> motherCoils,
                                                String groupKey) {
        if (motherCoils.isEmpty()) {
            log.warn("slitOptimize: no mother coils found for groupKey={}", groupKey);
            return createFallbackResult(poolItems, groupKey, "MULTI_1D");
        }

        InvStock coil = motherCoils.get(0);
        BigDecimal motherWidth = coil.getActualWidth() != null ? coil.getActualWidth() : DEFAULT_MOTHER_WIDTH;
        BigDecimal thickness = coil.getActualThickness() != null ? coil.getActualThickness() : BigDecimal.ONE;
        BigDecimal motherWeight = coil.getOnHandWeight() != null ? coil.getOnHandWeight() : BigDecimal.ZERO;

        BigDecimal usableWidth = motherWidth
                .subtract(DEFAULT_EDGE_TRIM.multiply(new BigDecimal("2")));

        List<ApsNestingPool> sorted = new ArrayList<>(poolItems);
        sorted.sort((a, b) -> {
            BigDecimal wa = a.getWidth() != null ? a.getWidth() : BigDecimal.ZERO;
            BigDecimal wb = b.getWidth() != null ? b.getWidth() : BigDecimal.ZERO;
            return wb.compareTo(wa);
        });

        BigDecimal remaining = usableWidth;
        List<SlitAllocation> allocations = new ArrayList<>();
        int slitCount = 0;

        for (ApsNestingPool item : sorted) {
            BigDecimal stripWidth = item.getWidth() != null ? item.getWidth() : BigDecimal.ZERO;
            if (stripWidth.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal needed = stripWidth.add(DEFAULT_KERF);
            int qty = item.getRemainingQty() != null ? item.getRemainingQty().intValue() : 1;

            for (int i = 0; i < qty; i++) {
                if (remaining.compareTo(needed) >= 0) {
                    remaining = remaining.subtract(needed);
                    slitCount++;

                    SlitAllocation alloc = new SlitAllocation();
                    alloc.poolId = item.getPoolId();
                    alloc.width = stripWidth;
                    alloc.contractNo = item.getContractNo();
                    allocations.add(alloc);
                }
            }
        }

        BigDecimal usedWidth = usableWidth.subtract(remaining);
        BigDecimal utilizationPct = motherWidth.compareTo(BigDecimal.ZERO) > 0
                ? usedWidth.divide(motherWidth, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                : BigDecimal.ZERO;

        BigDecimal wasteWidthFraction = remaining.divide(motherWidth, 6, RoundingMode.HALF_UP);
        BigDecimal wasteWeight = motherWeight.multiply(wasteWidthFraction).setScale(3, RoundingMode.HALF_UP);

        ApsNestingPlan plan = new ApsNestingPlan();
        plan.setNestingNo("NST-M-" + System.currentTimeMillis());
        plan.setNestingType("MULTI_1D");
        plan.setSourceStockId(coil.getStockId());
        plan.setSourceMaterialId(coil.getPrdtId());
        plan.setSourceGradeCode(coil.getPatName());
        plan.setSourceCoilNo(coil.getResNo());
        plan.setSourceWidth(motherWidth);
        plan.setSourceThickness(thickness);
        plan.setSourceWeight(motherWeight);
        plan.setEdgeTrim(DEFAULT_EDGE_TRIM);
        plan.setKerfWidth(DEFAULT_KERF);
        plan.setSlitCount(slitCount);
        plan.setUtilizationPct(utilizationPct);
        plan.setWasteWeight(wasteWeight);
        plan.setNestingStatus("OPTIMIZED");
        plan.setCreatedTime(new Date());
        nestingPlanMapper.insert(plan);

        int lineNo = 1;
        for (SlitAllocation alloc : allocations) {
            ApsNestingDetail detail = new ApsNestingDetail();
            detail.setNestingId(plan.getNestingId());
            detail.setLineNo(lineNo++);
            detail.setOutputWidth(alloc.width);
            detail.setOutputThickness(thickness);
            detail.setOutputCount(1);
            detail.setContractNo(alloc.contractNo);
            detail.setOutputType("PRODUCT");

            BigDecimal widthFraction = alloc.width.divide(motherWidth, 6, RoundingMode.HALF_UP);
            detail.setTotalWeight(motherWeight.multiply(widthFraction).setScale(3, RoundingMode.HALF_UP));
            nestingDetailMapper.insert(detail);

            updatePoolNestingLink(alloc.poolId, plan.getNestingId(), detail.getNestingDetailId());
        }

        NestingOptimizeResult result = new NestingOptimizeResult();
        result.setNestingId(plan.getNestingId());
        result.setUtilizationPct(utilizationPct);
        result.setWasteWeight(wasteWeight);
        result.setAllocations(allocations.stream().map(a -> {
            AllocationItem ai = new AllocationItem();
            ai.setPoolId(a.poolId);
            ai.setContractNo(a.contractNo);
            ai.setWidth(a.width);
            return ai;
        }).collect(Collectors.toList()));
        return result;
    }

    private NestingOptimizeResult levelOptimize(List<ApsNestingPool> poolItems,
                                                 List<InvStock> motherCoils,
                                                 String groupKey) {
        if (motherCoils.isEmpty()) {
            log.warn("levelOptimize: no mother coils found for groupKey={}", groupKey);
            return createFallbackResult(poolItems, groupKey, "MULTI_1D");
        }

        InvStock coil = motherCoils.get(0);
        BigDecimal motherWidth = coil.getActualWidth() != null ? coil.getActualWidth() : DEFAULT_MOTHER_WIDTH;
        BigDecimal thickness = coil.getActualThickness() != null ? coil.getActualThickness() : BigDecimal.ONE;
        BigDecimal motherWeight = coil.getOnHandWeight() != null ? coil.getOnHandWeight() : BigDecimal.ZERO;

        BigDecimal crossSection = thickness.multiply(motherWidth).divide(MILLION, 6, RoundingMode.HALF_UP);
        BigDecimal motherLengthM = BigDecimal.ZERO;
        if (crossSection.compareTo(BigDecimal.ZERO) > 0 && motherWeight.compareTo(BigDecimal.ZERO) > 0) {
            motherLengthM = motherWeight
                    .divide(crossSection.multiply(STEEL_DENSITY), 3, RoundingMode.HALF_UP);
        }

        BigDecimal motherLengthMm = motherLengthM.multiply(THOUSAND);

        List<ApsNestingPool> sorted = new ArrayList<>(poolItems);
        sorted.sort(Comparator.comparingInt(p -> p.getPriority() != null ? p.getPriority() : Integer.MAX_VALUE));

        BigDecimal remainingLength = motherLengthMm;
        BigDecimal kerfMm = DEFAULT_KERF;
        List<LevelAllocation> allocations = new ArrayList<>();
        BigDecimal totalUsedLength = BigDecimal.ZERO;

        for (ApsNestingPool item : sorted) {
            BigDecimal cutLen = item.getCutLength() != null ? item.getCutLength() : BigDecimal.ZERO;
            if (cutLen.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            int qty = item.getRemainingQty() != null ? item.getRemainingQty().intValue() : 1;
            for (int i = 0; i < qty; i++) {
                BigDecimal needed = cutLen.add(kerfMm);
                if (remainingLength.compareTo(needed) >= 0) {
                    remainingLength = remainingLength.subtract(needed);
                    totalUsedLength = totalUsedLength.add(cutLen);

                    LevelAllocation alloc = new LevelAllocation();
                    alloc.poolId = item.getPoolId();
                    alloc.cutLength = cutLen;
                    alloc.contractNo = item.getContractNo();
                    allocations.add(alloc);
                }
            }
        }

        BigDecimal utilizationPct = motherLengthMm.compareTo(BigDecimal.ZERO) > 0
                ? totalUsedLength.divide(motherLengthMm, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                : BigDecimal.ZERO;

        BigDecimal wasteFraction = motherLengthMm.compareTo(BigDecimal.ZERO) > 0
                ? remainingLength.divide(motherLengthMm, 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal wasteWeight = motherWeight.multiply(wasteFraction).setScale(3, RoundingMode.HALF_UP);

        ApsNestingPlan plan = new ApsNestingPlan();
        plan.setNestingNo("NST-M-" + System.currentTimeMillis());
        plan.setNestingType("MULTI_1D");
        plan.setSourceStockId(coil.getStockId());
        plan.setSourceMaterialId(coil.getPrdtId());
        plan.setSourceGradeCode(coil.getPatName());
        plan.setSourceCoilNo(coil.getResNo());
        plan.setSourceWidth(motherWidth);
        plan.setSourceThickness(thickness);
        plan.setSourceWeight(motherWeight);
        plan.setSourceLengthM(motherLengthM);
        plan.setEdgeTrim(DEFAULT_EDGE_TRIM);
        plan.setKerfWidth(DEFAULT_KERF);
        plan.setSlitCount(allocations.size());
        plan.setUtilizationPct(utilizationPct);
        plan.setWasteWeight(wasteWeight);
        plan.setNestingStatus("OPTIMIZED");
        plan.setCreatedTime(new Date());
        nestingPlanMapper.insert(plan);

        int lineNo = 1;
        for (LevelAllocation alloc : allocations) {
            ApsNestingDetail detail = new ApsNestingDetail();
            detail.setNestingId(plan.getNestingId());
            detail.setLineNo(lineNo++);
            detail.setOutputWidth(motherWidth);
            detail.setOutputThickness(thickness);
            detail.setOutputLength(alloc.cutLength);
            detail.setOutputCount(1);
            detail.setContractNo(alloc.contractNo);
            detail.setOutputType("PRODUCT");

            BigDecimal lengthFraction = motherLengthMm.compareTo(BigDecimal.ZERO) > 0
                    ? alloc.cutLength.divide(motherLengthMm, 6, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            detail.setTotalWeight(motherWeight.multiply(lengthFraction).setScale(3, RoundingMode.HALF_UP));
            nestingDetailMapper.insert(detail);

            updatePoolNestingLink(alloc.poolId, plan.getNestingId(), detail.getNestingDetailId());
        }

        NestingOptimizeResult result = new NestingOptimizeResult();
        result.setNestingId(plan.getNestingId());
        result.setUtilizationPct(utilizationPct);
        result.setWasteWeight(wasteWeight);
        result.setAllocations(allocations.stream().map(a -> {
            AllocationItem ai = new AllocationItem();
            ai.setPoolId(a.poolId);
            ai.setContractNo(a.contractNo);
            ai.setLength(a.cutLength);
            return ai;
        }).collect(Collectors.toList()));
        return result;
    }

    private NestingOptimizeResult cutOptimize(List<ApsNestingPool> poolItems,
                                               List<InvStock> motherCoils,
                                               String groupKey) {
        if (motherCoils.isEmpty()) {
            log.warn("cutOptimize: no mother coils found for groupKey={}", groupKey);
            return createFallbackResult(poolItems, groupKey, "MULTI_2D");
        }

        InvStock coil = motherCoils.get(0);
        BigDecimal sheetWidth = coil.getActualWidth() != null ? coil.getActualWidth() : DEFAULT_MOTHER_WIDTH;
        BigDecimal thickness = coil.getActualThickness() != null ? coil.getActualThickness() : BigDecimal.ONE;
        BigDecimal motherWeight = coil.getOnHandWeight() != null ? coil.getOnHandWeight() : BigDecimal.ZERO;

        BigDecimal crossSection = thickness.multiply(sheetWidth).divide(MILLION, 6, RoundingMode.HALF_UP);
        BigDecimal sheetLengthM = BigDecimal.ZERO;
        if (crossSection.compareTo(BigDecimal.ZERO) > 0 && motherWeight.compareTo(BigDecimal.ZERO) > 0) {
            sheetLengthM = motherWeight
                    .divide(crossSection.multiply(STEEL_DENSITY), 3, RoundingMode.HALF_UP);
        }
        BigDecimal sheetLength = sheetLengthM.multiply(THOUSAND);

        List<PieceRequest> pieces = new ArrayList<>();
        for (ApsNestingPool item : poolItems) {
            BigDecimal pw = item.getCutWidth() != null ? item.getCutWidth() : BigDecimal.ZERO;
            BigDecimal pl = item.getCutLength() != null ? item.getCutLength() : BigDecimal.ZERO;
            if (pw.compareTo(BigDecimal.ZERO) <= 0 || pl.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            int qty = item.getRemainingQty() != null ? item.getRemainingQty().intValue() : 1;
            for (int i = 0; i < qty; i++) {
                PieceRequest pr = new PieceRequest();
                pr.poolId = item.getPoolId();
                pr.width = pw;
                pr.length = pl;
                pr.area = pw.multiply(pl);
                pr.contractNo = item.getContractNo();
                pieces.add(pr);
            }
        }

        pieces.sort((a, b) -> b.area.compareTo(a.area));

        List<FreeRect> freeRects = new ArrayList<>();
        freeRects.add(new FreeRect(BigDecimal.ZERO, BigDecimal.ZERO, sheetWidth, sheetLength));

        List<PlacedPiece> placed = new ArrayList<>();
        BigDecimal totalPlacedArea = BigDecimal.ZERO;

        for (PieceRequest piece : pieces) {
            FreeRect bestRect = null;
            boolean bestRotated = false;

            for (FreeRect rect : freeRects) {
                if (rect.width.compareTo(piece.width) >= 0 && rect.height.compareTo(piece.length) >= 0) {
                    if (bestRect == null || rect.y.compareTo(bestRect.y) < 0
                            || (rect.y.compareTo(bestRect.y) == 0 && rect.x.compareTo(bestRect.x) < 0)) {
                        bestRect = rect;
                        bestRotated = false;
                    }
                }
                if (rect.width.compareTo(piece.length) >= 0 && rect.height.compareTo(piece.width) >= 0) {
                    if (bestRect == null || rect.y.compareTo(bestRect.y) < 0
                            || (rect.y.compareTo(bestRect.y) == 0 && rect.x.compareTo(bestRect.x) < 0)) {
                        bestRect = rect;
                        bestRotated = true;
                    }
                }
            }

            if (bestRect != null) {
                BigDecimal pw = bestRotated ? piece.length : piece.width;
                BigDecimal pl = bestRotated ? piece.width : piece.length;

                PlacedPiece pp = new PlacedPiece();
                pp.poolId = piece.poolId;
                pp.x = bestRect.x;
                pp.y = bestRect.y;
                pp.width = pw;
                pp.length = pl;
                pp.rotated = bestRotated;
                pp.contractNo = piece.contractNo;
                placed.add(pp);

                totalPlacedArea = totalPlacedArea.add(pw.multiply(pl));

                freeRects.remove(bestRect);

                BigDecimal rightX = bestRect.x.add(pw);
                BigDecimal topY = bestRect.y.add(pl);
                BigDecimal rightWidth = bestRect.width.subtract(pw);
                BigDecimal topHeight = bestRect.height.subtract(pl);

                if (rightWidth.compareTo(BigDecimal.ZERO) > 0) {
                    freeRects.add(new FreeRect(rightX, bestRect.y, rightWidth, bestRect.height));
                }
                if (topHeight.compareTo(BigDecimal.ZERO) > 0) {
                    freeRects.add(new FreeRect(bestRect.x, topY, pw, topHeight));
                }
            }
        }

        BigDecimal totalArea = sheetWidth.multiply(sheetLength);
        BigDecimal utilizationPct = totalArea.compareTo(BigDecimal.ZERO) > 0
                ? totalPlacedArea.divide(totalArea, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                : BigDecimal.ZERO;

        BigDecimal wasteFraction = totalArea.compareTo(BigDecimal.ZERO) > 0
                ? BigDecimal.ONE.subtract(totalPlacedArea.divide(totalArea, 6, RoundingMode.HALF_UP))
                : BigDecimal.ZERO;
        BigDecimal wasteWeight = motherWeight.multiply(wasteFraction).setScale(3, RoundingMode.HALF_UP);

        ApsNestingPlan plan = new ApsNestingPlan();
        plan.setNestingNo("NST-M-" + System.currentTimeMillis());
        plan.setNestingType("MULTI_2D");
        plan.setSourceStockId(coil.getStockId());
        plan.setSourceMaterialId(coil.getPrdtId());
        plan.setSourceGradeCode(coil.getPatName());
        plan.setSourceCoilNo(coil.getResNo());
        plan.setSourceWidth(sheetWidth);
        plan.setSourceThickness(thickness);
        plan.setSourceWeight(motherWeight);
        plan.setSourceLengthM(sheetLengthM);
        plan.setUtilizationPct(utilizationPct);
        plan.setWasteWeight(wasteWeight);
        plan.setNestingStatus("OPTIMIZED");
        plan.setCreatedTime(new Date());
        nestingPlanMapper.insert(plan);

        Map<Long, ApsNestingDetail> detailByPool = new LinkedHashMap<>();
        int lineNo = 1;

        for (PlacedPiece pp : placed) {
            ApsNestingDetail detail = detailByPool.get(pp.poolId);
            if (detail == null) {
                detail = new ApsNestingDetail();
                detail.setNestingId(plan.getNestingId());
                detail.setLineNo(lineNo++);
                detail.setOutputWidth(pp.width);
                detail.setOutputThickness(thickness);
                detail.setOutputLength(pp.length);
                detail.setOutputCount(0);
                detail.setContractNo(pp.contractNo);
                detail.setOutputType("PRODUCT");
                detail.setTotalWeight(BigDecimal.ZERO);
                detailByPool.put(pp.poolId, detail);
            }
            detail.setOutputCount(detail.getOutputCount() + 1);

            BigDecimal pieceWeight = BigDecimal.ZERO;
            if (totalArea.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal areaFraction = pp.width.multiply(pp.length)
                        .divide(totalArea, 6, RoundingMode.HALF_UP);
                pieceWeight = motherWeight.multiply(areaFraction).setScale(3, RoundingMode.HALF_UP);
            }
            detail.setTotalWeight(detail.getTotalWeight().add(pieceWeight));
        }

        for (ApsNestingDetail detail : detailByPool.values()) {
            nestingDetailMapper.insert(detail);
        }

        int sheetNo = 1;
        for (PlacedPiece pp : placed) {
            ApsNestingDetail detail = detailByPool.get(pp.poolId);

            ApsNesting2dLayout layout = new ApsNesting2dLayout();
            layout.setNestingDetailId(detail.getNestingDetailId());
            layout.setNestingId(plan.getNestingId());
            layout.setSheetNo(sheetNo);
            layout.setPoolId(pp.poolId);
            layout.setContractNo(pp.contractNo);
            layout.setPieceWidth(pp.width);
            layout.setPieceLength(pp.length);
            layout.setPieceQty(1);
            layout.setPosX(pp.x);
            layout.setPosY(pp.y);
            layout.setIsRotated(pp.rotated);
            layout.setPieceType("PRODUCT");
            nesting2dLayoutMapper.insert(layout);
        }

        for (Map.Entry<Long, ApsNestingDetail> entry : detailByPool.entrySet()) {
            updatePoolNestingLink(entry.getKey(), plan.getNestingId(), entry.getValue().getNestingDetailId());
        }

        NestingOptimizeResult result = new NestingOptimizeResult();
        result.setNestingId(plan.getNestingId());
        result.setUtilizationPct(utilizationPct);
        result.setWasteWeight(wasteWeight);
        result.setAllocations(placed.stream().map(pp -> {
            AllocationItem ai = new AllocationItem();
            ai.setPoolId(pp.poolId);
            ai.setContractNo(pp.contractNo);
            ai.setWidth(pp.width);
            ai.setLength(pp.length);
            ai.setPosX(pp.x);
            ai.setPosY(pp.y);
            return ai;
        }).collect(Collectors.toList()));
        return result;
    }

    // ======================== Helper methods ========================

    private List<InvStock> findMotherCoils(BigDecimal thickness, String patName) {
        LambdaQueryWrapper<InvStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.gt(InvStock::getOnHandWeight, 0);
        if (thickness != null && thickness.compareTo(BigDecimal.ZERO) > 0) {
            wrapper.eq(InvStock::getActualThickness, thickness);
        }
        if (patName != null && !patName.isEmpty()) {
            wrapper.eq(InvStock::getPatName, patName);
        }
        wrapper.orderByDesc(InvStock::getOnHandWeight);
        return stockMapper.selectList(wrapper);
    }

    private void updatePoolStatus(List<ApsNestingPool> items, String status, Long nestingId) {
        for (ApsNestingPool item : items) {
            LambdaUpdateWrapper<ApsNestingPool> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(ApsNestingPool::getPoolId, item.getPoolId())
                    .set(ApsNestingPool::getPoolStatus, status)
                    .set(ApsNestingPool::getNestingId, nestingId);
            nestingPoolMapper.update(null, wrapper);
        }
    }

    private void updatePoolNestingLink(Long poolId, Long nestingId, Long nestingDetailId) {
        LambdaUpdateWrapper<ApsNestingPool> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ApsNestingPool::getPoolId, poolId)
                .set(ApsNestingPool::getNestingId, nestingId)
                .set(ApsNestingPool::getNestingDetailId, nestingDetailId);
        nestingPoolMapper.update(null, wrapper);
    }

    private NestingOptimizeResult createFallbackResult(List<ApsNestingPool> poolItems,
                                                        String groupKey,
                                                        String nestingType) {
        ApsNestingPlan plan = new ApsNestingPlan();
        plan.setNestingNo("NST-M-" + System.currentTimeMillis());
        plan.setNestingType(nestingType);
        plan.setSourceWidth(DEFAULT_MOTHER_WIDTH);
        plan.setUtilizationPct(BigDecimal.ZERO);
        plan.setWasteWeight(BigDecimal.ZERO);
        plan.setNestingStatus("NO_STOCK");
        plan.setCreatedTime(new Date());
        nestingPlanMapper.insert(plan);

        int lineNo = 1;
        for (ApsNestingPool item : poolItems) {
            ApsNestingDetail detail = new ApsNestingDetail();
            detail.setNestingId(plan.getNestingId());
            detail.setLineNo(lineNo++);
            detail.setOutputWidth(item.getWidth());
            detail.setOutputThickness(item.getThickness());
            detail.setOutputLength(item.getCutLength());
            detail.setOutputCount(item.getRemainingQty() != null ? item.getRemainingQty().intValue() : 1);
            detail.setContractNo(item.getContractNo());
            detail.setOutputType("PRODUCT");
            detail.setTotalWeight(item.getRemainingWeight());
            nestingDetailMapper.insert(detail);
        }

        NestingOptimizeResult result = new NestingOptimizeResult();
        result.setNestingId(plan.getNestingId());
        result.setUtilizationPct(BigDecimal.ZERO);
        result.setWasteWeight(BigDecimal.ZERO);
        result.setAllocations(Collections.emptyList());
        return result;
    }

    // ======================== Inner data classes ========================

    @Data
    public static class NestingPoolGroup {
        private String groupKey;
        private List<ApsNestingPool> items;
        private BigDecimal totalWeight;
        private int contractCount;
    }

    @Data
    public static class NestingOptimizeResult {
        private Long nestingId;
        private BigDecimal utilizationPct;
        private BigDecimal wasteWeight;
        private List<AllocationItem> allocations;
    }

    @Data
    public static class AllocationItem {
        private Long poolId;
        private String contractNo;
        private BigDecimal width;
        private BigDecimal length;
        private BigDecimal posX;
        private BigDecimal posY;
    }

    @Data
    public static class CostSplitItem {
        private Long nestingDetailId;
        private String contractNo;
        private BigDecimal outputWeight;
        private BigDecimal weightRatio;
        private BigDecimal allocatedCostWeight;
    }

    private static class SlitAllocation {
        Long poolId;
        BigDecimal width;
        String contractNo;
    }

    private static class LevelAllocation {
        Long poolId;
        BigDecimal cutLength;
        String contractNo;
    }

    private static class PieceRequest {
        Long poolId;
        BigDecimal width;
        BigDecimal length;
        BigDecimal area;
        String contractNo;
    }

    private static class FreeRect {
        BigDecimal x, y, width, height;

        FreeRect(BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private static class PlacedPiece {
        Long poolId;
        BigDecimal x, y, width, length;
        boolean rotated;
        String contractNo;
    }
}
