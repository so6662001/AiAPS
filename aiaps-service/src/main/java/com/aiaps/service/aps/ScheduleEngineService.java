package com.aiaps.service.aps;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.aps.ApsSchedule;
import com.aiaps.domain.aps.ApsScheduleOper;
import com.aiaps.domain.base.BasMaterial;
import com.aiaps.domain.base.BasRoutingHead;
import com.aiaps.domain.base.BasRoutingOper;
import com.aiaps.domain.base.BasWorkCenter;
import com.aiaps.domain.mrp.MrpPlanOrder;
import com.aiaps.mapper.aps.ApsScheduleMapper;
import com.aiaps.mapper.aps.ApsScheduleOperMapper;
import com.aiaps.mapper.base.BasMaterialMapper;
import com.aiaps.mapper.base.BasMoldMapper;
import com.aiaps.mapper.base.BasRoutingHeadMapper;
import com.aiaps.mapper.base.BasRoutingOperMapper;
import com.aiaps.mapper.base.BasWorkCenterMapper;
import com.aiaps.mapper.mrp.MrpPlanOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleEngineService {

    private final ApsScheduleMapper scheduleMapper;

    @Autowired
    private ScheduleModificationService modificationService;
    private final ApsScheduleOperMapper scheduleOperMapper;
    private final BasWorkCenterMapper workCenterMapper;
    private final BasMoldMapper moldMapper;
    private final BasRoutingOperMapper routingOperMapper;
    private final MrpPlanOrderMapper planOrderMapper;
    private final BasRoutingHeadMapper routingHeadMapper;
    private final BasMaterialMapper materialMapper;

    private final java.util.concurrent.atomic.AtomicInteger scheduleSeq = new java.util.concurrent.atomic.AtomicInteger(0);

    @Transactional
    public List<ApsSchedule> autoSchedule(List<Long> planOrderIds, String strategy) {
        List<ApsSchedule> schedules = new ArrayList<>();

        List<MrpPlanOrder> planOrders = planOrderMapper.selectBatchIds(planOrderIds);
        if (planOrders.isEmpty()) {
            throw new BizException("未找到指定的计划订单");
        }

        planOrders.sort(Comparator.comparingInt(this::calculatePriority).reversed());

        List<BasWorkCenter> allWcs = workCenterMapper.selectList(
                new LambdaQueryWrapper<BasWorkCenter>().eq(BasWorkCenter::getIsActive, true));
        Map<String, List<BasWorkCenter>> wcByType = allWcs.stream()
                .collect(Collectors.groupingBy(wc -> wc.getWcType() != null ? wc.getWcType() : "DEFAULT"));

        for (MrpPlanOrder planOrder : planOrders) {
            ApsSchedule schedule = new ApsSchedule();
            schedule.setScheduleNo(generateScheduleNo());
            schedule.setPlanOrderId(planOrder.getPlanOrderId());
            schedule.setPrdtId(planOrder.getPrdtId());
            schedule.setPlannedQty(planOrder.getPlannedQty());
            schedule.setPlannedWeight(planOrder.getPlannedWeight());
            schedule.setDemandSource(planOrder.getDemandSource());
            schedule.setContractNo(planOrder.getContractNo());
            schedule.setDemandGradeCode(planOrder.getPatName());
            schedule.setDemandOriginCode(planOrder.getPaName());
            schedule.setPriority(calculatePriority(planOrder));
            schedule.setScheduleStatus("DRAFT");
            schedule.setIsLocked(false);
            schedule.setScheduleVersion(1);
            schedule.setCreatedTime(new Date());

            BasMaterial material = materialMapper.selectById(planOrder.getPrdtId());
            schedule.setOutputFlowType(inferOutputFlowType(material));

            // Multi-stage scheduling fields
            if (material != null) {
                String matType = material.getMaterialType();
                if ("RAW".equals(matType)) {
                    schedule.setSchedulePhase("PREP");
                    schedule.setScheduleLevel("RAW");
                } else if ("SEMI".equals(matType)) {
                    schedule.setSchedulePhase("MFG");
                    schedule.setScheduleLevel("PRODUCT");
                } else {
                    schedule.setSchedulePhase("MFG");
                    schedule.setScheduleLevel("PRODUCT");
                }
            }

            // Raw material info from plan order
            schedule.setRawGradeCode(planOrder.getRawGradeCode());
            schedule.setRawOriginCode(planOrder.getRawOriginCode());
            if (planOrder.getMatchedStockId() != null) {
                schedule.setRawStockId(planOrder.getMatchedStockId());
                schedule.setRawCoilNo(planOrder.getMatchedCoilNo());
            }

            List<BasRoutingOper> routingOpers = findRoutingOpers(planOrder.getPrdtId());

            List<ApsScheduleOper> opers = new ArrayList<>();

            if (routingOpers.isEmpty()) {
                ApsScheduleOper defaultOper = new ApsScheduleOper();
                defaultOper.setOperNo(10);
                defaultOper.setOperName("默认工序");
                defaultOper.setPlannedQty(planOrder.getPlannedQty());
                defaultOper.setPlannedWeight(planOrder.getPlannedWeight());
                defaultOper.setOperStatus("PENDING");
                defaultOper.setIsLocked(false);
                defaultOper.setSequenceInWc(1);
                defaultOper.setPatName(planOrder.getPatName());
                defaultOper.setPaName(planOrder.getPaName());
                defaultOper.setContractNo(planOrder.getContractNo());

                List<BasWorkCenter> candidates = allWcs;
                if (!candidates.isEmpty()) {
                    defaultOper.setWcId(candidates.get(0).getWcId());
                }
                opers.add(defaultOper);
            } else {
                int seq = 1;
                for (BasRoutingOper routingOper : routingOpers) {
                    ApsScheduleOper oper = new ApsScheduleOper();
                    oper.setOperNo(routingOper.getOperNo());
                    oper.setOperName(routingOper.getOperName());
                    oper.setPlannedQty(planOrder.getPlannedQty());
                    oper.setPlannedWeight(planOrder.getPlannedWeight());
                    oper.setOperStatus("PENDING");
                    oper.setIsLocked(false);
                    oper.setSequenceInWc(seq++);
                    oper.setPatName(planOrder.getPatName());
                    oper.setPaName(planOrder.getPaName());
                    oper.setContractNo(planOrder.getContractNo());

                    Long wcId = assignWorkCenter(routingOper, wcByType, strategy);
                    oper.setWcId(wcId);

                    opers.add(oper);
                }
            }

            calculateScheduleTimes(schedule, opers, planOrder);

            scheduleMapper.insert(schedule);

            for (ApsScheduleOper oper : opers) {
                oper.setScheduleId(schedule.getScheduleId());
                scheduleOperMapper.insert(oper);
            }

            schedule.setOpers(opers);

            planOrder.setOrderStatus("SCHEDULED");
            planOrderMapper.updateById(planOrder);

            schedules.add(schedule);
        }

        return schedules;
    }

    @Transactional
    public void moveSchedule(Long scheduleId, Date newStart, Long newWcId) {
        ApsSchedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BizException("排程不存在: " + scheduleId);
        }
        if (Boolean.TRUE.equals(schedule.getIsLocked())) {
            throw new BizException("排程已锁定, 无法移动: " + scheduleId);
        }

        modificationService.createSnapshot(Arrays.asList(scheduleId), "AUTO", "移动排产前自动快照", null);

        Long effectiveWcId = newWcId;

        if (newStart != null) {
            long duration = 0;
            if (schedule.getScheduleStart() != null && schedule.getScheduleEnd() != null) {
                duration = schedule.getScheduleEnd().getTime() - schedule.getScheduleStart().getTime();
            }
            schedule.setScheduleStart(newStart);
            schedule.setScheduleEnd(new Date(newStart.getTime() + duration));
        }

        schedule.setUpdatedTime(new Date());
        scheduleMapper.updateById(schedule);

        if (newWcId != null) {
            List<ApsScheduleOper> opers = scheduleOperMapper.selectByScheduleId(scheduleId);
            if (opers != null && !opers.isEmpty()) {
                ApsScheduleOper firstOper = opers.get(0);
                firstOper.setWcId(newWcId);
                scheduleOperMapper.updateById(firstOper);
            }
        }

        if (effectiveWcId == null && schedule.getOpers() != null && !schedule.getOpers().isEmpty()) {
            effectiveWcId = schedule.getOpers().get(0).getWcId();
        }
        if (effectiveWcId == null) {
            List<ApsScheduleOper> opers = scheduleOperMapper.selectByScheduleId(scheduleId);
            if (opers != null && !opers.isEmpty()) {
                effectiveWcId = opers.get(0).getWcId();
            }
        }

        if (effectiveWcId != null && schedule.getScheduleStart() != null) {
            List<ApsScheduleOper> wcOpers = scheduleOperMapper.selectList(
                    new LambdaQueryWrapper<ApsScheduleOper>()
                            .eq(ApsScheduleOper::getWcId, effectiveWcId)
                            .ge(ApsScheduleOper::getOperStart, schedule.getScheduleStart())
                            .ne(ApsScheduleOper::getScheduleId, scheduleId)
                            .orderByAsc(ApsScheduleOper::getOperStart));

            Date endOfMoved = schedule.getScheduleEnd();
            for (ApsScheduleOper nextOper : wcOpers) {
                if (nextOper.getOperStart() != null && endOfMoved != null
                        && nextOper.getOperStart().before(endOfMoved)
                        && !Boolean.TRUE.equals(nextOper.getIsLocked())) {
                    long overlapMs = endOfMoved.getTime() - nextOper.getOperStart().getTime();
                    Date newOperStart = new Date(nextOper.getOperStart().getTime() + overlapMs);
                    long operDuration = 0;
                    if (nextOper.getOperEnd() != null) {
                        operDuration = nextOper.getOperEnd().getTime() - nextOper.getOperStart().getTime();
                    }
                    nextOper.setOperStart(newOperStart);
                    nextOper.setOperEnd(new Date(newOperStart.getTime() + operDuration));
                    scheduleOperMapper.updateById(nextOper);

                    ApsSchedule nextSchedule = scheduleMapper.selectById(nextOper.getScheduleId());
                    if (nextSchedule != null) {
                        nextSchedule.setScheduleStart(newOperStart);
                        nextSchedule.setScheduleEnd(new Date(newOperStart.getTime() + operDuration));
                        nextSchedule.setUpdatedTime(new Date());
                        scheduleMapper.updateById(nextSchedule);
                    }

                    endOfMoved = new Date(newOperStart.getTime() + operDuration);
                } else {
                    break;
                }
            }
        }

        modificationService.recordChangeLog("SCHEDULE", scheduleId, schedule.getScheduleNo(), "MOVE", "USER", null, null);
    }

    @Transactional
    public void insertOrder(ApsSchedule schedule) {
        if (schedule.getScheduleStatus() == null) {
            schedule.setScheduleStatus("DRAFT");
        }
        schedule.setIsLocked(false);
        schedule.setScheduleVersion(1);
        schedule.setCreatedTime(new Date());
        scheduleMapper.insert(schedule);
    }

    @Transactional
    public void lockSchedule(List<Long> scheduleIds, boolean locked) {
        for (Long id : scheduleIds) {
            ApsSchedule schedule = scheduleMapper.selectById(id);
            if (schedule != null) {
                schedule.setIsLocked(locked);
                schedule.setLockReason(locked ? "MANUAL_LOCK" : null);
                schedule.setUpdatedTime(new Date());
                scheduleMapper.updateById(schedule);

                List<ApsScheduleOper> opers = scheduleOperMapper.selectByScheduleId(id);
                if (opers != null) {
                    for (ApsScheduleOper oper : opers) {
                        oper.setIsLocked(locked);
                        scheduleOperMapper.updateById(oper);
                    }
                }
            }
        }
    }

    public List<ApsSchedule> getGanttData(List<Long> wcIds, Date dateFrom, Date dateTo) {
        if (wcIds == null || wcIds.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<ApsScheduleOper> operWrapper = new LambdaQueryWrapper<>();
        operWrapper.in(ApsScheduleOper::getWcId, wcIds);
        if (dateFrom != null) {
            operWrapper.ge(ApsScheduleOper::getOperStart, dateFrom);
        }
        if (dateTo != null) {
            operWrapper.le(ApsScheduleOper::getOperEnd, dateTo);
        }
        List<ApsScheduleOper> opers = scheduleOperMapper.selectList(operWrapper);

        Set<Long> scheduleIds = opers.stream()
                .map(ApsScheduleOper::getScheduleId)
                .collect(Collectors.toSet());

        if (scheduleIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<ApsSchedule> schedules = scheduleMapper.selectBatchIds(scheduleIds);

        Map<Long, List<ApsScheduleOper>> opersBySchedule = opers.stream()
                .collect(Collectors.groupingBy(ApsScheduleOper::getScheduleId));
        for (ApsSchedule schedule : schedules) {
            schedule.setOpers(opersBySchedule.getOrDefault(schedule.getScheduleId(), Collections.emptyList()));
        }

        return schedules;
    }

    public List<Map<String, Object>> detectConflicts(List<Long> wcIds, Date dateFrom, Date dateTo) {
        List<Map<String, Object>> conflicts = new ArrayList<>();

        List<ApsSchedule> schedules = getGanttData(wcIds, dateFrom, dateTo);

        Map<Long, List<ApsScheduleOper>> opersByWc = new HashMap<>();
        for (ApsSchedule s : schedules) {
            if (s.getOpers() != null) {
                for (ApsScheduleOper o : s.getOpers()) {
                    opersByWc.computeIfAbsent(o.getWcId(), k -> new ArrayList<>()).add(o);
                }
            }
        }

        for (Map.Entry<Long, List<ApsScheduleOper>> entry : opersByWc.entrySet()) {
            List<ApsScheduleOper> ops = entry.getValue();
            ops.sort(Comparator.comparing(o -> o.getOperStart() != null ? o.getOperStart() : new Date(0)));

            for (int i = 0; i < ops.size() - 1; i++) {
                ApsScheduleOper curr = ops.get(i);
                ApsScheduleOper next = ops.get(i + 1);
                if (curr.getOperEnd() != null && next.getOperStart() != null
                        && curr.getOperEnd().after(next.getOperStart())) {
                    Map<String, Object> conflict = new HashMap<>();
                    conflict.put("type", "RESOURCE_OVERLAP");
                    conflict.put("wcId", entry.getKey());
                    conflict.put("schedule1", curr.getScheduleId());
                    conflict.put("schedule2", next.getScheduleId());
                    conflict.put("overlapMinutes", (curr.getOperEnd().getTime() - next.getOperStart().getTime()) / 60000);
                    conflicts.add(conflict);
                }
            }
        }

        for (ApsSchedule s : schedules) {
            if (s.getScheduleEnd() != null && s.getPlanOrderId() != null) {
                MrpPlanOrder po = planOrderMapper.selectById(s.getPlanOrderId());
                if (po != null && po.getPlannedEndDate() != null
                        && s.getScheduleEnd().after(po.getPlannedEndDate())) {
                    Map<String, Object> conflict = new HashMap<>();
                    conflict.put("type", "DUE_DATE_VIOLATION");
                    conflict.put("scheduleId", s.getScheduleId());
                    conflict.put("scheduleEnd", s.getScheduleEnd());
                    conflict.put("dueDate", po.getPlannedEndDate());
                    conflicts.add(conflict);
                }
            }
        }

        return conflicts;
    }

    private List<BasRoutingOper> findRoutingOpers(Long prdtId) {
        LambdaQueryWrapper<BasRoutingHead> headWrapper = new LambdaQueryWrapper<>();
        headWrapper.eq(BasRoutingHead::getPrdtId, prdtId)
                   .eq(BasRoutingHead::getIsDefault, true)
                   .eq(BasRoutingHead::getIsActive, true);
        BasRoutingHead head = routingHeadMapper.selectOne(headWrapper);

        if (head == null) {
            log.warn("物料 {} 无默认工艺路线, 将创建单工序排产", prdtId);
            return Collections.emptyList();
        }

        return routingOperMapper.selectByRoutingId(head.getRoutingId());
    }

    private int calculatePriority(MrpPlanOrder planOrder) {
        int score = 50;

        if (planOrder.getPlannedEndDate() != null) {
            long daysUntilDue = (planOrder.getPlannedEndDate().getTime() - System.currentTimeMillis()) / (24 * 3600 * 1000L);
            int urgency = (int) Math.max(0, Math.min(100, 100 - daysUntilDue * 3));
            score = (int) (urgency * 0.4);
        }

        String source = planOrder.getDemandSource();
        int sourceScore = "MTO".equals(source) || "DEMAND".equals(source) ? 80
                : "MTS".equals(source) ? 50 : 30;
        score += (int) (sourceScore * 0.25);

        score += 35;

        return Math.max(1, Math.min(100, score));
    }

    private void calculateScheduleTimes(ApsSchedule schedule, List<ApsScheduleOper> opers,
                                         MrpPlanOrder planOrder) {
        Date earliest = planOrder.getPlannedStartDate() != null
                ? planOrder.getPlannedStartDate() : new Date();
        if (earliest.before(new Date())) {
            earliest = new Date();
        }

        Date currentTime = earliest;

        for (ApsScheduleOper oper : opers) {
            BigDecimal weight = schedule.getPlannedWeight() != null ? schedule.getPlannedWeight() : BigDecimal.ZERO;
            BasWorkCenter wc = workCenterMapper.selectById(oper.getWcId());

            BigDecimal hoursNeeded = BigDecimal.ONE;
            if (wc != null && wc.getStdCapacity() != null && wc.getStdCapacity().compareTo(BigDecimal.ZERO) > 0) {
                hoursNeeded = weight.divide(wc.getStdCapacity(), 2, java.math.RoundingMode.CEILING);
            }

            long setupMinutes = 0;

            long processMillis = hoursNeeded.multiply(new BigDecimal(3600000)).longValue();
            long setupMillis = setupMinutes * 60 * 1000;

            Date operStart = currentTime;
            Date setupEnd = new Date(operStart.getTime() + setupMillis);
            Date operEnd = new Date(setupEnd.getTime() + processMillis);

            oper.setSetupStart(operStart);
            oper.setSetupEnd(setupEnd);
            oper.setOperStart(setupEnd);
            oper.setOperEnd(operEnd);
            oper.setOperStatus("PLANNED");
            oper.setPlannedWeight(weight);
            oper.setInputWeight(weight);
            oper.setPatName(schedule.getDemandGradeCode());
            oper.setPaName(schedule.getDemandOriginCode());

            currentTime = operEnd;
        }

        if (!opers.isEmpty()) {
            schedule.setScheduleStart(opers.get(0).getOperStart());
            schedule.setScheduleEnd(opers.get(opers.size() - 1).getOperEnd());
        }
    }

    private String generateScheduleNo() {
        int seq = scheduleSeq.incrementAndGet();
        return String.format("SCH-%tY%<tm%<td-%06d", new Date(), seq);
    }

    private String inferOutputFlowType(BasMaterial material) {
        if (material == null) return "FG_STOCK";
        String type = material.getMaterialType();
        if ("FG".equals(type)) return "FG_STOCK";
        if ("SEMI".equals(type)) return "SEMI_STOCK";
        if ("RAW".equals(type)) return "SEMI_STOCK";
        return "FG_STOCK";
    }

    private Long assignWorkCenter(BasRoutingOper routingOper,
                                  Map<String, List<BasWorkCenter>> wcByType,
                                  String strategy) {
        if (routingOper.getWcId() != null) {
            return routingOper.getWcId();
        }
        if (routingOper.getAltWcId() != null) {
            return routingOper.getAltWcId();
        }
        List<BasWorkCenter> candidates = wcByType.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        if (!candidates.isEmpty()) {
            return candidates.get(0).getWcId();
        }
        return null;
    }
}
