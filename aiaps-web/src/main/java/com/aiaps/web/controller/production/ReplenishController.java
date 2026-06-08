package com.aiaps.web.controller.production;

import com.aiaps.common.result.PageResult;
import com.aiaps.common.result.R;
import com.aiaps.domain.inventory.InvStock;
import com.aiaps.domain.production.PrdReplenish;
import com.aiaps.mapper.production.PrdReplenishMapper;
import com.aiaps.service.production.ReplenishService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Validated
@RestController
@RequestMapping("/v1/replenish")
@RequiredArgsConstructor
public class ReplenishController {

    private final ReplenishService replenishService;
    private final PrdReplenishMapper replenishMapper;

    @PostMapping
    public R<Void> create(@Valid @RequestBody PrdReplenish replenish) {
        replenishService.createReplenish(replenish);
        return R.ok();
    }

    @GetMapping
    public R<PageResult<PrdReplenish>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long scheduleId,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<PrdReplenish> wrapper = new LambdaQueryWrapper<>();
        if (scheduleId != null) {
            wrapper.eq(PrdReplenish::getScheduleId, scheduleId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(PrdReplenish::getReplenishStatus, status);
        }
        wrapper.orderByDesc(PrdReplenish::getRequestedTime);
        Page<PrdReplenish> page = replenishMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    @PutMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id) {
        replenishService.approveReplenish(id);
        return R.ok();
    }

    @PutMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestParam String reason) {
        replenishService.rejectReplenish(id, reason);
        return R.ok();
    }

    @PutMapping("/{id}/execute")
    public R<Void> execute(@PathVariable Long id, @RequestParam String operatedBy) {
        replenishService.executeReplenish(id, operatedBy);
        return R.ok();
    }

    @PutMapping("/{id}/receive")
    public R<Void> receive(@PathVariable Long id) {
        replenishService.receiveReplenish(id);
        return R.ok();
    }

    @GetMapping("/recommend/{scheduleId}")
    public R<List<InvStock>> recommendSources(@PathVariable Long scheduleId) {
        return R.ok(replenishService.recommendSources(scheduleId));
    }

    @GetMapping("/schedule/{scheduleId}")
    public R<List<PrdReplenish>> getBySchedule(@PathVariable Long scheduleId) {
        return R.ok(replenishService.getBySchedule(scheduleId));
    }
}
