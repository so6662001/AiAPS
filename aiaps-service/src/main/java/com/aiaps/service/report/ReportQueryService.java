package com.aiaps.service.report;

import com.aiaps.domain.aps.ApsSchedule;
import com.aiaps.domain.aps.ApsScheduleOper;
import com.aiaps.domain.base.BasWorkCenter;
import com.aiaps.domain.demand.DemDemandLine;
import com.aiaps.domain.production.PrdReport;
import com.aiaps.mapper.aps.ApsScheduleMapper;
import com.aiaps.mapper.aps.ApsScheduleOperMapper;
import com.aiaps.mapper.base.BasMoldMapper;
import com.aiaps.mapper.base.BasWorkCenterMapper;
import com.aiaps.mapper.demand.DemDemandHeadMapper;
import com.aiaps.mapper.demand.DemDemandLineMapper;
import com.aiaps.mapper.inventory.InvStockMapper;
import com.aiaps.mapper.mrp.MrpPlanOrderMapper;
import com.aiaps.mapper.mrp.MrpRunLogMapper;
import com.aiaps.mapper.production.PrdReportMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportQueryService {

    private final ApsScheduleMapper scheduleMapper;
    private final ApsScheduleOperMapper scheduleOperMapper;
    private final DemDemandLineMapper demandLineMapper;
    private final DemDemandHeadMapper demandHeadMapper;
    private final InvStockMapper stockMapper;
    private final PrdReportMapper reportMapper;
    private final BasWorkCenterMapper workCenterMapper;
    private final BasMoldMapper moldMapper;
    private final MrpPlanOrderMapper planOrderMapper;
    private final MrpRunLogMapper runLogMapper;

    /**
     * R01: 订单生产进度
     */
    public List<Map<String, Object>> getOrderProgress(String demandSource,
                                                       String customerCode,
                                                       String contractNo,
                                                       Date dateFrom,
                                                       Date dateTo) {
        LambdaQueryWrapper<DemDemandLine> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DemDemandLine::getLineStatus, "OPEN", "PARTIAL", "COMPLETED");
        if (dateFrom != null) {
            wrapper.ge(DemDemandLine::getRequiredDate, dateFrom);
        }
        if (dateTo != null) {
            wrapper.le(DemDemandLine::getRequiredDate, dateTo);
        }
        if (contractNo != null && !contractNo.isEmpty()) {
            wrapper.eq(DemDemandLine::getContractNo, contractNo);
        }

        List<DemDemandLine> lines = demandLineMapper.selectList(wrapper);
        List<Map<String, Object>> results = new ArrayList<>();

        for (DemDemandLine line : lines) {
            Map<String, Object> row = new HashMap<>();
            row.put("demandLineId", line.getDemandLineId());
            row.put("prdtId", line.getPrdtId());
            row.put("patName", line.getPatName());
            row.put("paName", line.getPaName());
            row.put("contractNo", line.getContractNo());
            row.put("requiredWeight", line.getRequiredWeight());
            row.put("producedWeight", line.getProducedWeight());

            BigDecimal reqW = line.getRequiredWeight() != null ? line.getRequiredWeight() : BigDecimal.ZERO;
            BigDecimal prodW = line.getProducedWeight() != null ? line.getProducedWeight() : BigDecimal.ZERO;
            row.put("progressPct", reqW.compareTo(BigDecimal.ZERO) > 0
                    ? prodW.divide(reqW, 4, RoundingMode.HALF_UP).multiply(new BigDecimal(100))
                    : BigDecimal.ZERO);
            row.put("requiredDate", line.getRequiredDate());
            row.put("status", line.getLineStatus());

            List<ApsSchedule> schedules = scheduleMapper.selectList(
                    new LambdaQueryWrapper<ApsSchedule>()
                            .eq(ApsSchedule::getContractNo, line.getContractNo())
                            .eq(ApsSchedule::getPrdtId, line.getPrdtId())
                            .ne(ApsSchedule::getScheduleStatus, "CANCELLED")
                            .orderByDesc(ApsSchedule::getScheduleEnd));
            if (!schedules.isEmpty() && schedules.get(0).getScheduleEnd() != null) {
                row.put("estimatedCompletion", schedules.get(0).getScheduleEnd());
            }
            results.add(row);
        }
        return results;
    }

    /**
     * R02: 排程执行情况
     */
    public List<Map<String, Object>> getScheduleExecution(Long wcId, Date date) {
        LambdaQueryWrapper<ApsSchedule> wrapper = new LambdaQueryWrapper<>();
        if (wcId != null) {
            LambdaQueryWrapper<ApsScheduleOper> operWrapper = new LambdaQueryWrapper<>();
            operWrapper.eq(ApsScheduleOper::getWcId, wcId);
            List<ApsScheduleOper> opers = scheduleOperMapper.selectList(operWrapper);
            if (opers.isEmpty()) {
                return Collections.emptyList();
            }
            List<Long> scheduleIds = opers.stream()
                    .map(ApsScheduleOper::getScheduleId)
                    .distinct()
                    .collect(Collectors.toList());
            wrapper.in(ApsSchedule::getScheduleId, scheduleIds);
        }
        if (date != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Date dayStart = cal.getTime();
            cal.add(Calendar.DAY_OF_MONTH, 1);
            Date dayEnd = cal.getTime();
            wrapper.ge(ApsSchedule::getScheduleStart, dayStart);
            wrapper.lt(ApsSchedule::getScheduleStart, dayEnd);
        }
        wrapper.ne(ApsSchedule::getScheduleStatus, "CANCELLED");
        wrapper.orderByAsc(ApsSchedule::getScheduleStart);

        List<ApsSchedule> schedules = scheduleMapper.selectList(wrapper);
        List<Map<String, Object>> results = new ArrayList<>();

        for (ApsSchedule s : schedules) {
            Map<String, Object> row = new HashMap<>();
            row.put("scheduleId", s.getScheduleId());
            row.put("scheduleNo", s.getScheduleNo());
            row.put("prdtId", s.getPrdtId());
            row.put("contractNo", s.getContractNo());
            row.put("customerName", s.getCustomerName());
            row.put("plannedQty", s.getPlannedQty());
            row.put("plannedWeight", s.getPlannedWeight());
            row.put("goodQty", s.getGoodQty());
            row.put("goodWeight", s.getGoodWeight());
            row.put("scrapQty", s.getScrapQty());
            row.put("scheduleStart", s.getScheduleStart());
            row.put("scheduleEnd", s.getScheduleEnd());
            row.put("scheduleStatus", s.getScheduleStatus());
            row.put("priority", s.getPriority());

            BigDecimal planned = s.getPlannedWeight() != null ? s.getPlannedWeight() : BigDecimal.ZERO;
            BigDecimal good = s.getGoodWeight() != null ? s.getGoodWeight() : BigDecimal.ZERO;
            row.put("completionPct", planned.compareTo(BigDecimal.ZERO) > 0
                    ? good.divide(planned, 4, RoundingMode.HALF_UP).multiply(new BigDecimal(100))
                    : BigDecimal.ZERO);
            results.add(row);
        }
        return results;
    }

    /**
     * R04: 交期达成率
     */
    public Map<String, Object> getDeliveryRate(Date dateFrom, Date dateTo) {
        LambdaQueryWrapper<DemDemandLine> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DemDemandLine::getLineStatus, "COMPLETED");
        if (dateFrom != null) {
            wrapper.ge(DemDemandLine::getRequiredDate, dateFrom);
        }
        if (dateTo != null) {
            wrapper.le(DemDemandLine::getRequiredDate, dateTo);
        }

        List<DemDemandLine> completed = demandLineMapper.selectList(wrapper);
        int total = completed.size();
        int onTime = 0;
        int early = 0;
        int late = 0;

        for (DemDemandLine line : completed) {
            if (line.getRequiredDate() != null && line.getProducedWeight() != null
                    && line.getRequiredWeight() != null
                    && line.getProducedWeight().compareTo(line.getRequiredWeight()) >= 0) {
                onTime++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalOrders", total);
        result.put("onTimeOrders", onTime);
        result.put("earlyOrders", early);
        result.put("lateOrders", late);
        result.put("onTimeRate", total > 0 ? (double) onTime / total * 100 : 0);
        return result;
    }

    /**
     * R05: 延期预警
     */
    public List<Map<String, Object>> getDelayWarning() {
        Date now = new Date();
        LambdaQueryWrapper<DemDemandLine> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DemDemandLine::getLineStatus, "OPEN", "PARTIAL");
        wrapper.le(DemDemandLine::getRequiredDate, now);

        List<DemDemandLine> delayed = demandLineMapper.selectList(wrapper);
        List<Map<String, Object>> results = new ArrayList<>();

        for (DemDemandLine line : delayed) {
            Map<String, Object> row = new HashMap<>();
            row.put("demandLineId", line.getDemandLineId());
            row.put("contractNo", line.getContractNo());
            row.put("prdtId", line.getPrdtId());
            row.put("patName", line.getPatName());
            row.put("requiredWeight", line.getRequiredWeight());
            row.put("producedWeight", line.getProducedWeight());
            row.put("requiredDate", line.getRequiredDate());
            long delayDays = (now.getTime() - line.getRequiredDate().getTime()) / (24 * 3600 * 1000L);
            row.put("delayDays", delayDays);
            row.put("severity", delayDays > 7 ? "HIGH" : delayDays > 3 ? "MEDIUM" : "LOW");
            results.add(row);
        }
        return results;
    }

    /**
     * R06: 合同进度
     */
    public List<Map<String, Object>> getContractProgress(String contractNo) {
        LambdaQueryWrapper<DemDemandLine> lineWrapper = new LambdaQueryWrapper<>();
        lineWrapper.eq(DemDemandLine::getContractNo, contractNo);
        List<DemDemandLine> lines = demandLineMapper.selectList(lineWrapper);

        LambdaQueryWrapper<ApsSchedule> schedWrapper = new LambdaQueryWrapper<>();
        schedWrapper.eq(ApsSchedule::getContractNo, contractNo);
        schedWrapper.ne(ApsSchedule::getScheduleStatus, "CANCELLED");
        schedWrapper.orderByAsc(ApsSchedule::getScheduleStart);
        List<ApsSchedule> schedules = scheduleMapper.selectList(schedWrapper);

        List<Map<String, Object>> results = new ArrayList<>();
        for (DemDemandLine line : lines) {
            Map<String, Object> row = new HashMap<>();
            row.put("demandLineId", line.getDemandLineId());
            row.put("prdtId", line.getPrdtId());
            row.put("patName", line.getPatName());
            row.put("paName", line.getPaName());
            row.put("requiredQty", line.getRequiredQty());
            row.put("requiredWeight", line.getRequiredWeight());
            row.put("producedQty", line.getProducedQty());
            row.put("producedWeight", line.getProducedWeight());
            row.put("requiredDate", line.getRequiredDate());
            row.put("lineStatus", line.getLineStatus());

            BigDecimal reqW = line.getRequiredWeight() != null ? line.getRequiredWeight() : BigDecimal.ZERO;
            BigDecimal prodW = line.getProducedWeight() != null ? line.getProducedWeight() : BigDecimal.ZERO;
            row.put("progressPct", reqW.compareTo(BigDecimal.ZERO) > 0
                    ? prodW.divide(reqW, 4, RoundingMode.HALF_UP).multiply(new BigDecimal(100))
                    : BigDecimal.ZERO);

            List<Map<String, Object>> linkedSchedules = new ArrayList<>();
            for (ApsSchedule s : schedules) {
                if (line.getPrdtId() != null && line.getPrdtId().equals(s.getPrdtId())) {
                    Map<String, Object> schedRow = new HashMap<>();
                    schedRow.put("scheduleId", s.getScheduleId());
                    schedRow.put("scheduleNo", s.getScheduleNo());
                    schedRow.put("scheduleStatus", s.getScheduleStatus());
                    schedRow.put("plannedWeight", s.getPlannedWeight());
                    schedRow.put("goodWeight", s.getGoodWeight());
                    schedRow.put("scheduleStart", s.getScheduleStart());
                    schedRow.put("scheduleEnd", s.getScheduleEnd());
                    linkedSchedules.add(schedRow);
                }
            }
            row.put("schedules", linkedSchedules);
            results.add(row);
        }
        return results;
    }

    /**
     * R07: 产能利用率
     */
    public List<Map<String, Object>> getCapacityUtilization(List<Long> wcIds,
                                                             Date dateFrom,
                                                             Date dateTo) {
        List<BasWorkCenter> wcs;
        if (wcIds != null && !wcIds.isEmpty()) {
            wcs = workCenterMapper.selectBatchIds(wcIds);
        } else {
            wcs = workCenterMapper.selectList(
                    new LambdaQueryWrapper<BasWorkCenter>().eq(BasWorkCenter::getIsActive, true));
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (BasWorkCenter wc : wcs) {
            Map<String, Object> row = new HashMap<>();
            row.put("wcId", wc.getWcId());
            row.put("wcName", wc.getWcName());
            row.put("wcCode", wc.getWcCode());
            row.put("shiftMode", wc.getShiftMode());

            LambdaQueryWrapper<ApsScheduleOper> operWrapper = new LambdaQueryWrapper<>();
            operWrapper.eq(ApsScheduleOper::getWcId, wc.getWcId());
            if (dateFrom != null) {
                operWrapper.ge(ApsScheduleOper::getOperStart, dateFrom);
            }
            if (dateTo != null) {
                operWrapper.le(ApsScheduleOper::getOperEnd, dateTo);
            }
            operWrapper.eq(ApsScheduleOper::getOperStatus, "COMPLETED");

            List<ApsScheduleOper> opers = scheduleOperMapper.selectList(operWrapper);

            BigDecimal totalHours = BigDecimal.ZERO;
            BigDecimal loadedHours = BigDecimal.ZERO;

            if (dateFrom != null && dateTo != null) {
                long days = Math.max(1, (dateTo.getTime() - dateFrom.getTime()) / (24 * 3600 * 1000L));
                BigDecimal hps = wc.getHoursPerShift() != null ? wc.getHoursPerShift() : new BigDecimal(8);
                int shifts = "3S".equals(wc.getShiftMode()) ? 3 : "2S".equals(wc.getShiftMode()) ? 2 : 1;
                totalHours = hps.multiply(BigDecimal.valueOf(shifts)).multiply(BigDecimal.valueOf(days));
            }

            for (ApsScheduleOper oper : opers) {
                if (oper.getOperStart() != null && oper.getOperEnd() != null) {
                    long ms = oper.getOperEnd().getTime() - oper.getOperStart().getTime();
                    loadedHours = loadedHours.add(
                            BigDecimal.valueOf(ms).divide(BigDecimal.valueOf(3600000), 2, RoundingMode.HALF_UP));
                }
            }

            row.put("totalHours", totalHours);
            row.put("loadedHours", loadedHours);
            row.put("utilizationPct", totalHours.compareTo(BigDecimal.ZERO) > 0
                    ? loadedHours.divide(totalHours, 4, RoundingMode.HALF_UP).multiply(new BigDecimal(100))
                    : BigDecimal.ZERO);
            results.add(row);
        }
        return results;
    }

    /**
     * R10: 成材率分析
     */
    public List<Map<String, Object>> getYieldAnalysis(Date dateFrom, Date dateTo) {
        LambdaQueryWrapper<ApsSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApsSchedule::getScheduleStatus, "COMPLETED");
        if (dateFrom != null) {
            wrapper.ge(ApsSchedule::getScheduleStart, dateFrom);
        }
        if (dateTo != null) {
            wrapper.le(ApsSchedule::getScheduleEnd, dateTo);
        }
        wrapper.isNotNull(ApsSchedule::getYieldRate);

        List<ApsSchedule> schedules = scheduleMapper.selectList(wrapper);

        Map<Long, List<ApsSchedule>> byMaterial = schedules.stream()
                .collect(Collectors.groupingBy(ApsSchedule::getPrdtId));

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map.Entry<Long, List<ApsSchedule>> entry : byMaterial.entrySet()) {
            Map<String, Object> row = new HashMap<>();
            row.put("prdtId", entry.getKey());

            BigDecimal totalInput = BigDecimal.ZERO;
            BigDecimal totalGood = BigDecimal.ZERO;
            BigDecimal totalScrap = BigDecimal.ZERO;

            for (ApsSchedule s : entry.getValue()) {
                if (s.getInputWeight() != null) {
                    totalInput = totalInput.add(s.getInputWeight());
                }
                if (s.getGoodWeight() != null) {
                    totalGood = totalGood.add(s.getGoodWeight());
                }
                if (s.getScrapWeight() != null) {
                    totalScrap = totalScrap.add(s.getScrapWeight());
                }
            }

            row.put("inputWeight", totalInput);
            row.put("goodWeight", totalGood);
            row.put("scrapWeight", totalScrap);
            row.put("yieldRate", totalInput.compareTo(BigDecimal.ZERO) > 0
                    ? totalGood.divide(totalInput, 4, RoundingMode.HALF_UP).multiply(new BigDecimal(100))
                    : BigDecimal.ZERO);
            row.put("scheduleCount", entry.getValue().size());
            results.add(row);
        }
        return results;
    }

    /**
     * R11: 产量日报
     */
    public List<Map<String, Object>> getDailyOutput(Date date, Long wcId) {
        LambdaQueryWrapper<PrdReport> wrapper = new LambdaQueryWrapper<>();
        if (date != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Date dayStart = cal.getTime();
            cal.add(Calendar.DAY_OF_MONTH, 1);
            Date dayEnd = cal.getTime();
            wrapper.ge(PrdReport::getReportTime, dayStart);
            wrapper.lt(PrdReport::getReportTime, dayEnd);
        }

        List<PrdReport> reports = reportMapper.selectList(wrapper);

        Map<String, List<PrdReport>> byShift = reports.stream()
                .collect(Collectors.groupingBy(r -> r.getShiftCode() != null ? r.getShiftCode() : "UNKNOWN"));

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map.Entry<String, List<PrdReport>> entry : byShift.entrySet()) {
            Map<String, Object> row = new HashMap<>();
            row.put("shiftCode", entry.getKey());
            BigDecimal totalGood = BigDecimal.ZERO;
            BigDecimal totalScrap = BigDecimal.ZERO;
            BigDecimal totalInput = BigDecimal.ZERO;

            for (PrdReport r : entry.getValue()) {
                if (r.getGoodWeight() != null) {
                    totalGood = totalGood.add(r.getGoodWeight());
                }
                if (r.getScrapWeight() != null) {
                    totalScrap = totalScrap.add(r.getScrapWeight());
                }
                if (r.getInputWeight() != null) {
                    totalInput = totalInput.add(r.getInputWeight());
                }
            }

            row.put("goodWeight", totalGood);
            row.put("scrapWeight", totalScrap);
            row.put("inputWeight", totalInput);
            row.put("yieldRate", totalInput.compareTo(BigDecimal.ZERO) > 0
                    ? totalGood.divide(totalInput, 4, RoundingMode.HALF_UP).multiply(new BigDecimal(100))
                    : BigDecimal.ZERO);
            row.put("reportCount", entry.getValue().size());
            results.add(row);
        }
        return results;
    }
}
