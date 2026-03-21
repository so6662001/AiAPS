package com.aiaps.web.controller.aps;

import com.aiaps.common.result.PageResult;
import com.aiaps.common.result.R;
import com.aiaps.domain.aps.ApsSchedule;
import com.aiaps.mapper.aps.ApsScheduleMapper;
import com.aiaps.mapper.aps.ApsScheduleOperMapper;
import com.aiaps.service.aps.ScheduleEngineService;
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
@RequestMapping("/v1/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleEngineService scheduleEngineService;
    private final ApsScheduleMapper scheduleMapper;
    private final ApsScheduleOperMapper scheduleOperMapper;

    @PostMapping("/auto")
    public R<List<ApsSchedule>> autoSchedule(
            @RequestParam List<Long> planOrderIds,
            @RequestParam(defaultValue = "FORWARD") String strategy) {
        return R.ok(scheduleEngineService.autoSchedule(planOrderIds, strategy));
    }

    @GetMapping
    public R<PageResult<ApsSchedule>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long wcId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String contractNo,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateTo) {
        LambdaQueryWrapper<ApsSchedule> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(ApsSchedule::getScheduleStatus, status);
        }
        if (contractNo != null && !contractNo.isEmpty()) {
            wrapper.eq(ApsSchedule::getContractNo, contractNo);
        }
        if (dateFrom != null) {
            wrapper.ge(ApsSchedule::getScheduleStart, dateFrom);
        }
        if (dateTo != null) {
            wrapper.le(ApsSchedule::getScheduleStart, dateTo);
        }
        wrapper.orderByDesc(ApsSchedule::getCreatedTime);
        Page<ApsSchedule> page = scheduleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<ApsSchedule> getById(@PathVariable Long id) {
        ApsSchedule schedule = scheduleMapper.selectById(id);
        if (schedule != null) {
            schedule.setOpers(scheduleOperMapper.selectByScheduleId(id));
        }
        return R.ok(schedule);
    }

    @PutMapping("/move")
    public R<Void> move(
            @RequestParam Long scheduleId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date newStart,
            @RequestParam(required = false) Long newWcId) {
        scheduleEngineService.moveSchedule(scheduleId, newStart, newWcId);
        return R.ok();
    }

    @PostMapping("/insert")
    public R<Void> insertOrder(@Valid @RequestBody ApsSchedule schedule) {
        scheduleEngineService.insertOrder(schedule);
        return R.ok();
    }

    @PutMapping("/lock")
    public R<Void> lock(
            @RequestParam List<Long> scheduleIds,
            @RequestParam boolean locked) {
        scheduleEngineService.lockSchedule(scheduleIds, locked);
        return R.ok();
    }

    @GetMapping("/gantt")
    public R<List<ApsSchedule>> gantt(
            @RequestParam List<Long> wcIds,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateTo) {
        return R.ok(scheduleEngineService.getGanttData(wcIds, dateFrom, dateTo));
    }

    @GetMapping("/contract/{contractNo}")
    public R<List<ApsSchedule>> getByContract(@PathVariable String contractNo) {
        return R.ok(scheduleMapper.selectByContract(contractNo));
    }
}
