package com.aiaps.web.controller.production;

import com.aiaps.common.result.PageResult;
import com.aiaps.common.result.R;
import com.aiaps.domain.production.PrdMaterialSwap;
import com.aiaps.mapper.production.PrdMaterialSwapMapper;
import com.aiaps.service.production.MaterialSwapService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/material-swap")
@RequiredArgsConstructor
public class MaterialSwapController {

    private final MaterialSwapService materialSwapService;
    private final PrdMaterialSwapMapper swapMapper;

    @PostMapping
    public R<Void> recordSwap(@RequestBody PrdMaterialSwap swap) {
        materialSwapService.recordSwap(swap);
        return R.ok();
    }

    @GetMapping
    public R<PageResult<PrdMaterialSwap>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long scheduleId) {
        LambdaQueryWrapper<PrdMaterialSwap> wrapper = new LambdaQueryWrapper<>();
        if (scheduleId != null) {
            wrapper.eq(PrdMaterialSwap::getScheduleId, scheduleId);
        }
        wrapper.orderByDesc(PrdMaterialSwap::getSwapTime);
        Page<PrdMaterialSwap> page = swapMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    @GetMapping("/schedule/{scheduleId}")
    public R<List<PrdMaterialSwap>> getBySchedule(@PathVariable Long scheduleId) {
        return R.ok(materialSwapService.getBySchedule(scheduleId));
    }

    @GetMapping("/coil/{coilNo}")
    public R<List<PrdMaterialSwap>> getByCoilNo(@PathVariable String coilNo) {
        return R.ok(materialSwapService.getByCoilNo(coilNo));
    }
}
