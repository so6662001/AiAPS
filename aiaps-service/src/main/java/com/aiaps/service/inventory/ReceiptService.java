package com.aiaps.service.inventory;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.aps.ApsSchedule;
import com.aiaps.domain.inventory.InvReceipt;
import com.aiaps.domain.inventory.InvStock;
import com.aiaps.domain.trace.TrcTraceLink;
import com.aiaps.mapper.aps.ApsScheduleMapper;
import com.aiaps.mapper.inventory.InvReceiptMapper;
import com.aiaps.mapper.inventory.InvStockMapper;
import com.aiaps.mapper.trace.TrcTraceLinkMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
