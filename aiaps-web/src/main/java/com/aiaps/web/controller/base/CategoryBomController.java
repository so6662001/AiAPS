package com.aiaps.web.controller.base;

import com.aiaps.common.result.R;
import com.aiaps.domain.base.BasCategoryBom;
import com.aiaps.domain.base.BasSpecFormula;
import com.aiaps.mapper.base.BasCategoryBomMapper;
import com.aiaps.mapper.base.BasSpecFormulaMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/v1/category-bom")
@RequiredArgsConstructor
public class CategoryBomController {

    private final BasCategoryBomMapper categoryBomMapper;
    private final BasSpecFormulaMapper specFormulaMapper;

    @GetMapping
    public R<List<BasCategoryBom>> list() {
        return R.ok(categoryBomMapper.selectAllActive());
    }

    @GetMapping("/parent/{category}")
    public R<List<BasCategoryBom>> byParent(@PathVariable String category) {
        return R.ok(categoryBomMapper.selectByParentCategory(category));
    }

    @GetMapping("/child/{category}")
    public R<List<BasCategoryBom>> byChild(@PathVariable String category) {
        return R.ok(categoryBomMapper.selectByChildCategory(category));
    }

    @PostMapping
    public R<Void> create(@RequestBody BasCategoryBom bom) {
        bom.setIsActive(true);
        categoryBomMapper.insert(bom);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BasCategoryBom bom) {
        bom.setCatBomId(id);
        categoryBomMapper.updateById(bom);
        return R.ok();
    }

    @GetMapping("/formula")
    public R<List<BasSpecFormula>> listFormulas() {
        return R.ok(specFormulaMapper.selectList(
            new LambdaQueryWrapper<BasSpecFormula>().eq(BasSpecFormula::getIsActive, true)));
    }

    @GetMapping("/formula/{code}")
    public R<BasSpecFormula> getFormula(@PathVariable String code) {
        return R.ok(specFormulaMapper.selectByCode(code));
    }

    @PostMapping("/formula")
    public R<Void> createFormula(@RequestBody BasSpecFormula formula) {
        formula.setIsActive(true);
        specFormulaMapper.insert(formula);
        return R.ok();
    }

    @PutMapping("/formula/{id}")
    public R<Void> updateFormula(@PathVariable Long id, @RequestBody BasSpecFormula formula) {
        formula.setFormulaId(id);
        specFormulaMapper.updateById(formula);
        return R.ok();
    }
}
