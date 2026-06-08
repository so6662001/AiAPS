package com.aiaps.web.controller.production;

import com.aiaps.common.result.PageResult;
import com.aiaps.common.result.R;
import com.aiaps.domain.production.PrdReprocess;
import com.aiaps.mapper.production.PrdReprocessMapper;
import com.aiaps.service.production.ReprocessService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

@Validated
@RestController
@RequestMapping("/v1/reprocess")
@RequiredArgsConstructor
public class ReprocessController {

    private final ReprocessService reprocessService;
    private final PrdReprocessMapper reprocessMapper;

    @PostMapping
    public R<Void> create(@Valid @RequestBody PrdReprocess reprocess) {
        reprocessService.createReprocess(reprocess);
        return R.ok();
    }

    @GetMapping
    public R<PageResult<PrdReprocess>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String reprocessType,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<PrdReprocess> wrapper = new LambdaQueryWrapper<>();
        if (reprocessType != null && !reprocessType.isEmpty()) {
            wrapper.eq(PrdReprocess::getReprocessType, reprocessType);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(PrdReprocess::getReprocessStatus, status);
        }
        wrapper.orderByDesc(PrdReprocess::getCreatedTime);
        Page<PrdReprocess> page = reprocessMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    @PostMapping("/{id}/schedule")
    public R<Void> scheduleReprocess(@PathVariable Long id) {
        reprocessService.scheduleReprocess(id);
        return R.ok();
    }

    @PutMapping("/{id}/complete")
    public R<Void> complete(
            @PathVariable Long id,
            @RequestParam BigDecimal actualQty,
            @RequestParam BigDecimal scrapQty) {
        reprocessService.completeReprocess(id, actualQty, scrapQty);
        return R.ok();
    }

    @GetMapping("/source/{scheduleId}")
    public R<List<PrdReprocess>> getBySourceSchedule(@PathVariable Long scheduleId) {
        return R.ok(reprocessService.getBySourceSchedule(scheduleId));
    }

    @GetMapping("/chain/{reprocessId}")
    public R<List<PrdReprocess>> getReprocessChain(@PathVariable Long reprocessId) {
        return R.ok(reprocessService.getReprocessChain(reprocessId));
    }
}
