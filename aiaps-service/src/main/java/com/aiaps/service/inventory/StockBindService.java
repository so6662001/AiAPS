package com.aiaps.service.inventory;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.inventory.InvStockBind;
import com.aiaps.mapper.inventory.InvStockBindMapper;
import com.aiaps.mapper.inventory.InvStockMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class StockBindService {

    private final InvStockBindMapper stockBindMapper;
    private final InvStockMapper stockMapper;

    private static final AtomicInteger SEQ = new AtomicInteger(0);

    @Transactional
    public InvStockBind createBind(InvStockBind bind) {
        if (bind.getStockId() == null) {
            throw new BizException("库存ID不能为空");
        }
        if (bind.getCardNo() == null || bind.getCardNo().isEmpty()) {
            throw new BizException("卡号不能为空");
        }
        if (bind.getBindQty() == null || bind.getBindQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("绑定数量必须大于0");
        }
        bind.setBindNo("BN-" + System.currentTimeMillis() + "-" + SEQ.incrementAndGet());
        bind.setBindStatus("IN_STOCK");
        bind.setCreatedTime(new Date());
        stockBindMapper.insert(bind);
        return bind;
    }

    @Transactional
    public List<InvStockBind> createBindBatch(Long stockId, String cardNo, int totalQty, int qtyPerBind,
                                               BigDecimal totalWeight, String contractNo,
                                               BigDecimal productLength, String lengthDisplay) {
        if (totalQty <= 0) {
            throw new BizException("总数量必须大于0");
        }
        if (qtyPerBind <= 0) {
            throw new BizException("每捆数量必须大于0");
        }

        int bindCount = (int) Math.ceil((double) totalQty / qtyPerBind);
        BigDecimal totalQtyBd = BigDecimal.valueOf(totalQty);
        List<InvStockBind> binds = new ArrayList<>();

        int remaining = totalQty;
        for (int i = 0; i < bindCount; i++) {
            int currentQty = Math.min(qtyPerBind, remaining);
            remaining -= currentQty;

            InvStockBind bind = new InvStockBind();
            bind.setStockId(stockId);
            bind.setCardNo(cardNo);
            bind.setBindQty(BigDecimal.valueOf(currentQty));
            bind.setContractNo(contractNo);
            bind.setProductLength(productLength);
            bind.setLengthDisplay(lengthDisplay);

            if (totalWeight != null && totalQtyBd.compareTo(BigDecimal.ZERO) > 0) {
                bind.setBindWeight(totalWeight.multiply(BigDecimal.valueOf(currentQty))
                        .divide(totalQtyBd, 6, RoundingMode.HALF_UP));
            }

            bind.setBindNo("BN-" + System.currentTimeMillis() + "-" + SEQ.incrementAndGet());
            bind.setBindStatus("IN_STOCK");
            bind.setCreatedTime(new Date());
            stockBindMapper.insert(bind);
            binds.add(bind);
        }
        return binds;
    }

    public List<InvStockBind> getByStock(Long stockId) {
        LambdaQueryWrapper<InvStockBind> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InvStockBind::getStockId, stockId);
        wrapper.orderByDesc(InvStockBind::getCreatedTime);
        return stockBindMapper.selectList(wrapper);
    }

    public List<InvStockBind> getByCardNo(String cardNo) {
        LambdaQueryWrapper<InvStockBind> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InvStockBind::getCardNo, cardNo);
        wrapper.orderByDesc(InvStockBind::getCreatedTime);
        return stockBindMapper.selectList(wrapper);
    }

    @Transactional
    public void updateStatus(String bindNo, String status) {
        LambdaUpdateWrapper<InvStockBind> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(InvStockBind::getBindNo, bindNo);
        wrapper.set(InvStockBind::getBindStatus, status);
        stockBindMapper.update(null, wrapper);
    }
}
