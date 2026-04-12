package com.aiaps.service.production;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.aps.ApsSchedule;
import com.aiaps.domain.inventory.InvStock;
import com.aiaps.domain.production.PrdReplenish;
import com.aiaps.mapper.aps.ApsScheduleMapper;
import com.aiaps.mapper.inventory.InvStockMapper;
import com.aiaps.mapper.production.PrdReplenishMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReplenishService {

    private final PrdReplenishMapper replenishMapper;
    private final InvStockMapper stockMapper;
    private final ApsScheduleMapper scheduleMapper;

    @Transactional
    public void createReplenish(PrdReplenish replenish) {
        if (replenish.getScheduleId() == null) {
            throw new BizException("scheduleId is required");
        }
        ApsSchedule schedule = scheduleMapper.selectById(replenish.getScheduleId());
        if (schedule == null) {
            throw new BizException("Schedule not found: " + replenish.getScheduleId());
        }
        replenish.setReplenishStatus("PENDING");
        replenish.setRequestedTime(new Date());
        replenishMapper.insert(replenish);
    }

    @Transactional
    public void approveReplenish(Long replenishId) {
        PrdReplenish replenish = replenishMapper.selectById(replenishId);
        if (replenish == null) {
            throw new BizException("Replenish record not found: " + replenishId);
        }
        replenish.setReplenishStatus("APPROVED");
        replenish.setApprovedTime(new Date());
        replenishMapper.updateById(replenish);
    }

    @Transactional
    public void rejectReplenish(Long replenishId, String reason) {
        PrdReplenish replenish = replenishMapper.selectById(replenishId);
        if (replenish == null) {
            throw new BizException("Replenish record not found: " + replenishId);
        }
        replenish.setReplenishStatus("REJECTED");
        replenish.setReasonDesc(reason);
        replenishMapper.updateById(replenish);
    }

    @Transactional
    public void executeReplenish(Long replenishId, String operatedBy) {
        PrdReplenish replenish = replenishMapper.selectById(replenishId);
        if (replenish == null) {
            throw new BizException("Replenish record not found: " + replenishId);
        }
        replenish.setReplenishStatus("ISSUED");
        replenish.setIssuedTime(new Date());
        replenish.setRequestedBy(operatedBy);
        replenishMapper.updateById(replenish);

        if (replenish.getSourceStockId() != null) {
            InvStock stock = stockMapper.selectById(replenish.getSourceStockId());
            if (stock != null && replenish.getReplenishWeight() != null) {
                stock.setOnHandWeight(stock.getOnHandWeight().subtract(replenish.getReplenishWeight()));
                stock.setOnHandQty(stock.getOnHandQty().subtract(
                        replenish.getReplenishQty() != null ? replenish.getReplenishQty() : java.math.BigDecimal.ZERO));
                stockMapper.updateById(stock);
            }
        }
    }

    @Transactional
    public void receiveReplenish(Long replenishId) {
        PrdReplenish replenish = replenishMapper.selectById(replenishId);
        if (replenish == null) {
            throw new BizException("Replenish record not found: " + replenishId);
        }
        replenish.setReplenishStatus("RECEIVED");
        replenish.setReceivedTime(new Date());
        replenishMapper.updateById(replenish);
    }

    public List<InvStock> recommendSources(Long scheduleId) {
        ApsSchedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BizException("Schedule not found: " + scheduleId);
        }

        List<InvStock> result = new ArrayList<>();

        List<InvStock> sameSpecSameGrade = stockMapper.selectAvailable(
                schedule.getPrdtId(), schedule.getDemandGradeCode(), null);
        if (sameSpecSameGrade != null) {
            result.addAll(sameSpecSameGrade);
        }

        List<InvStock> anyGrade = stockMapper.selectByMaterialWithAnyGradeOrigin(schedule.getPrdtId());
        if (anyGrade != null) {
            for (InvStock stock : anyGrade) {
                if (!result.contains(stock)) {
                    result.add(stock);
                }
            }
        }

        return result;
    }

    public List<PrdReplenish> getBySchedule(Long scheduleId) {
        return replenishMapper.selectByScheduleId(scheduleId);
    }
}
