package com.aiaps.service.aps;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.aps.ApsFurnaceCharge;
import com.aiaps.domain.aps.ApsFurnaceChargeLayer;
import com.aiaps.domain.base.BasAnnealRecipe;
import com.aiaps.domain.base.BasFurnace;
import com.aiaps.domain.inventory.InvStock;
import com.aiaps.domain.trace.TrcTraceLink;
import com.aiaps.mapper.aps.ApsFurnaceChargeLayerMapper;
import com.aiaps.mapper.aps.ApsFurnaceChargeMapper;
import com.aiaps.mapper.base.BasAnnealRecipeMapper;
import com.aiaps.mapper.base.BasFurnaceMapper;
import com.aiaps.mapper.inventory.InvStockMapper;
import com.aiaps.mapper.trace.TrcTraceLinkMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FurnaceService {

    private final BasFurnaceMapper furnaceMapper;
    private final BasAnnealRecipeMapper annealRecipeMapper;
    private final ApsFurnaceChargeMapper chargeMapper;
    private final ApsFurnaceChargeLayerMapper chargeLayerMapper;
    private final InvStockMapper stockMapper;
    private final TrcTraceLinkMapper traceLinkMapper;

    public List<BasFurnace> listFurnaces() {
        LambdaQueryWrapper<BasFurnace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BasFurnace::getIsActive, true);
        return furnaceMapper.selectList(wrapper);
    }

    public BasFurnace getFurnaceById(Long id) {
        BasFurnace furnace = furnaceMapper.selectById(id);
        if (furnace == null) {
            throw new BizException("炉子不存在: " + id);
        }
        return furnace;
    }

    @Transactional
    public void updateFurnaceStatus(Long furnaceId, String status) {
        BasFurnace furnace = getFurnaceById(furnaceId);
        furnace.setFurnaceStatus(status);
        if ("ANNEALING".equals(status)) {
            furnace.setCurrentStartTime(new Date());
        } else if ("IDLE".equals(status)) {
            furnace.setCurrentStartTime(null);
            furnace.setExpectedEndTime(null);
            furnace.setCurrentScheduleId(null);
        }
        furnaceMapper.updateById(furnace);
    }

    public List<BasAnnealRecipe> getCompatibleRecipes(String gradeCode) {
        return annealRecipeMapper.selectCompatible(gradeCode);
    }

    @Transactional
    public void createCharge(ApsFurnaceCharge charge) {
        if (charge.getFurnaceId() == null) {
            throw new BizException("炉子ID不能为空");
        }
        BasFurnace furnace = furnaceMapper.selectById(charge.getFurnaceId());
        if (furnace == null) {
            throw new BizException("炉子不存在: " + charge.getFurnaceId());
        }
        if (charge.getRecipeId() == null) {
            throw new BizException("退火配方ID不能为空");
        }
        BasAnnealRecipe recipe = annealRecipeMapper.selectById(charge.getRecipeId());
        if (recipe == null) {
            throw new BizException("退火配方不存在: " + charge.getRecipeId());
        }
        charge.setChargeStatus("PLANNED");
        charge.setCreatedTime(new Date());
        chargeMapper.insert(charge);
    }

    @Transactional
    public void loadLayer(Long chargeId, int layerNo, Long stockId) {
        ApsFurnaceCharge charge = chargeMapper.selectById(chargeId);
        if (charge == null) {
            throw new BizException("装炉计划不存在: " + chargeId);
        }

        BasFurnace furnace = furnaceMapper.selectById(charge.getFurnaceId());
        if (furnace == null) {
            throw new BizException("炉子不存在: " + charge.getFurnaceId());
        }

        if (layerNo > furnace.getTotalLayers()) {
            throw new BizException("层号超出炉子最大层数: " + furnace.getTotalLayers());
        }

        LambdaQueryWrapper<ApsFurnaceChargeLayer> layerWrapper = new LambdaQueryWrapper<>();
        layerWrapper.eq(ApsFurnaceChargeLayer::getChargeId, chargeId)
                .eq(ApsFurnaceChargeLayer::getLayerNo, layerNo);
        Long existingCount = chargeLayerMapper.selectCount(layerWrapper);
        if (existingCount > 0) {
            throw new BizException("层号已被占用: " + layerNo);
        }

        InvStock stock = stockMapper.selectById(stockId);
        if (stock == null) {
            throw new BizException("库存记录不存在: " + stockId);
        }

        BigDecimal coilWeight = stock.getOnHandWeight();
        if (coilWeight != null && furnace.getMaxWeightPerLayer() != null
                && coilWeight.compareTo(furnace.getMaxWeightPerLayer()) > 0) {
            throw new BizException("卷重超出单层最大承重: " + furnace.getMaxWeightPerLayer());
        }

        BasAnnealRecipe recipe = annealRecipeMapper.selectById(charge.getRecipeId());
        if (recipe != null && recipe.getApplicableGrade() != null) {
            if (!recipe.getApplicableGrade().equals(stock.getPatName())) {
                throw new BizException("钢种不匹配退火配方要求: " + recipe.getApplicableGrade());
            }
        }

        ApsFurnaceChargeLayer layer = new ApsFurnaceChargeLayer();
        layer.setChargeId(chargeId);
        layer.setLayerNo(layerNo);
        layer.setStockId(stockId);
        layer.setCardNo(stock.getCardNo());
        layer.setResNo(stock.getResNo());
        layer.setPrdtId(stock.getPrdtId());
        layer.setPatName(stock.getPatName());
        layer.setPaName(stock.getPaName());
        layer.setCoilWeight(coilWeight);
        layer.setThickness(stock.getActualThickness());
        layer.setWidth(stock.getActualWidth());
        layer.setContractNo(stock.getContractNo());
        chargeLayerMapper.insert(layer);

        Integer totalCoils = charge.getTotalCoils() != null ? charge.getTotalCoils() : 0;
        charge.setTotalCoils(totalCoils + 1);

        BigDecimal totalWeight = charge.getTotalWeight() != null ? charge.getTotalWeight() : BigDecimal.ZERO;
        if (coilWeight != null) {
            charge.setTotalWeight(totalWeight.add(coilWeight));
        }

        Integer layersUsed = charge.getLayersUsed() != null ? charge.getLayersUsed() : 0;
        charge.setLayersUsed(layersUsed + 1);
        chargeMapper.updateById(charge);

        TrcTraceLink traceLink = new TrcTraceLink();
        traceLink.setSourceType("STOCK");
        traceLink.setSourceStockId(stockId);
        traceLink.setSourceCardNo(stock.getCardNo());
        traceLink.setSourceResNo(stock.getResNo());
        traceLink.setSourcePrdtId(stock.getPrdtId());
        traceLink.setSourcePatName(stock.getPatName());
        traceLink.setSourcePaName(stock.getPaName());
        traceLink.setSourceWeight(coilWeight);
        traceLink.setProcessType("ANNEAL");
        traceLink.setContractNo(stock.getContractNo());
        traceLink.setTraceTime(new Date());
        traceLinkMapper.insert(traceLink);
    }

    @Transactional
    public void startAnneal(Long chargeId) {
        ApsFurnaceCharge charge = chargeMapper.selectById(chargeId);
        if (charge == null) {
            throw new BizException("装炉计划不存在: " + chargeId);
        }

        charge.setChargeStatus("ANNEALING");
        charge.setActualAnnealStart(new Date());
        chargeMapper.updateById(charge);

        BasFurnace furnace = furnaceMapper.selectById(charge.getFurnaceId());
        if (furnace != null) {
            furnace.setFurnaceStatus("ANNEALING");
            furnace.setCurrentStartTime(new Date());

            BasAnnealRecipe recipe = annealRecipeMapper.selectById(charge.getRecipeId());
            if (recipe != null && recipe.getTotalCycleHours() != null) {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.HOUR_OF_DAY, recipe.getTotalCycleHours());
                furnace.setExpectedEndTime(cal.getTime());
            }

            furnaceMapper.updateById(furnace);
        }
    }

    @Transactional
    public void completeCharge(Long chargeId) {
        ApsFurnaceCharge charge = chargeMapper.selectById(chargeId);
        if (charge == null) {
            throw new BizException("装炉计划不存在: " + chargeId);
        }

        charge.setChargeStatus("COMPLETED");
        charge.setActualUnloadEnd(new Date());
        chargeMapper.updateById(charge);

        BasFurnace furnace = furnaceMapper.selectById(charge.getFurnaceId());
        if (furnace != null) {
            furnace.setFurnaceStatus("IDLE");
            furnace.setCurrentScheduleId(null);
            furnace.setCurrentStartTime(null);
            furnace.setExpectedEndTime(null);
            furnaceMapper.updateById(furnace);
        }
    }

    public ApsFurnaceCharge getChargeDetail(Long chargeId) {
        ApsFurnaceCharge charge = chargeMapper.selectById(chargeId);
        if (charge == null) {
            throw new BizException("装炉计划不存在: " + chargeId);
        }
        return charge;
    }

    public List<ApsFurnaceChargeLayer> getChargeLayers(Long chargeId) {
        return chargeLayerMapper.selectByChargeId(chargeId);
    }

    public List<ApsFurnaceCharge> getGanttData(Date dateFrom, Date dateTo) {
        LambdaQueryWrapper<ApsFurnaceCharge> wrapper = new LambdaQueryWrapper<>();
        if (dateFrom != null) {
            wrapper.ge(ApsFurnaceCharge::getPlanLoadStart, dateFrom);
        }
        if (dateTo != null) {
            wrapper.le(ApsFurnaceCharge::getPlanLoadStart, dateTo);
        }
        wrapper.orderByAsc(ApsFurnaceCharge::getFurnaceId, ApsFurnaceCharge::getPlanLoadStart);
        return chargeMapper.selectList(wrapper);
    }
}
