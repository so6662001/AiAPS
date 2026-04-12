package com.aiaps.service.base;

import com.aiaps.common.exception.BizException;
import com.aiaps.common.result.PageResult;
import com.aiaps.domain.base.BasMold;
import com.aiaps.domain.base.BasMoldChangeMatrix;
import com.aiaps.domain.base.BasMoldProduct;
import com.aiaps.domain.base.BasMoldUsageLog;
import com.aiaps.mapper.base.BasMoldChangeMatrixMapper;
import com.aiaps.mapper.base.BasMoldMapper;
import com.aiaps.mapper.base.BasMoldProductMapper;
import com.aiaps.mapper.base.BasMoldUsageLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MoldService {

    private final BasMoldMapper moldMapper;
    private final BasMoldProductMapper moldProductMapper;
    private final BasMoldChangeMatrixMapper moldChangeMatrixMapper;
    private final BasMoldUsageLogMapper moldUsageLogMapper;

    public BasMold getById(Long moldId) {
        BasMold mold = moldMapper.selectById(moldId);
        if (mold == null) {
            throw new BizException("模具不存在: " + moldId);
        }
        return mold;
    }

    public PageResult<BasMold> page(int pageNum, int pageSize, String moldStatus) {
        LambdaQueryWrapper<BasMold> wrapper = new LambdaQueryWrapper<>();
        if (moldStatus != null && !moldStatus.isEmpty()) {
            wrapper.eq(BasMold::getMoldStatus, moldStatus);
        }
        wrapper.eq(BasMold::getIsActive, true);
        wrapper.orderByAsc(BasMold::getMoldCode);

        Page<BasMold> page = moldMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);

        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Transactional
    public void create(BasMold mold) {
        mold.setIsActive(true);
        mold.setCreatedTime(new Date());
        if (mold.getCurrentAccumulated() == null) {
            mold.setCurrentAccumulated(BigDecimal.ZERO);
        }
        if (mold.getMoldStatus() == null) {
            mold.setMoldStatus("IDLE");
        }
        moldMapper.insert(mold);
    }

    @Transactional
    public void update(BasMold mold) {
        moldMapper.updateById(mold);
    }

    public List<BasMoldProduct> getMoldProducts(Long moldId) {
        return moldProductMapper.selectByMoldId(moldId);
    }

    public List<BasMoldProduct> findCompatibleMolds(Long materialId) {
        List<BasMoldProduct> products = moldProductMapper.selectByMaterialId(materialId);
        products.sort(Comparator
                .comparing((BasMoldProduct p) -> p.getIsPrimary() != null && p.getIsPrimary() ? 0 : 1)
                .thenComparing(p -> p.getEfficiencyRate() != null ? p.getEfficiencyRate().negate() : BigDecimal.ZERO));
        return products;
    }

    public Integer getChangeTime(Long wcId, Long fromMoldId, Long toMoldId) {
        LambdaQueryWrapper<BasMoldChangeMatrix> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BasMoldChangeMatrix::getWcId, wcId);
        wrapper.eq(BasMoldChangeMatrix::getToMoldId, toMoldId);

        if (fromMoldId != null) {
            wrapper.eq(BasMoldChangeMatrix::getFromMoldId, fromMoldId);
        } else {
            wrapper.isNull(BasMoldChangeMatrix::getFromMoldId);
        }

        BasMoldChangeMatrix matrix = moldChangeMatrixMapper.selectOne(wrapper);
        if (matrix == null) {
            return null;
        }

        if (matrix.getIsQuickChange() != null && matrix.getIsQuickChange()
                && matrix.getQuickChangeMinutes() != null) {
            return matrix.getQuickChangeMinutes();
        }
        return matrix.getChangeTimeMinutes();
    }

    @Transactional
    public void recordUsage(Long moldId, Long wcId, Long scheduleId, BigDecimal usageQty) {
        BasMold mold = getById(moldId);

        BigDecimal before = mold.getCurrentAccumulated() != null
                ? mold.getCurrentAccumulated() : BigDecimal.ZERO;
        BigDecimal after = before.add(usageQty);

        BasMoldUsageLog usageLog = new BasMoldUsageLog();
        usageLog.setMoldId(moldId);
        usageLog.setWcId(wcId);
        usageLog.setScheduleId(scheduleId);
        usageLog.setUsageDate(new Date());
        usageLog.setUsageQty(usageQty);
        usageLog.setAccumulatedBefore(before);
        usageLog.setAccumulatedAfter(after);
        usageLog.setMoldEvent("PRODUCTION");
        moldUsageLogMapper.insert(usageLog);

        mold.setCurrentAccumulated(after);

        BigDecimal warningThreshold = mold.getMaxProductionQty()
                .multiply(mold.getWarningPct())
                .divide(BigDecimal.valueOf(100), 3, BigDecimal.ROUND_HALF_UP);

        if (after.compareTo(warningThreshold) >= 0
                && after.compareTo(mold.getMaxProductionQty()) < 0) {
            log.warn("模具 {} 累计已达 {}/{} ({}%), 接近寿命上限",
                    mold.getMoldCode(), after, mold.getMaxProductionQty(),
                    after.multiply(BigDecimal.valueOf(100))
                            .divide(mold.getMaxProductionQty(), 1, BigDecimal.ROUND_HALF_UP));
        }

        if (after.compareTo(mold.getMaxProductionQty()) >= 0) {
            log.warn("模具 {} 累计已达上限 {}/{}, 设置为需要维修",
                    mold.getMoldCode(), after, mold.getMaxProductionQty());
            mold.setMoldStatus("NEEDS_REPAIR");
        }

        moldMapper.updateById(mold);
    }

    public CapacityCheckResult checkMoldCapacity(Long moldId, BigDecimal plannedWeight) {
        BasMold mold = getById(moldId);

        BigDecimal accumulated = mold.getCurrentAccumulated() != null
                ? mold.getCurrentAccumulated() : BigDecimal.ZERO;
        BigDecimal remaining = mold.getMaxProductionQty().subtract(accumulated);

        CapacityCheckResult result = new CapacityCheckResult();
        result.setRemainingCapacity(remaining);

        if (plannedWeight.compareTo(remaining) <= 0) {
            result.setNeedsSplit(false);
            result.setSplitWeight1(plannedWeight);
            result.setSplitWeight2(BigDecimal.ZERO);
        } else {
            result.setNeedsSplit(true);
            result.setSplitWeight1(remaining);
            result.setSplitWeight2(plannedWeight.subtract(remaining));
        }

        return result;
    }

    @Data
    public static class CapacityCheckResult {
        private BigDecimal remainingCapacity;
        private boolean needsSplit;
        private BigDecimal splitWeight1;
        private BigDecimal splitWeight2;
    }
}
