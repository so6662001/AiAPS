package com.aiaps.web.controller.inventory;

import com.aiaps.common.result.PageResult;
import com.aiaps.common.result.R;
import com.aiaps.domain.inventory.InvStock;
import com.aiaps.domain.inventory.InvStockBind;
import com.aiaps.mapper.inventory.InvStockBindMapper;
import com.aiaps.mapper.inventory.InvStockMapper;
import com.aiaps.service.inventory.StockService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final InvStockMapper stockMapper;
    private final InvStockBindMapper stockBindMapper;

    @GetMapping
    public R<PageResult<InvStock>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long prdtId,
            @RequestParam(required = false) String patName,
            @RequestParam(required = false) String paName,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String contractNo) {
        LambdaQueryWrapper<InvStock> wrapper = new LambdaQueryWrapper<>();
        if (prdtId != null) {
            wrapper.eq(InvStock::getPrdtId, prdtId);
        }
        if (patName != null && !patName.isEmpty()) {
            wrapper.eq(InvStock::getPatName, patName);
        }
        if (paName != null && !paName.isEmpty()) {
            wrapper.eq(InvStock::getPaName, paName);
        }
        if (warehouseCode != null && !warehouseCode.isEmpty()) {
            wrapper.eq(InvStock::getWarehouseCode, warehouseCode);
        }
        if (contractNo != null && !contractNo.isEmpty()) {
            wrapper.eq(InvStock::getContractNo, contractNo);
        }
        wrapper.orderByDesc(InvStock::getLastUpdated);
        Page<InvStock> page = stockMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    @GetMapping("/available")
    public R<List<InvStock>> available(
            @RequestParam(required = false) Long prdtId,
            @RequestParam(required = false) String patName,
            @RequestParam(required = false) String paName) {
        return R.ok(stockService.getAvailableStock(prdtId, patName, paName));
    }

    @GetMapping("/{id}/binds")
    public R<List<InvStockBind>> getBinds(@PathVariable Long id) {
        LambdaQueryWrapper<InvStockBind> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InvStockBind::getStockId, id);
        wrapper.orderByDesc(InvStockBind::getCreatedTime);
        return R.ok(stockBindMapper.selectList(wrapper));
    }
}
