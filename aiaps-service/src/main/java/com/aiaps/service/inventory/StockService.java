package com.aiaps.service.inventory;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.inventory.InvStock;
import com.aiaps.domain.inventory.InvTransaction;
import com.aiaps.mapper.inventory.InvStockMapper;
import com.aiaps.mapper.inventory.InvTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockService {

    private final InvStockMapper stockMapper;
    private final InvTransactionMapper transactionMapper;

    public List<InvStock> getAvailableStock(Long prdtId, String patName, String paName) {
        return stockMapper.selectAvailable(prdtId, patName, paName);
    }

    public BigDecimal getAvailableWeight(Long prdtId, String patName, String paName) {
        BigDecimal weight = stockMapper.selectAvailableWeight(prdtId, patName, paName);
        return weight != null ? weight : BigDecimal.ZERO;
    }

    @Transactional
    public void stockIn(InvStock stock, String txnType,
                        Long sourceDocId, String sourceDocNo, String operatedBy) {
        BigDecimal beforeQty = BigDecimal.ZERO;
        BigDecimal beforeWeight = BigDecimal.ZERO;

        if (stock.getStockId() != null) {
            InvStock existing = stockMapper.selectById(stock.getStockId());
            if (existing != null) {
                beforeQty = existing.getOnHandQty() != null ? existing.getOnHandQty() : BigDecimal.ZERO;
                beforeWeight = existing.getOnHandWeight() != null ? existing.getOnHandWeight() : BigDecimal.ZERO;
                existing.setOnHandQty(beforeQty.add(
                        stock.getOnHandQty() != null ? stock.getOnHandQty() : BigDecimal.ZERO));
                existing.setOnHandWeight(beforeWeight.add(
                        stock.getOnHandWeight() != null ? stock.getOnHandWeight() : BigDecimal.ZERO));
                existing.setLastUpdated(new Date());
                stockMapper.updateById(existing);
                stock = existing;
            } else {
                stock.setLastUpdated(new Date());
                stockMapper.insert(stock);
            }
        } else {
            stock.setLastUpdated(new Date());
            stockMapper.insert(stock);
        }

        InvTransaction txn = new InvTransaction();
        txn.setTxnNo(generateTxnNo());
        txn.setTxnType(txnType);
        txn.setTxnDirection("IN");
        txn.setPrdtId(stock.getPrdtId());
        txn.setPatName(stock.getPatName());
        txn.setPaName(stock.getPaName());
        txn.setStockId(stock.getStockId());
        txn.setCoilNo(stock.getResNo());
        txn.setCardNo(stock.getCardNo());
        txn.setTxnQty(stock.getOnHandQty() != null ? stock.getOnHandQty().subtract(beforeQty) : BigDecimal.ZERO);
        txn.setTxnWeight(stock.getOnHandWeight() != null ? stock.getOnHandWeight().subtract(beforeWeight) : BigDecimal.ZERO);
        txn.setWarehouseCode(stock.getWarehouseCode());
        txn.setLocationCode(stock.getLocationCode());
        txn.setSourceDocId(sourceDocId);
        txn.setSourceDocNo(sourceDocNo);
        txn.setBeforeQty(beforeQty);
        txn.setAfterQty(stock.getOnHandQty());
        txn.setBeforeWeight(beforeWeight);
        txn.setAfterWeight(stock.getOnHandWeight());
        txn.setContractNo(stock.getContractNo());
        txn.setTxnTime(new Date());
        txn.setOperatedBy(operatedBy);
        transactionMapper.insert(txn);
    }

    @Transactional
    public void stockOut(Long stockId, BigDecimal qty, BigDecimal weight,
                         String txnType, Long sourceDocId, String sourceDocNo, String operatedBy) {
        InvStock stock = stockMapper.selectById(stockId);
        if (stock == null) {
            throw new BizException("库存记录不存在: " + stockId);
        }

        BigDecimal beforeQty = stock.getOnHandQty() != null ? stock.getOnHandQty() : BigDecimal.ZERO;
        BigDecimal beforeWeight = stock.getOnHandWeight() != null ? stock.getOnHandWeight() : BigDecimal.ZERO;

        BigDecimal outQty = qty != null ? qty : BigDecimal.ZERO;
        BigDecimal outWeight = weight != null ? weight : BigDecimal.ZERO;

        if (beforeQty.compareTo(outQty) < 0) {
            throw new BizException("库存数量不足, 当前: " + beforeQty + ", 需要: " + outQty);
        }

        if (beforeWeight.compareTo(outWeight) < 0) {
            throw new BizException("库存重量不足: 当前=" + beforeWeight + "T, 请求=" + outWeight + "T");
        }

        stock.setOnHandQty(beforeQty.subtract(outQty));
        stock.setOnHandWeight(beforeWeight.subtract(outWeight));
        stock.setLastUpdated(new Date());
        int updated = stockMapper.updateById(stock);
        if (updated == 0) {
            throw new BizException("库存并发冲突，请重试");
        }

        InvTransaction txn = new InvTransaction();
        txn.setTxnNo(generateTxnNo());
        txn.setTxnType(txnType);
        txn.setTxnDirection("OUT");
        txn.setPrdtId(stock.getPrdtId());
        txn.setPatName(stock.getPatName());
        txn.setPaName(stock.getPaName());
        txn.setStockId(stock.getStockId());
        txn.setCoilNo(stock.getResNo());
        txn.setCardNo(stock.getCardNo());
        txn.setTxnQty(outQty);
        txn.setTxnWeight(outWeight);
        txn.setWarehouseCode(stock.getWarehouseCode());
        txn.setLocationCode(stock.getLocationCode());
        txn.setSourceDocId(sourceDocId);
        txn.setSourceDocNo(sourceDocNo);
        txn.setBeforeQty(beforeQty);
        txn.setAfterQty(stock.getOnHandQty());
        txn.setBeforeWeight(beforeWeight);
        txn.setAfterWeight(stock.getOnHandWeight());
        txn.setContractNo(stock.getContractNo());
        txn.setTxnTime(new Date());
        txn.setOperatedBy(operatedBy);
        transactionMapper.insert(txn);
    }

    private String generateTxnNo() {
        return "TXN" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }
}
