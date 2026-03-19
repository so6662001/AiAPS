package com.aiaps.service.aps;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.aps.ApsSchedule;
import com.aiaps.domain.aps.ApsScheduleOper;
import com.aiaps.domain.base.BasRoutingOper;
import com.aiaps.domain.base.BasWorkCenter;
import com.aiaps.domain.mrp.MrpPlanOrder;
import com.aiaps.mapper.aps.ApsScheduleMapper;
import com.aiaps.mapper.aps.ApsScheduleOperMapper;
import com.aiaps.mapper.base.BasMoldMapper;
import com.aiaps.mapper.base.BasRoutingOperMapper;
import com.aiaps.mapper.base.BasWorkCenterMapper;
import com.aiaps.mapper.mrp.MrpPlanOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleEngineService {

    private final ApsScheduleMapper scheduleMapper;
    private final ApsScheduleOperMapper scheduleOperMapper;
    private final BasWorkCenterMapper workCenterMapper;
    private final BasMoldMapper moldMapper;
    private final BasRoutingOperMapper routingOperMapper;
    private final MrpPlanOrderMapper planOrderMapper;

    @Transactional
    public List<ApsSchedule> autoSchedule(List<Long> planOrderIds, String strategy) {
        List<ApsSchedule> schedules = new ArrayList<>();

        List<MrpPlanOrder> planOrders = planOrderMapper.selectBatchIds(planOrderIds);
        if (planOrders.isEmpty()) {
            throw new BizException("未找到指定的计划订单");
        }

        List<BasWorkCenter> allWcs = workCenterMapper.selectList(
                new LambdaQueryWrapper<BasWorkCenter>().eq(BasWorkCenter::getIsActive, true));
        Map<String, List<BasWorkCenter>> wcByType = allWcs.stream()
                .collect(Collectors.groupingBy(wc -> wc.getWcType() != null ? wc.getWcType() : "DEFAULT"));

        for (MrpPlanOrder planOrder : planOrders) {
            ApsSchedule schedule = new ApsSchedule();
            schedule.setScheduleNo("SCH" + System.currentTimeMillis());
            schedule.setPlanOrderId(planOrder.getPlanOrderId());
            schedule.setPrdtId(planOrder.getPrdtId());
            schedule.setPlannedQty(planOrder.getPlannedQty());
            schedule.setPlannedWeight(planOrder.getPlannedWeight());
            schedule.setDemandSource(planOrder.getDemandSource());
            schedule.setContractNo(planOrder.getContractNo());
            schedule.setDemandGradeCode(planOrder.getPatName());
            schedule.setDemandOriginCode(planOrder.getPaName());
            schedule.setScheduleStatus("DRAFT");
            schedule.setIsLocked(false);
            schedule.setScheduleVersion(1);
            schedule.setCreatedTime(new Date());

            scheduleMapper.insert(schedule);

            List<BasRoutingOper> routingOpers = findRoutingOpers(planOrder.getPrdtId());
            int seq = 1;
            for (BasRoutingOper routingOper : routingOpers) {
                ApsScheduleOper oper = new ApsScheduleOper();
                oper.setScheduleId(schedule.getScheduleId());
                oper.setOperNo(routingOper.getOperNo());
                oper.setOperName(routingOper.getOperName());
                oper.setPlannedQty(planOrder.getPlannedQty());
                oper.setPlannedWeight(planOrder.getPlannedWeight());
                oper.setOperStatus("PENDING");
                oper.setIsLocked(false);
                oper.setSequenceInWc(seq++);

                Long wcId = assignWorkCenter(routingOper, wcByType, strategy);
                oper.setWcId(wcId);

                scheduleOperMapper.insert(oper);
            }

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

    private List<BasRoutingOper> findRoutingOpers(Long prdtId) {
        LambdaQueryWrapper<BasRoutingOper> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(BasRoutingOper::getOperNo);
        List<BasRoutingOper> all = routingOperMapper.selectList(wrapper);
        return all;
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
