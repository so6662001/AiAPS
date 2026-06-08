package com.aiaps.service.aps;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.aps.ApsSchedule;
import com.aiaps.domain.aps.ApsScheduleOper;
import com.aiaps.domain.base.BasSwapCostParam;
import com.aiaps.domain.inventory.InvStock;
import com.aiaps.domain.base.BasMaterial;
import com.aiaps.mapper.base.BasSwapCostParamMapper;
import com.aiaps.mapper.aps.ApsScheduleMapper;
import com.aiaps.mapper.aps.ApsScheduleOperMapper;
import com.aiaps.mapper.inventory.InvStockMapper;
import com.aiaps.mapper.base.BasMaterialMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SmartSwapService {

    private final BasSwapCostParamMapper swapCostParamMapper;
    private final ApsScheduleMapper scheduleMapper;
    private final ApsScheduleOperMapper scheduleOperMapper;
    private final InvStockMapper invStockMapper;
    private final BasMaterialMapper materialMapper;

    @Autowired
    private ScheduleModificationService modificationService;

    private static final BigDecimal DEFAULT_VIOLATION_COST = BigDecimal.valueOf(5000);

    @Data
    public static class MismatchDecision {
        private Long currentScheduleId;
        private Long actualStockId;
        private String mismatchType;
        private String mismatchDesc;
        private int swapTimeMinutes;
        private BigDecimal swapCost;
        private Long matchingScheduleId;
        private String matchingScheduleNo;
        private BigDecimal orderSwapCost;
        private String recommendation;
        private String recommendReason;
    }

    public MismatchDecision detectAndDecide(Long scheduleId, Long actualStockId) {
        ApsSchedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BizException("排产不存在: " + scheduleId);
        }
        InvStock stock = invStockMapper.selectById(actualStockId);
        if (stock == null) {
            throw new BizException("库存不存在: " + actualStockId);
        }

        String demandGrade = schedule.getDemandGradeCode();
        String demandOrigin = schedule.getDemandOriginCode();
        String stockGrade = stock.getPatName();
        String stockOrigin = stock.getPaName();

        boolean gradeMatch = Objects.equals(demandGrade, stockGrade);
        boolean originMatch = Objects.equals(demandOrigin, stockOrigin);

        if (gradeMatch && originMatch) {
            return null;
        }

        MismatchDecision decision = new MismatchDecision();
        decision.setCurrentScheduleId(scheduleId);
        decision.setActualStockId(actualStockId);

        if (!gradeMatch && !originMatch) {
            decision.setMismatchType("GRADE_AND_ORIGIN");
            decision.setMismatchDesc("牌号和产地均不匹配: 需求[" + demandGrade + "/" + demandOrigin + "] 实际[" + stockGrade + "/" + stockOrigin + "]");
        } else if (!gradeMatch) {
            decision.setMismatchType("GRADE");
            decision.setMismatchDesc("牌号不匹配: 需求[" + demandGrade + "] 实际[" + stockGrade + "]");
        } else {
            decision.setMismatchType("ORIGIN");
            decision.setMismatchDesc("产地不匹配: 需求[" + demandOrigin + "] 实际[" + stockOrigin + "]");
        }

        Long wcId = findWcId(schedule);
        BasSwapCostParam param = wcId != null ? swapCostParamMapper.selectByWcId(wcId) : null;

        int totalSwapMin;
        BigDecimal swapCost;
        BigDecimal threshold;
        if (param != null) {
            int unload = param.getUnloadTimeMin() != null ? param.getUnloadTimeMin() : 0;
            int locate = param.getLocateTimeMin() != null ? param.getLocateTimeMin() : 0;
            int load = param.getLoadTimeMin() != null ? param.getLoadTimeMin() : 0;
            totalSwapMin = unload + locate + load;
            BigDecimal costPerMin = param.getDowntimeCostPerMin() != null ? param.getDowntimeCostPerMin() : BigDecimal.ZERO;
            BigDecimal handling = param.getHandlingCost() != null ? param.getHandlingCost() : BigDecimal.ZERO;
            swapCost = BigDecimal.valueOf(totalSwapMin).multiply(costPerMin).add(handling);
            threshold = param.getSwapThreshold() != null ? param.getSwapThreshold() : BigDecimal.ONE;
        } else {
            totalSwapMin = 30;
            swapCost = BigDecimal.valueOf(500);
            threshold = BigDecimal.ONE;
        }

        decision.setSwapTimeMinutes(totalSwapMin);
        decision.setSwapCost(swapCost);

        ApsSchedule matchingSchedule = findMatchingSchedule(schedule, stockGrade, stockOrigin, wcId);

        if (matchingSchedule != null) {
            decision.setMatchingScheduleId(matchingSchedule.getScheduleId());
            decision.setMatchingScheduleNo(matchingSchedule.getScheduleNo());

            BigDecimal orderSwapCost = BigDecimal.ZERO;
            if (matchingSchedule.getScheduleEnd() != null && schedule.getScheduleEnd() != null
                    && matchingSchedule.getScheduleEnd().after(schedule.getScheduleEnd())) {
                orderSwapCost = DEFAULT_VIOLATION_COST;
            }
            decision.setOrderSwapCost(orderSwapCost);

            if (swapCost.compareTo(orderSwapCost.multiply(threshold)) < 0) {
                decision.setRecommendation("SWAP");
                decision.setRecommendReason("物料换料成本(" + swapCost + ")低于订单互换成本(" + orderSwapCost + ")×阈值(" + threshold + ")");
            } else {
                decision.setRecommendation("ORDER_SWAP");
                decision.setRecommendReason("订单互换成本(" + orderSwapCost + ")更优，建议与排产[" + matchingSchedule.getScheduleNo() + "]互换");
            }
        } else {
            decision.setRecommendation("SWAP");
            decision.setRecommendReason("无匹配排产可互换，直接换料");
            decision.setOrderSwapCost(BigDecimal.ZERO);
        }

        return decision;
    }

    @Transactional
    public Map<String, Object> executeOrderSwap(Long currentScheduleId, Long matchingScheduleId) {
        ApsSchedule current = scheduleMapper.selectById(currentScheduleId);
        ApsSchedule matching = scheduleMapper.selectById(matchingScheduleId);
        if (current == null || matching == null) {
            throw new BizException("排产不存在");
        }

        Date tempStart = current.getScheduleStart();
        Date tempEnd = current.getScheduleEnd();

        current.setScheduleStart(matching.getScheduleStart());
        current.setScheduleEnd(matching.getScheduleEnd());
        current.setUpdatedTime(new Date());

        matching.setScheduleStart(tempStart);
        matching.setScheduleEnd(tempEnd);
        matching.setUpdatedTime(new Date());

        scheduleMapper.updateById(current);
        scheduleMapper.updateById(matching);

        // Record change logs
        modificationService.recordChangeLog("SCHEDULE", currentScheduleId, current.getScheduleNo(), "MOVE", "SYSTEM", "智能调单: 与" + matching.getScheduleNo() + "互换", null);
        modificationService.recordChangeLog("SCHEDULE", matchingScheduleId, matching.getScheduleNo(), "MOVE", "SYSTEM", "智能调单: 与" + current.getScheduleNo() + "互换", null);

        Map<String, Object> result = new HashMap<>();
        result.put("currentScheduleId", currentScheduleId);
        result.put("matchingScheduleId", matchingScheduleId);
        result.put("status", "SWAPPED");
        result.put("message", "排产[" + current.getScheduleNo() + "]与[" + matching.getScheduleNo() + "]已互换时间");
        return result;
    }

    private Long findWcId(ApsSchedule schedule) {
        List<ApsScheduleOper> opers = scheduleOperMapper.selectByScheduleId(schedule.getScheduleId());
        if (opers != null && !opers.isEmpty()) {
            return opers.get(0).getWcId();
        }
        return null;
    }

    private ApsSchedule findMatchingSchedule(ApsSchedule current, String stockGrade, String stockOrigin, Long wcId) {
        if (wcId == null) return null;

        List<ApsSchedule> candidates = scheduleMapper.selectQueueByWc(wcId, "CONFIRMED");
        if (candidates == null) return null;

        for (ApsSchedule candidate : candidates) {
            if (candidate.getScheduleId().equals(current.getScheduleId())) continue;

            // Must be same product (same physical material spec to be interchangeable)
            if (!Objects.equals(candidate.getPrdtId(), current.getPrdtId())) continue;

            boolean gradeOk = Objects.equals(candidate.getDemandGradeCode(), stockGrade);
            boolean originOk = Objects.equals(candidate.getDemandOriginCode(), stockOrigin);
            if (gradeOk && originOk) {
                return candidate;
            }
        }
        return null;
    }
}
