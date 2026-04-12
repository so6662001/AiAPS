package com.aiaps.service.report;

import com.aiaps.domain.aps.ApsSchedule;
import com.aiaps.domain.aps.ApsScheduleOper;
import com.aiaps.domain.base.BasMold;
import com.aiaps.domain.base.BasWorkCenter;
import com.aiaps.domain.demand.DemDemandLine;
import com.aiaps.domain.mrp.MrpRunLog;
import com.aiaps.domain.production.PrdReport;
import com.aiaps.mapper.aps.ApsScheduleMapper;
import com.aiaps.mapper.aps.ApsScheduleOperMapper;
import com.aiaps.mapper.base.BasMoldMapper;
import com.aiaps.mapper.base.BasWorkCenterMapper;
import com.aiaps.mapper.demand.DemDemandLineMapper;
import com.aiaps.mapper.mrp.MrpRunLogMapper;
import com.aiaps.mapper.production.PrdReportMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ApsScheduleMapper scheduleMapper;
    private final DemDemandLineMapper demandLineMapper;
    private final BasWorkCenterMapper workCenterMapper;
    private final MrpRunLogMapper runLogMapper;
    private final ApsScheduleOperMapper scheduleOperMapper;
    private final PrdReportMapper reportMapper;
    private final BasMoldMapper moldMapper;

    /**
     * R18: 指挥中心 — production command center overview
     */
    public Map<String, Object> getCommandCenter() {
        Map<String, Object> result = new HashMap<>();

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date todayStart = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        Date todayEnd = cal.getTime();

        // Today's output
        LambdaQueryWrapper<PrdReport> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.ge(PrdReport::getReportTime, todayStart);
        todayWrapper.lt(PrdReport::getReportTime, todayEnd);
        List<PrdReport> todayReports = reportMapper.selectList(todayWrapper);

        BigDecimal todayOutput = BigDecimal.ZERO;
        for (PrdReport r : todayReports) {
            if (r.getGoodWeight() != null) {
                todayOutput = todayOutput.add(r.getGoodWeight());
            }
        }
        result.put("todayOutput", todayOutput);

        // Month total
        cal.setTime(todayStart);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        Date monthStart = cal.getTime();

        LambdaQueryWrapper<PrdReport> monthWrapper = new LambdaQueryWrapper<>();
        monthWrapper.ge(PrdReport::getReportTime, monthStart);
        monthWrapper.lt(PrdReport::getReportTime, todayEnd);
        List<PrdReport> monthReports = reportMapper.selectList(monthWrapper);

        BigDecimal monthTotal = BigDecimal.ZERO;
        for (PrdReport r : monthReports) {
            if (r.getGoodWeight() != null) {
                monthTotal = monthTotal.add(r.getGoodWeight());
            }
        }
        result.put("monthTotal", monthTotal);

        // Delivery rate — completed demands
        LambdaQueryWrapper<DemDemandLine> completedWrapper = new LambdaQueryWrapper<>();
        completedWrapper.eq(DemDemandLine::getLineStatus, "COMPLETED");
        Long completedCount = demandLineMapper.selectCount(completedWrapper);

        LambdaQueryWrapper<DemDemandLine> allWrapper = new LambdaQueryWrapper<>();
        allWrapper.in(DemDemandLine::getLineStatus, "OPEN", "PARTIAL", "COMPLETED");
        Long allCount = demandLineMapper.selectCount(allWrapper);

        result.put("deliveryRate", allCount > 0 ? (double) completedCount / allCount * 100 : 0);

        // Active orders
        LambdaQueryWrapper<DemDemandLine> activeWrapper = new LambdaQueryWrapper<>();
        activeWrapper.in(DemDemandLine::getLineStatus, "OPEN", "PARTIAL");
        Long activeOrders = demandLineMapper.selectCount(activeWrapper);
        result.put("activeOrders", activeOrders);

        // Work center line statuses with current tasks
        List<BasWorkCenter> wcs = workCenterMapper.selectList(
                new LambdaQueryWrapper<BasWorkCenter>().eq(BasWorkCenter::getIsActive, true));
        List<Map<String, Object>> lineStatuses = new ArrayList<>();
        for (BasWorkCenter wc : wcs) {
            Map<String, Object> wcStatus = new HashMap<>();
            wcStatus.put("wcId", wc.getWcId());
            wcStatus.put("wcName", wc.getWcName());
            wcStatus.put("wcCode", wc.getWcCode());

            LambdaQueryWrapper<ApsScheduleOper> operWrapper = new LambdaQueryWrapper<>();
            operWrapper.eq(ApsScheduleOper::getWcId, wc.getWcId());
            operWrapper.eq(ApsScheduleOper::getOperStatus, "IN_PROGRESS");
            operWrapper.last("LIMIT 1");
            List<ApsScheduleOper> currentOps = scheduleOperMapper.selectList(operWrapper);
            if (!currentOps.isEmpty()) {
                ApsScheduleOper currentOp = currentOps.get(0);
                wcStatus.put("currentScheduleId", currentOp.getScheduleId());
                wcStatus.put("currentOperName", currentOp.getOperName());
                wcStatus.put("status", "RUNNING");
            } else {
                wcStatus.put("status", "IDLE");
            }
            lineStatuses.add(wcStatus);
        }
        result.put("lineStatuses", lineStatuses);

        // Urgent items — demands due within 3 days that are not completed
        cal.setTime(new Date());
        cal.add(Calendar.DAY_OF_MONTH, 3);
        Date urgentDeadline = cal.getTime();

        LambdaQueryWrapper<DemDemandLine> urgentWrapper = new LambdaQueryWrapper<>();
        urgentWrapper.in(DemDemandLine::getLineStatus, "OPEN", "PARTIAL");
        urgentWrapper.le(DemDemandLine::getRequiredDate, urgentDeadline);
        List<DemDemandLine> urgentLines = demandLineMapper.selectList(urgentWrapper);

        List<Map<String, Object>> urgentItems = new ArrayList<>();
        for (DemDemandLine line : urgentLines) {
            Map<String, Object> item = new HashMap<>();
            item.put("demandLineId", line.getDemandLineId());
            item.put("contractNo", line.getContractNo());
            item.put("patName", line.getPatName());
            item.put("requiredDate", line.getRequiredDate());
            item.put("requiredWeight", line.getRequiredWeight());
            item.put("producedWeight", line.getProducedWeight());
            urgentItems.add(item);
        }
        result.put("urgentItems", urgentItems);

        return result;
    }

    /**
     * R20: 预警列表
     */
    public List<Map<String, Object>> getAlerts() {
        List<Map<String, Object>> alerts = new ArrayList<>();
        Date now = new Date();

        // Delayed orders
        LambdaQueryWrapper<DemDemandLine> delayWrapper = new LambdaQueryWrapper<>();
        delayWrapper.in(DemDemandLine::getLineStatus, "OPEN", "PARTIAL");
        delayWrapper.lt(DemDemandLine::getRequiredDate, now);
        List<DemDemandLine> delayed = demandLineMapper.selectList(delayWrapper);
        for (DemDemandLine line : delayed) {
            Map<String, Object> alert = new HashMap<>();
            alert.put("type", "DELAY");
            alert.put("severity", "HIGH");
            alert.put("message", "订单延期: " + line.getContractNo() + " - " + line.getPatName());
            alert.put("contractNo", line.getContractNo());
            alert.put("demandLineId", line.getDemandLineId());
            alert.put("requiredDate", line.getRequiredDate());
            long delayDays = (now.getTime() - line.getRequiredDate().getTime()) / (24 * 3600 * 1000L);
            alert.put("delayDays", delayDays);
            alerts.add(alert);
        }

        // Mold warnings — molds approaching max production
        LambdaQueryWrapper<BasMold> moldWrapper = new LambdaQueryWrapper<>();
        moldWrapper.eq(BasMold::getIsActive, true);
        moldWrapper.isNotNull(BasMold::getMaxProductionQty);
        moldWrapper.isNotNull(BasMold::getCurrentAccumulated);
        List<BasMold> molds = moldMapper.selectList(moldWrapper);
        for (BasMold mold : molds) {
            if (mold.getWarningPct() != null && mold.getMaxProductionQty() != null
                    && mold.getCurrentAccumulated() != null) {
                BigDecimal threshold = mold.getMaxProductionQty()
                        .multiply(mold.getWarningPct())
                        .divide(new BigDecimal(100), 2, java.math.RoundingMode.HALF_UP);
                if (mold.getCurrentAccumulated().compareTo(threshold) >= 0) {
                    Map<String, Object> alert = new HashMap<>();
                    alert.put("type", "MOLD_WARNING");
                    alert.put("severity", "MEDIUM");
                    alert.put("message", "模具寿命预警: " + mold.getMoldCode() + " (" + mold.getMoldName() + ")");
                    alert.put("moldId", mold.getMoldId());
                    alert.put("moldCode", mold.getMoldCode());
                    alert.put("currentAccumulated", mold.getCurrentAccumulated());
                    alert.put("maxProductionQty", mold.getMaxProductionQty());
                    alert.put("usagePct", mold.getCurrentAccumulated()
                            .divide(mold.getMaxProductionQty(), 4, java.math.RoundingMode.HALF_UP)
                            .multiply(new BigDecimal(100)));
                    alerts.add(alert);
                }
            }
        }

        return alerts;
    }

    /**
     * R21: MRP运行监控
     */
    public List<Map<String, Object>> getMrpMonitor() {
        LambdaQueryWrapper<MrpRunLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(MrpRunLog::getStartTime);
        wrapper.last("LIMIT 5");
        List<MrpRunLog> logs = runLogMapper.selectList(wrapper);

        List<Map<String, Object>> results = new ArrayList<>();
        for (MrpRunLog log : logs) {
            Map<String, Object> row = new HashMap<>();
            row.put("runId", log.getRunId());
            row.put("runNo", log.getRunNo());
            row.put("runType", log.getRunType());
            row.put("runScope", log.getRunScope());
            row.put("runStatus", log.getRunStatus());
            row.put("startTime", log.getStartTime());
            row.put("endTime", log.getEndTime());
            row.put("demandCount", log.getDemandCount());
            row.put("planOrderCount", log.getPlanOrderCount());
            row.put("purchaseCount", log.getPurchaseCount());
            row.put("errorMessage", log.getErrorMessage());
            row.put("runBy", log.getRunBy());

            if (log.getStartTime() != null && log.getEndTime() != null) {
                long durationMs = log.getEndTime().getTime() - log.getStartTime().getTime();
                row.put("durationSeconds", durationMs / 1000);
            }
            results.add(row);
        }
        return results;
    }
}
