package com.aiaps.web.controller.base;

import com.aiaps.common.result.R;
import com.aiaps.common.result.PageResult;
import com.aiaps.domain.base.BasMaterial;
import com.aiaps.service.base.MaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Validated
@RestController
@RequestMapping("/v1/material")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @GetMapping
    public R<PageResult<BasMaterial>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String keyword) {
        return R.ok(materialService.page(pageNum, pageSize, categoryCode, keyword));
    }

    @GetMapping("/{id}")
    public R<BasMaterial> getById(@PathVariable Long id) {
        return R.ok(materialService.getById(id));
    }

    @PostMapping
    public R<Void> create(@Valid @RequestBody BasMaterial material) {
        materialService.create(material);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody BasMaterial material) {
        material.setPrdtId(id);
        materialService.update(material);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return R.ok();
    }
}
