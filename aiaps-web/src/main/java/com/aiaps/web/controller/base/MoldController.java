package com.aiaps.web.controller.base;

import com.aiaps.common.result.PageResult;
import com.aiaps.common.result.R;
import com.aiaps.domain.base.BasMold;
import com.aiaps.domain.base.BasMoldProduct;
import com.aiaps.service.base.MoldService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

@Validated
@RestController
@RequestMapping("/v1/mold")
@RequiredArgsConstructor
public class MoldController {

    private final MoldService moldService;

    @GetMapping
    public R<PageResult<BasMold>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String moldStatus) {
        return R.ok(moldService.page(pageNum, pageSize, moldStatus));
    }

    @GetMapping("/{id}")
    public R<BasMold> getById(@PathVariable Long id) {
        return R.ok(moldService.getById(id));
    }

    @PostMapping
    public R<Void> create(@Valid @RequestBody BasMold mold) {
        moldService.create(mold);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody BasMold mold) {
        mold.setMoldId(id);
        moldService.update(mold);
        return R.ok();
    }

    @GetMapping("/{id}/products")
    public R<List<BasMoldProduct>> getMoldProducts(@PathVariable Long id) {
        return R.ok(moldService.getMoldProducts(id));
    }

    @GetMapping("/for-material/{materialId}")
    public R<List<BasMoldProduct>> getCompatibleMolds(@PathVariable Long materialId) {
        return R.ok(moldService.findCompatibleMolds(materialId));
    }

    @GetMapping("/change-time")
    public R<Integer> getChangeTime(
            @RequestParam Long wcId,
            @RequestParam Long fromMoldId,
            @RequestParam Long toMoldId) {
        return R.ok(moldService.getChangeTime(wcId, fromMoldId, toMoldId));
    }

    @PostMapping("/{id}/usage")
    public R<Void> recordUsage(
            @PathVariable Long id,
            @RequestParam Long wcId,
            @RequestParam Long scheduleId,
            @RequestParam BigDecimal usageQty) {
        moldService.recordUsage(id, wcId, scheduleId, usageQty);
        return R.ok();
    }

    @GetMapping("/{id}/capacity-check")
    public R<MoldService.CapacityCheckResult> checkCapacity(
            @PathVariable Long id,
            @RequestParam BigDecimal plannedWeight) {
        return R.ok(moldService.checkMoldCapacity(id, plannedWeight));
    }
}
