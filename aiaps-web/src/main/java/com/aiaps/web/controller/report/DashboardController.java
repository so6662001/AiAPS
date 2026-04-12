package com.aiaps.web.controller.report;

import com.aiaps.common.result.R;
import com.aiaps.service.report.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * R18: 生产指挥中心
     */
    @GetMapping("/command-center")
    public R<Map<String, Object>> getCommandCenter() {
        return R.ok(dashboardService.getCommandCenter());
    }

    /**
     * R20: 预警列表
     */
    @GetMapping("/alerts")
    public R<List<Map<String, Object>>> getAlerts() {
        return R.ok(dashboardService.getAlerts());
    }

    /**
     * R21: MRP运行监控
     */
    @GetMapping("/mrp-monitor")
    public R<List<Map<String, Object>>> getMrpMonitor() {
        return R.ok(dashboardService.getMrpMonitor());
    }
}
