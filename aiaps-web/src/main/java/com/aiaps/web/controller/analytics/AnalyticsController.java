package com.aiaps.web.controller.analytics;

import com.aiaps.common.result.R;
import com.aiaps.domain.analytics.AnalyticsActionTrack;
import com.aiaps.domain.analytics.AnalyticsAlertRule;
import com.aiaps.domain.analytics.AnalyticsPageView;
import com.aiaps.domain.analytics.AnalyticsPerformance;
import com.aiaps.mapper.analytics.AnalyticsAlertRuleMapper;
import com.aiaps.service.analytics.AnalyticsCollectService;
import com.aiaps.service.analytics.AnalyticsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController("pageAnalyticsController")
@RequestMapping("/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsCollectService collectService;
    private final AnalyticsQueryService queryService;
    private final AnalyticsAlertRuleMapper alertRuleMapper;

    @PostMapping("/page-view")
    public R<Void> recordPageView(@RequestBody AnalyticsPageView view) {
        collectService.recordPageView(view);
        return R.ok();
    }

    @PostMapping("/action")
    public R<Void> recordAction(@RequestBody AnalyticsActionTrack track) {
        collectService.recordAction(track);
        return R.ok();
    }

    @PostMapping("/performance")
    public R<Void> recordPerformance(@RequestBody AnalyticsPerformance perf) {
        collectService.recordPerformance(perf);
        return R.ok();
    }

    @PostMapping("/batch")
    public R<Void> recordBatch(@RequestBody List<Map<String, Object>> events) {
        collectService.recordBatch(events);
        return R.ok();
    }

    @GetMapping("/page-heat")
    public R<List<Map<String, Object>>> getPageHeat(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateTo) {
        return R.ok(queryService.getPageHeat(dateFrom, dateTo));
    }

    @GetMapping("/feature-value")
    public R<List<Map<String, Object>>> getFeatureValue(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateTo) {
        return R.ok(queryService.getFeatureValue(dateFrom, dateTo));
    }

    @GetMapping("/user-activity")
    public R<Map<String, Object>> getUserActivity(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateTo) {
        return R.ok(queryService.getUserActivity(dateFrom, dateTo));
    }

    @GetMapping("/efficiency")
    public R<List<Map<String, Object>>> getEfficiency(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateTo) {
        return R.ok(queryService.getEfficiency(dateFrom, dateTo));
    }

    @GetMapping("/performance-overview")
    public R<Map<String, Object>> getPerformanceOverview() {
        return R.ok(queryService.getPerformanceOverview());
    }

    @GetMapping("/recent-alerts")
    public R<List<AnalyticsPerformance>> getRecentAlerts(
            @RequestParam(required = false, defaultValue = "10") int limit) {
        return R.ok(queryService.getRecentAlerts(limit));
    }

    @GetMapping("/alert-rules")
    public R<List<AnalyticsAlertRule>> getAlertRules() {
        return R.ok(alertRuleMapper.selectList(null));
    }

    @PostMapping("/alert-rules")
    public R<Void> createAlertRule(@RequestBody AnalyticsAlertRule rule) {
        alertRuleMapper.insert(rule);
        return R.ok();
    }

    @PutMapping("/alert-rules/{id}")
    public R<Void> updateAlertRule(@PathVariable Long id, @RequestBody AnalyticsAlertRule rule) {
        rule.setRuleId(id);
        alertRuleMapper.updateById(rule);
        return R.ok();
    }
}
