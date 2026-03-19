package com.aiaps.web.controller.inventory;

import com.aiaps.common.result.PageResult;
import com.aiaps.common.result.R;
import com.aiaps.domain.production.PrdMaterialIssue;
import com.aiaps.mapper.production.PrdMaterialIssueMapper;
import com.aiaps.service.inventory.MaterialIssueService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/material-issue")
@RequiredArgsConstructor
public class MaterialIssueController {

    private final MaterialIssueService materialIssueService;
    private final PrdMaterialIssueMapper materialIssueMapper;

    @PostMapping
    public R<Void> create(@RequestBody PrdMaterialIssue issue) {
        materialIssueService.createIssue(issue);
        return R.ok();
    }

    @GetMapping
    public R<PageResult<PrdMaterialIssue>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long scheduleId) {
        LambdaQueryWrapper<PrdMaterialIssue> wrapper = new LambdaQueryWrapper<>();
        if (scheduleId != null) {
            wrapper.eq(PrdMaterialIssue::getScheduleId, scheduleId);
        }
        wrapper.orderByDesc(PrdMaterialIssue::getRequestedTime);
        Page<PrdMaterialIssue> page = materialIssueMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    @PutMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id) {
        materialIssueService.approveIssue(id);
        return R.ok();
    }

    @PutMapping("/{id}/execute")
    public R<Void> execute(@PathVariable Long id, @RequestParam String operatedBy) {
        materialIssueService.executeIssue(id, operatedBy);
        return R.ok();
    }
}
