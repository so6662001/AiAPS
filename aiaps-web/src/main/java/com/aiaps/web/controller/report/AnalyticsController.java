package com.aiaps.web.controller.report;

import com.aiaps.common.result.R;
import com.aiaps.service.report.ReportQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final ReportQueryService reportQueryService;

    /**
     * R01: 订单生产进度
     */
    @GetMapping("/order-progress")
    public R<List<Map<String, Object>>> getOrderProgress(
            @RequestParam(required = false) String demandSource,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String contractNo,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateTo) {
        return R.ok(reportQueryService.getOrderProgress(demandSource, customerCode, contractNo, dateFrom, dateTo));
    }

    /**
     * R02: 排程执行情况
     */
    @GetMapping("/schedule-execution")
    public R<List<Map<String, Object>>> getScheduleExecution(
            @RequestParam(required = false) Long wcId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date date) {
        return R.ok(reportQueryService.getScheduleExecution(wcId, date));
    }

    /**
     * R04: 交期达成率
     */
    @GetMapping("/delivery-rate")
    public R<Map<String, Object>> getDeliveryRate(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateTo) {
        return R.ok(reportQueryService.getDeliveryRate(dateFrom, dateTo));
    }

    /**
     * R05: 延期预警
     */
    @GetMapping("/delay-warning")
    public R<List<Map<String, Object>>> getDelayWarning() {
        return R.ok(reportQueryService.getDelayWarning());
    }

    /**
     * R06: 合同进度
     */
    @GetMapping("/contract-progress/{contractNo}")
    public R<List<Map<String, Object>>> getContractProgress(@PathVariable String contractNo) {
        return R.ok(reportQueryService.getContractProgress(contractNo));
    }

    /**
     * R07: 产能利用率
     */
    @GetMapping("/capacity-utilization")
    public R<List<Map<String, Object>>> getCapacityUtilization(
            @RequestParam(required = false) List<Long> wcIds,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateTo) {
        return R.ok(reportQueryService.getCapacityUtilization(wcIds, dateFrom, dateTo));
    }

    /**
     * R10: 成材率分析
     */
    @GetMapping("/yield-analysis")
    public R<List<Map<String, Object>>> getYieldAnalysis(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateTo) {
        return R.ok(reportQueryService.getYieldAnalysis(dateFrom, dateTo));
    }

    /**
     * R11: 产量日报
     */
    @GetMapping("/daily-output")
    public R<List<Map<String, Object>>> getDailyOutput(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date date,
            @RequestParam(required = false) Long wcId) {
        return R.ok(reportQueryService.getDailyOutput(date, wcId));
    }
}
