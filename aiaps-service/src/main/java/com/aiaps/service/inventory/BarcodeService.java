package com.aiaps.service.inventory;

import com.aiaps.domain.base.BasCategoryConfig;
import com.aiaps.domain.base.BasMaterial;
import com.aiaps.domain.inventory.InvItemBarcode;
import com.aiaps.mapper.base.BasCategoryConfigMapper;
import com.aiaps.mapper.base.BasMaterialMapper;
import com.aiaps.mapper.inventory.InvItemBarcodeMapper;
import com.aiaps.mapper.inventory.InvStockBindMapper;
import com.aiaps.service.util.SpecDisplayBuilder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BarcodeService {

    private final InvItemBarcodeMapper itemBarcodeMapper;
    private final InvStockBindMapper stockBindMapper;
    private final BasCategoryConfigMapper categoryConfigMapper;
    private final BasMaterialMapper materialMapper;
    private final SpecDisplayBuilder specDisplayBuilder;

    public boolean isBarcodeEnabled(String categoryCode) {
        BasCategoryConfig config = categoryConfigMapper.selectByCategory(categoryCode);
        return config != null && Boolean.TRUE.equals(config.getEnableItemBarcode());
    }

    @Transactional
    public List<InvItemBarcode> generateBarcodes(Long stockId, String cardNo, String bindNo, Long bindId,
                                                  int qty, Long prdtId, String patName, String paName,
                                                  BigDecimal itemLength, BigDecimal itemWeight,
                                                  Long scheduleId, String contractNo) {
        String prefix = "BC";
        BasMaterial material = materialMapper.selectById(prdtId);
        if (material != null) {
            BasCategoryConfig config = categoryConfigMapper.selectByCategory(material.getCategoryCode());
            if (config != null && config.getBarcodePrefix() != null && !config.getBarcodePrefix().isEmpty()) {
                prefix = config.getBarcodePrefix();
            }
        }

        String prdtNo = material != null ? material.getPrdtNo() : null;
        String prdtName = material != null ? material.getPrdtName() : null;

        String specDisplay = null;
        if (material != null) {
            String lengthDisplay = specDisplayBuilder.formatLength(itemLength);
            specDisplay = specDisplayBuilder.build(material.getSpecDesc(), lengthDisplay, patName, paName);
        }

        long timestamp = System.currentTimeMillis();
        List<InvItemBarcode> barcodes = new ArrayList<>();

        for (int i = 0; i < qty; i++) {
            InvItemBarcode barcode = new InvItemBarcode();
            barcode.setItemBarcode(prefix + "-" + timestamp + "-" + String.format("%03d", i + 1));
            barcode.setStockId(stockId);
            barcode.setCardNo(cardNo);
            barcode.setBindNo(bindNo);
            barcode.setBindId(bindId);
            barcode.setPrdtId(prdtId);
            barcode.setPrdtNo(prdtNo);
            barcode.setPrdtName(prdtName);
            barcode.setPatName(patName);
            barcode.setPaName(paName);
            barcode.setItemLength(itemLength);
            barcode.setItemWeight(itemWeight);
            barcode.setSpecDisplay(specDisplay);
            barcode.setQcStatus("PASSED");
            barcode.setItemStatus("IN_STOCK");
            barcode.setScheduleId(scheduleId);
            barcode.setContractNo(contractNo);
            barcode.setCreatedTime(new Date());
            itemBarcodeMapper.insert(barcode);
            barcodes.add(barcode);
        }
        return barcodes;
    }

    public InvItemBarcode getByBarcode(String itemBarcode) {
        return itemBarcodeMapper.selectByBarcode(itemBarcode);
    }

    public List<InvItemBarcode> getByBindNo(String bindNo) {
        LambdaQueryWrapper<InvItemBarcode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InvItemBarcode::getBindNo, bindNo);
        wrapper.orderByAsc(InvItemBarcode::getItemBarcode);
        return itemBarcodeMapper.selectList(wrapper);
    }

    public List<InvItemBarcode> getByCardNo(String cardNo) {
        LambdaQueryWrapper<InvItemBarcode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InvItemBarcode::getCardNo, cardNo);
        wrapper.orderByAsc(InvItemBarcode::getItemBarcode);
        return itemBarcodeMapper.selectList(wrapper);
    }

    @Transactional
    public void updateStatus(String itemBarcode, String status) {
        LambdaUpdateWrapper<InvItemBarcode> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(InvItemBarcode::getItemBarcode, itemBarcode);
        wrapper.set(InvItemBarcode::getItemStatus, status);
        itemBarcodeMapper.update(null, wrapper);
    }
}
