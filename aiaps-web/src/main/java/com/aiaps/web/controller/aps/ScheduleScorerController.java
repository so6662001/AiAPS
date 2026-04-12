package com.aiaps.web.controller.aps;

import com.aiaps.common.result.R;
import com.aiaps.domain.base.BasScheduleStrategy;
import com.aiaps.mapper.base.BasScheduleStrategyMapper;
import com.aiaps.service.aps.ScheduleScorerService;
import com.aiaps.service.aps.ScheduleScorerService.ScoreResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/v1/schedule-scorer")
@RequiredArgsConstructor
public class ScheduleScorerController {

    private final ScheduleScorerService scorerService;
    private final BasScheduleStrategyMapper strategyMapper;

    @PostMapping("/score")
    public R<List<ScoreResult>> score(
            @RequestParam Long prdtId,
            @RequestParam BigDecimal plannedWeight,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date dueDate,
            @RequestParam(required = false) String strategyCode) {
        return R.ok(scorerService.scoreAllOptions(prdtId, plannedWeight, dueDate, strategyCode));
    }

    @PostMapping("/estimate")
    public R<List<ScoreResult>> estimate(
            @RequestParam Long prdtId,
            @RequestParam BigDecimal plannedWeight) {
        return R.ok(scorerService.estimateCompletion(prdtId, plannedWeight));
    }

    @GetMapping("/strategy")
    public R<List<BasScheduleStrategy>> listStrategies() {
        return R.ok(strategyMapper.selectList(
                new LambdaQueryWrapper<BasScheduleStrategy>()
                        .eq(BasScheduleStrategy::getIsActive, true)));
    }

    @GetMapping("/strategy/default")
    public R<BasScheduleStrategy> getDefaultStrategy() {
        return R.ok(strategyMapper.selectDefault());
    }

    @PostMapping("/strategy")
    public R<Void> createStrategy(@RequestBody BasScheduleStrategy strategy) {
        strategy.setIsActive(true);
        strategyMapper.insert(strategy);
        return R.ok();
    }

    @PutMapping("/strategy/{id}")
    public R<Void> updateStrategy(@PathVariable Long id, @RequestBody BasScheduleStrategy strategy) {
        strategy.setStrategyId(id);
        strategyMapper.updateById(strategy);
        return R.ok();
    }
}
