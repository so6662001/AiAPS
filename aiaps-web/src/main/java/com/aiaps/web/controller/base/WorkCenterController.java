package com.aiaps.web.controller.base;

import com.aiaps.common.result.PageResult;
import com.aiaps.common.result.R;
import com.aiaps.domain.base.BasWorkCenter;
import com.aiaps.service.base.WorkCenterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/work-center")
@RequiredArgsConstructor
public class WorkCenterController {

    private final WorkCenterService workCenterService;

    @GetMapping
    public R<PageResult<BasWorkCenter>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String wcType) {
        return R.ok(workCenterService.page(pageNum, pageSize, wcType));
    }

    @GetMapping("/{id}")
    public R<BasWorkCenter> getById(@PathVariable Long id) {
        return R.ok(workCenterService.getById(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody BasWorkCenter workCenter) {
        workCenterService.create(workCenter);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BasWorkCenter workCenter) {
        workCenter.setWcId(id);
        workCenterService.update(workCenter);
        return R.ok();
    }
}
