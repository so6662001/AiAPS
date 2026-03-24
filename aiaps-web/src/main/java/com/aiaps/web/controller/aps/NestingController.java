package com.aiaps.web.controller.aps;

import com.aiaps.common.result.R;
import com.aiaps.domain.aps.ApsNestingPlan;
import com.aiaps.mapper.aps.ApsNestingPlanMapper;
import com.aiaps.service.aps.NestingAutoService;
import com.aiaps.service.aps.NestingService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

@Validated
@RestController
@RequestMapping("/v1/nesting")
@RequiredArgsConstructor
public class NestingController {

    private final NestingService nestingService;
    private final NestingAutoService nestingAutoService;
    private final ApsNestingPlanMapper nestingPlanMapper;

    @PostMapping
    public R<Void> create(@Valid @RequestBody ApsNestingPlan plan) {
        nestingService.createNestingPlan(plan, null);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<ApsNestingPlan> getById(@PathVariable Long id) {
        ApsNestingPlan plan = nestingPlanMapper.selectById(id);
        return R.ok(plan);
    }

    @PostMapping("/optimize")
    public R<ApsNestingPlan> optimize(
            @RequestParam Long sourceStockId,
            @RequestParam List<BigDecimal> requiredWidths) {
        return R.ok(nestingService.optimizeSlit(sourceStockId, requiredWidths));
    }

    @PostMapping("/multi-optimize")
    public R<NestingAutoService.NestingOptimizeResult> multiOptimize(@RequestParam String groupKey) {
        return R.ok(nestingAutoService.autoOptimize(groupKey));
    }

    @PostMapping("/multi-optimize/preview")
    public R<NestingAutoService.NestingOptimizeResult> multiOptimizePreview(@RequestParam String groupKey) {
        return R.ok(nestingAutoService.autoOptimize(groupKey));
    }

    @PostMapping("/{id}/confirm-schedule")
    public R<Void> confirmAndSchedule(@PathVariable Long id) {
        nestingAutoService.confirmAndSchedule(id);
        return R.ok();
    }

    @GetMapping("/{id}/cost-split")
    public R<List<NestingAutoService.CostSplitItem>> getCostSplit(@PathVariable Long id) {
        return R.ok(nestingAutoService.getCostSplit(id));
    }
}
