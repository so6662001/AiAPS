package com.aiaps.service.inventory;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.aps.ApsSchedule;
import com.aiaps.domain.base.BasMaterial;
import com.aiaps.domain.inventory.InvReceipt;
import com.aiaps.domain.inventory.InvStock;
import com.aiaps.domain.inventory.InvStockBind;
import com.aiaps.domain.trace.TrcTraceLink;
import com.aiaps.mapper.aps.ApsScheduleMapper;
import com.aiaps.mapper.base.BasMaterialMapper;
import com.aiaps.mapper.inventory.InvReceiptMapper;
import com.aiaps.mapper.inventory.InvStockMapper;
import com.aiaps.mapper.trace.TrcTraceLinkMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final InvReceiptMapper receiptMapper;
    private final StockService stockService;
    private final ApsScheduleMapper scheduleMapper;
    private final InvStockMapper stockMapper;
    private final TrcTraceLinkMapper traceLinkMapper;
    private final BarcodeService barcodeService;
    private final StockBindService stockBindService;
    private final BasMaterialMapper materialMapper;

    @Transactional
    public InvReceipt createReceipt(InvReceipt receipt) {
        if (receipt.getPrdtId() == null) {
            throw new BizException("产品ID不能为空");
        }
        if (receipt.getPatName() == null || receipt.getPatName().isEmpty()) {
            throw new BizException("品种名称不能为空");
        }
        if (receipt.getReceiptQty() == null) {
            throw new BizException("收货数量不能为空");
        }
        if (receipt.getReceiptWeight() == null) {
            throw new BizException("收货重量不能为空");
        }
        if (receipt.getWarehouseCode() == null || receipt.getWarehouseCode().isEmpty()) {
            throw new BizException("仓库编码不能为空");
        }
        receipt.setReceiptStatus("PENDING");
        receipt.setCreatedTime(new Date());
        receiptMapper.insert(receipt);
        return receipt;
    }

    @Transactional
    public void passQc(Long receiptId, String qcBy) {
        InvReceipt receipt = receiptMapper.selectById(receiptId);
        if (receipt == null) {
            throw new BizException("收货单不存在: " + receiptId);
        }
        receipt.setQcStatus("PASSED");
        receipt.setQcBy(qcBy);
        receipt.setQcTime(new Date());
        receiptMapper.updateById(receipt);
    }

    @Transactional
    public void failQc(Long receiptId, String qcBy, String reason) {
        InvReceipt receipt = receiptMapper.selectById(receiptId);
        if (receipt == null) {
            throw new BizException("收货单不存在: " + receiptId);
        }
        receipt.setQcStatus("FAILED");
        receipt.setQcBy(qcBy);
        receipt.setQcTime(new Date());
        receipt.setRemark(reason);
        receiptMapper.updateById(receipt);
    }

    @Transactional
    public void waiveQc(Long receiptId) {
        InvReceipt receipt = receiptMapper.selectById(receiptId);
        if (receipt == null) {
            throw new BizException("收货单不存在: " + receiptId);
        }
        receipt.setQcStatus("WAIVED");
        receiptMapper.updateById(receipt);
    }

    @Transactional
    public void confirmReceipt(Long receiptId, String receivedBy) {
        InvReceipt receipt = receiptMapper.selectById(receiptId);
        if (receipt == null) {
            throw new BizException("收货单不存在: " + receiptId);
        }
        if (!"PASSED".equals(receipt.getQcStatus()) && !"WAIVED".equals(receipt.getQcStatus())) {
            throw new BizException("质检状态不允许确认收货: " + receipt.getQcStatus());
        }

        InvStock stock = new InvStock();
        stock.setPrdtId(receipt.getPrdtId());
        stock.setPatName(receipt.getPatName());
        stock.setPaName(receipt.getPaName());
        stock.setWarehouseCode(receipt.getWarehouseCode());
        stock.setOnHandQty(receipt.getReceiptQty());
        stock.setOnHandWeight(receipt.getReceiptWeight());
        stock.setResNo(receipt.getCoilNo());
        stock.setContractNo(receipt.getContractNo());
        stock.setStockLength(receipt.getProductLength());
        stock.setLengthDisplay(receipt.getLengthDisplay());
        stock.setEnterDate(new Date());

        stockService.stockIn(stock, "RECEIPT", receipt.getReceiptId(), receipt.getReceiptNo(), receivedBy);

        Long targetStockId = stock.getStockId();
        String cardNo = stock.getCardNo();

        if (receipt.getReceiptQty() != null && receipt.getReceiptQty().compareTo(BigDecimal.ZERO) > 0) {
            int qtyPerBind = 6;
            int totalQty = receipt.getReceiptQty().intValue();

            List<InvStockBind> binds = stockBindService.createBindBatch(
                    targetStockId, cardNo, totalQty, qtyPerBind,
                    receipt.getReceiptWeight(), receipt.getContractNo(),
                    receipt.getProductLength(), receipt.getLengthDisplay());

            BasMaterial material = materialMapper.selectById(receipt.getPrdtId());
            if (material != null && barcodeService.isBarcodeEnabled(material.getCategoryCode())) {
                for (InvStockBind bind : binds) {
                    BigDecimal itemWeight = bind.getBindWeight() != null && bind.getBindQty().compareTo(BigDecimal.ZERO) > 0
                            ? bind.getBindWeight().divide(bind.getBindQty(), 6, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                    barcodeService.generateBarcodes(
                            targetStockId, cardNo, bind.getBindNo(), bind.getBindId(),
                            bind.getBindQty().intValue(), receipt.getPrdtId(),
                            receipt.getPatName(), receipt.getPaName(),
                            receipt.getProductLength(), itemWeight,
                            receipt.getScheduleId(), receipt.getContractNo());
                }
            }
        }

        receipt.setReceiptStatus("RECEIVED");
        receipt.setReceivedBy(receivedBy);
        receipt.setReceivedTime(new Date());
        receipt.setTargetStockId(stock.getStockId());
        receiptMapper.updateById(receipt);

        if (receipt.getScheduleId() != null) {
            TrcTraceLink link = new TrcTraceLink();
            link.setSourceType("SCHEDULE");
            link.setScheduleId(receipt.getScheduleId());
            link.setTargetType("STOCK");
            link.setTargetStockId(stock.getStockId());
            link.setProcessType("RECEIPT");
            link.setTargetPrdtId(receipt.getPrdtId());
            link.setTargetPatName(receipt.getPatName());
            link.setTargetPaName(receipt.getPaName());
            link.setTargetWeight(receipt.getReceiptWeight());
            link.setTargetResNo(receipt.getCoilNo());
            link.setContractNo(receipt.getContractNo());
            link.setTraceTime(new Date());
            link.setOperatedBy(receivedBy);
            traceLinkMapper.insert(link);

            ApsSchedule schedule = scheduleMapper.selectById(receipt.getScheduleId());
            if (schedule != null) {
                BigDecimal currentGoodWeight = schedule.getGoodWeight() != null
                        ? schedule.getGoodWeight() : BigDecimal.ZERO;
                schedule.setGoodWeight(currentGoodWeight.add(
                        receipt.getReceiptWeight() != null ? receipt.getReceiptWeight() : BigDecimal.ZERO));
                scheduleMapper.updateById(schedule);
            }
        }
    }

    public List<InvReceipt> getBySchedule(Long scheduleId) {
        LambdaQueryWrapper<InvReceipt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InvReceipt::getScheduleId, scheduleId);
        wrapper.orderByDesc(InvReceipt::getCreatedTime);
        return receiptMapper.selectList(wrapper);
    }

    public Page<InvReceipt> page(int pageNum, int pageSize, String receiptType, String receiptStatus) {
        LambdaQueryWrapper<InvReceipt> wrapper = new LambdaQueryWrapper<>();
        if (receiptType != null && !receiptType.isEmpty()) {
            wrapper.eq(InvReceipt::getReceiptType, receiptType);
        }
        if (receiptStatus != null && !receiptStatus.isEmpty()) {
            wrapper.eq(InvReceipt::getReceiptStatus, receiptStatus);
        }
        wrapper.orderByDesc(InvReceipt::getCreatedTime);
        return receiptMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }
}
