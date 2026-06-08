package com.aiaps.service.production;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.production.PrdMaterialSwap;
import com.aiaps.domain.trace.TrcTraceLink;
import com.aiaps.mapper.aps.ApsScheduleMapper;
import com.aiaps.mapper.inventory.InvStockMapper;
import com.aiaps.mapper.production.PrdMaterialSwapMapper;
import com.aiaps.mapper.trace.TrcTraceLinkMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MaterialSwapService {

    private final PrdMaterialSwapMapper swapMapper;
    private final InvStockMapper stockMapper;
    private final ApsScheduleMapper scheduleMapper;
    private final TrcTraceLinkMapper traceLinkMapper;

    @Transactional
    public void recordSwap(PrdMaterialSwap swap) {
        if (swap.getScheduleId() == null) {
            throw new BizException("scheduleId is required");
        }
        if (scheduleMapper.selectById(swap.getScheduleId()) == null) {
            throw new BizException("Schedule not found: " + swap.getScheduleId());
        }

        swap.setSwapStatus("COMPLETED");
        if (swap.getSwapTime() == null) {
            swap.setSwapTime(new Date());
        }
        swapMapper.insert(swap);

        TrcTraceLink link = new TrcTraceLink();
        link.setSourceType("COIL");
        link.setSourceResNo(swap.getOldCoilNo());
        link.setSourcePrdtId(swap.getOldMaterialId());
        link.setSourcePatName(swap.getOldGradeCode());
        link.setSourceStockId(swap.getOldStockId());
        link.setProcessType("SWAP");
        link.setScheduleId(swap.getScheduleId());
        link.setTargetType("COIL");
        link.setTargetResNo(swap.getNewCoilNo());
        link.setTargetPrdtId(swap.getNewMaterialId());
        link.setTargetPatName(swap.getNewGradeCode());
        link.setTargetStockId(swap.getNewStockId());
        link.setContractNo(swap.getContractNo());
        link.setTraceTime(swap.getSwapTime());
        link.setOperatedBy(swap.getOperatedBy());
        traceLinkMapper.insert(link);
    }

    public List<PrdMaterialSwap> getBySchedule(Long scheduleId) {
        return swapMapper.selectByScheduleId(scheduleId);
    }

    public List<PrdMaterialSwap> getByCoilNo(String coilNo) {
        LambdaQueryWrapper<PrdMaterialSwap> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrdMaterialSwap::getOldCoilNo, coilNo)
                .or()
                .eq(PrdMaterialSwap::getNewCoilNo, coilNo);
        wrapper.orderByDesc(PrdMaterialSwap::getSwapTime);
        return swapMapper.selectList(wrapper);
    }
}
