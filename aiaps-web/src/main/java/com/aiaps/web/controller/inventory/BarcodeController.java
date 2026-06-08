package com.aiaps.web.controller.inventory;

import com.aiaps.common.result.R;
import com.aiaps.domain.base.BasCategoryConfig;
import com.aiaps.domain.inventory.InvItemBarcode;
import com.aiaps.mapper.base.BasCategoryConfigMapper;
import com.aiaps.service.inventory.BarcodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/v1/barcode")
@RequiredArgsConstructor
public class BarcodeController {

    private final BarcodeService barcodeService;
    private final BasCategoryConfigMapper categoryConfigMapper;

    @PostMapping("/generate")
    public R<List<InvItemBarcode>> generate(@RequestBody GenerateBarcodeRequest req) {
        List<InvItemBarcode> barcodes = barcodeService.generateBarcodes(
                req.stockId, req.cardNo, req.bindNo, req.bindId,
                req.qty, req.prdtId, req.patName, req.paName,
                req.itemLength, req.itemWeight, req.scheduleId, req.contractNo);
        return R.ok(barcodes);
    }

    @GetMapping("/{itemBarcode}")
    public R<InvItemBarcode> lookup(@PathVariable String itemBarcode) {
        return R.ok(barcodeService.getByBarcode(itemBarcode));
    }

    @GetMapping("/bind/{bindNo}")
    public R<List<InvItemBarcode>> byBind(@PathVariable String bindNo) {
        return R.ok(barcodeService.getByBindNo(bindNo));
    }

    @GetMapping("/card/{cardNo}")
    public R<List<InvItemBarcode>> byCard(@PathVariable String cardNo) {
        return R.ok(barcodeService.getByCardNo(cardNo));
    }

    @PutMapping("/{itemBarcode}/status")
    public R<Void> updateStatus(@PathVariable String itemBarcode, @RequestParam String status) {
        barcodeService.updateStatus(itemBarcode, status);
        return R.ok();
    }

    @GetMapping("/category/{code}/config")
    public R<BasCategoryConfig> categoryConfig(@PathVariable String code) {
        return R.ok(categoryConfigMapper.selectByCategory(code));
    }

    static class GenerateBarcodeRequest {
        public Long stockId;
        public String cardNo;
        public String bindNo;
        public Long bindId;
        public int qty;
        public Long prdtId;
        public String patName;
        public String paName;
        public BigDecimal itemLength;
        public BigDecimal itemWeight;
        public Long scheduleId;
        public String contractNo;
    }
}
