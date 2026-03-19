package com.aiaps.web.controller.base;

import com.aiaps.common.result.PageResult;
import com.aiaps.common.result.R;
import com.aiaps.domain.base.BasBomHead;
import com.aiaps.mapper.base.BasBomHeadMapper;
import com.aiaps.service.base.BomService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/bom")
@RequiredArgsConstructor
public class BomController {

    private final BomService bomService;
    private final BasBomHeadMapper bomHeadMapper;

    @GetMapping
    public R<PageResult<BasBomHead>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long materialId) {
        LambdaQueryWrapper<BasBomHead> wrapper = new LambdaQueryWrapper<>();
        if (materialId != null) {
            wrapper.eq(BasBomHead::getPrdtId, materialId);
        }
        wrapper.orderByDesc(BasBomHead::getCreatedTime);
        Page<BasBomHead> page = bomHeadMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<BasBomHead> getById(@PathVariable Long id) {
        return R.ok(bomService.getBomTree(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody BasBomHead bomHead) {
        bomHeadMapper.insert(bomHead);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BasBomHead bomHead) {
        bomHead.setBomId(id);
        bomHeadMapper.updateById(bomHead);
        return R.ok();
    }
}
