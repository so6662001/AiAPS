package com.aiaps.service.demand;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.demand.DemDemandHead;
import com.aiaps.domain.demand.DemDemandLine;
import com.aiaps.mapper.demand.DemDemandHeadMapper;
import com.aiaps.mapper.demand.DemDemandLineMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DemandService {

    private final DemDemandHeadMapper demandHeadMapper;
    private final DemDemandLineMapper demandLineMapper;

    @Transactional
    public void createDemand(DemDemandHead head, List<DemDemandLine> lines) {
        head.setCreatedTime(new Date());
        if (head.getDemandStatus() == null) {
            head.setDemandStatus("OPEN");
        }
        demandHeadMapper.insert(head);

        if (lines != null) {
            for (int i = 0; i < lines.size(); i++) {
                DemDemandLine line = lines.get(i);
                line.setDemandId(head.getDemandId());
                if (line.getLineNo() == null) {
                    line.setLineNo(i + 1);
                }
                if (line.getLineStatus() == null) {
                    line.setLineStatus("OPEN");
                }
                demandLineMapper.insert(line);
            }
        }
    }

    public DemDemandHead getDemandWithLines(Long demandId) {
        DemDemandHead head = demandHeadMapper.selectById(demandId);
        if (head == null) {
            throw new BizException("需求单不存在: " + demandId);
        }
        LambdaQueryWrapper<DemDemandLine> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DemDemandLine::getDemandId, demandId);
        wrapper.orderByAsc(DemDemandLine::getLineNo);
        List<DemDemandLine> lines = demandLineMapper.selectList(wrapper);
        head.setLines(lines);
        return head;
    }

    public List<DemDemandLine> getOpenDemands(Long prdtId, String patName) {
        return demandLineMapper.selectOpenDemands(prdtId, patName);
    }

    public List<DemDemandLine> getDemandsByContract(String contractNo) {
        return demandLineMapper.selectByContract(contractNo);
    }

    @Transactional
    public void updateDemandLineProgress(Long lineId, BigDecimal producedQty, BigDecimal producedWeight) {
        DemDemandLine line = demandLineMapper.selectById(lineId);
        if (line == null) {
            throw new BizException("需求行不存在: " + lineId);
        }
        line.setProducedQty(producedQty);
        line.setProducedWeight(producedWeight);

        boolean qtyMet = line.getRequiredQty() != null
                && producedQty != null
                && producedQty.compareTo(line.getRequiredQty()) >= 0;
        boolean weightMet = line.getRequiredWeight() != null
                && producedWeight != null
                && producedWeight.compareTo(line.getRequiredWeight()) >= 0;

        if (qtyMet || weightMet) {
            line.setLineStatus("COMPLETED");
        } else if ((producedQty != null && producedQty.compareTo(BigDecimal.ZERO) > 0)
                || (producedWeight != null && producedWeight.compareTo(BigDecimal.ZERO) > 0)) {
            line.setLineStatus("PARTIAL");
        }

        demandLineMapper.updateById(line);
    }
}
