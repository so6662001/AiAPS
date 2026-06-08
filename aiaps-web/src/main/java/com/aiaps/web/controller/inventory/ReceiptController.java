package com.aiaps.web.controller.inventory;

import com.aiaps.common.result.PageResult;
import com.aiaps.common.result.R;
import com.aiaps.domain.inventory.InvReceipt;
import com.aiaps.mapper.inventory.InvReceiptMapper;
import com.aiaps.service.inventory.ReceiptService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Validated
@RestController
@RequestMapping("/v1/receipt")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;
    private final InvReceiptMapper receiptMapper;

    @PostMapping
    public R<InvReceipt> create(@Valid @RequestBody InvReceipt receipt) {
        return R.ok(receiptService.createReceipt(receipt));
    }

    @GetMapping
    public R<PageResult<InvReceipt>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String receiptType,
            @RequestParam(required = false) String receiptStatus) {
        Page<InvReceipt> page = receiptService.page(pageNum, pageSize, receiptType, receiptStatus);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<InvReceipt> detail(@PathVariable Long id) {
        return R.ok(receiptMapper.selectById(id));
    }

    @PutMapping("/{id}/qc-pass")
    public R<Void> passQc(@PathVariable Long id, @RequestParam String qcBy) {
        receiptService.passQc(id, qcBy);
        return R.ok();
    }

    @PutMapping("/{id}/qc-fail")
    public R<Void> failQc(@PathVariable Long id, @RequestParam String qcBy, @RequestParam String reason) {
        receiptService.failQc(id, qcBy, reason);
        return R.ok();
    }

    @PutMapping("/{id}/qc-waive")
    public R<Void> waiveQc(@PathVariable Long id) {
        receiptService.waiveQc(id);
        return R.ok();
    }

    @PutMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id, @RequestParam String receivedBy) {
        receiptService.confirmReceipt(id, receivedBy);
        return R.ok();
    }

    @GetMapping("/schedule/{scheduleId}")
    public R<List<InvReceipt>> bySchedule(@PathVariable Long scheduleId) {
        return R.ok(receiptService.getBySchedule(scheduleId));
    }
}
