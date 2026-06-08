package com.aiaps.web.controller.production;

import com.aiaps.common.result.PageResult;
import com.aiaps.common.result.R;
import com.aiaps.domain.production.PrdReport;
import com.aiaps.mapper.production.PrdReportMapper;
import com.aiaps.service.production.ReportService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Date;
import java.util.List;

@Validated
@RestController
@RequestMapping("/v1/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final PrdReportMapper reportMapper;

    @PostMapping
    public R<Void> submit(@Valid @RequestBody PrdReport report) {
        reportService.submitReport(report);
        return R.ok();
    }

    @GetMapping
    public R<PageResult<PrdReport>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long scheduleId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateTo) {
        LambdaQueryWrapper<PrdReport> wrapper = new LambdaQueryWrapper<>();
        if (scheduleId != null) {
            wrapper.eq(PrdReport::getScheduleId, scheduleId);
        }
        if (dateFrom != null) {
            wrapper.ge(PrdReport::getReportTime, dateFrom);
        }
        if (dateTo != null) {
            wrapper.le(PrdReport::getReportTime, dateTo);
        }
        wrapper.orderByDesc(PrdReport::getReportTime);
        Page<PrdReport> page = reportMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    @GetMapping("/schedule/{scheduleId}")
    public R<List<PrdReport>> getBySchedule(@PathVariable Long scheduleId) {
        return R.ok(reportService.getReportsBySchedule(scheduleId));
    }
}
