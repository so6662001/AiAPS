package com.aiaps.web.controller.inventory;

import com.aiaps.common.result.R;
import com.aiaps.domain.inventory.InvStockBind;
import com.aiaps.service.inventory.StockBindService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/v1/stock-bind")
@RequiredArgsConstructor
public class StockBindController {

    private final StockBindService stockBindService;

    @PostMapping
    public R<InvStockBind> create(@RequestBody InvStockBind bind) {
        return R.ok(stockBindService.createBind(bind));
    }

    @PostMapping("/batch")
    public R<List<InvStockBind>> createBatch(
            @RequestParam Long stockId,
            @RequestParam String cardNo,
            @RequestParam int totalQty,
            @RequestParam int qtyPerBind,
            @RequestParam(required = false) BigDecimal totalWeight,
            @RequestParam(required = false) String contractNo,
            @RequestParam(required = false) BigDecimal productLength,
            @RequestParam(required = false) String lengthDisplay) {
        List<InvStockBind> binds = stockBindService.createBindBatch(
                stockId, cardNo, totalQty, qtyPerBind, totalWeight, contractNo, productLength, lengthDisplay);
        return R.ok(binds);
    }

    @GetMapping("/stock/{stockId}")
    public R<List<InvStockBind>> byStock(@PathVariable Long stockId) {
        return R.ok(stockBindService.getByStock(stockId));
    }

    @GetMapping("/card/{cardNo}")
    public R<List<InvStockBind>> byCard(@PathVariable String cardNo) {
        return R.ok(stockBindService.getByCardNo(cardNo));
    }

    @PutMapping("/{bindNo}/status")
    public R<Void> updateStatus(@PathVariable String bindNo, @RequestParam String status) {
        stockBindService.updateStatus(bindNo, status);
        return R.ok();
    }
}
