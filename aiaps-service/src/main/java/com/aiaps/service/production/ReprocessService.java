package com.aiaps.service.production;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.aps.ApsSchedule;
import com.aiaps.domain.production.PrdReprocess;
import com.aiaps.domain.trace.TrcTraceLink;
import com.aiaps.mapper.aps.ApsScheduleMapper;
import com.aiaps.mapper.aps.ApsScheduleOperMapper;
import com.aiaps.mapper.production.PrdReprocessMapper;
import com.aiaps.mapper.trace.TrcTraceLinkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReprocessService {

    private final PrdReprocessMapper reprocessMapper;
    private final ApsScheduleMapper scheduleMapper;
    private final ApsScheduleOperMapper scheduleOperMapper;
    private final TrcTraceLinkMapper traceLinkMapper;

    @Transactional
    public void createReprocess(PrdReprocess reprocess) {
        if (reprocess.getReprocessType() == null) {
            throw new BizException("reprocessType is required");
        }
        reprocess.setReprocessStatus("PENDING");
        reprocess.setCreatedTime(new Date());
        reprocessMapper.insert(reprocess);
    }

    @Transactional
    public void scheduleReprocess(Long reprocessId) {
        PrdReprocess reprocess = reprocessMapper.selectById(reprocessId);
        if (reprocess == null) {
            throw new BizException("Reprocess record not found: " + reprocessId);
        }

        ApsSchedule schedule = new ApsSchedule();
        schedule.setScheduleNo("RPR-" + reprocess.getReprocessNo());
        schedule.setPrdtId(reprocess.getOutputMaterialId());
        schedule.setDemandGradeCode(reprocess.getOutputGradeCode() != null ? reprocess.getOutputGradeCode() : "");
        schedule.setPlannedQty(reprocess.getOutputQtyPlanned() != null ? reprocess.getOutputQtyPlanned() : BigDecimal.ZERO);
        schedule.setPlannedWeight(reprocess.getOutputQtyPlanned() != null ? reprocess.getOutputQtyPlanned() : BigDecimal.ZERO);
        schedule.setSchedulePhase("MFG");
        schedule.setOutputFlowType(reprocess.getOutputFlowType());
        schedule.setOutputFlowDesc(reprocess.getOutputFlowDesc());
        schedule.setContractNo(reprocess.getContractNo());
        schedule.setScheduleStatus("PLANNED");
        schedule.setScheduleStart(new Date());
        schedule.setScheduleEnd(new Date());
        schedule.setCreatedTime(new Date());
        scheduleMapper.insert(schedule);

        reprocess.setScheduleId(schedule.getScheduleId());
        reprocess.setReprocessStatus("SCHEDULED");
        reprocess.setUpdatedTime(new Date());
        reprocessMapper.updateById(reprocess);
    }

    @Transactional
    public void completeReprocess(Long reprocessId, BigDecimal actualQty, BigDecimal scrapQty) {
        PrdReprocess reprocess = reprocessMapper.selectById(reprocessId);
        if (reprocess == null) {
            throw new BizException("Reprocess record not found: " + reprocessId);
        }
        reprocess.setOutputQtyActual(actualQty);
        reprocess.setScrapQty(scrapQty);
        reprocess.setReprocessStatus("COMPLETED");
        reprocess.setUpdatedTime(new Date());
        reprocessMapper.updateById(reprocess);
    }

    public List<PrdReprocess> getBySourceSchedule(Long scheduleId) {
        return reprocessMapper.selectBySourceScheduleId(scheduleId);
    }

    public List<PrdReprocess> getReprocessChain(Long reprocessId) {
        List<PrdReprocess> chain = new ArrayList<>();
        Long currentId = reprocessId;
        while (currentId != null) {
            PrdReprocess reprocess = reprocessMapper.selectById(currentId);
            if (reprocess == null) {
                break;
            }
            chain.add(reprocess);
            currentId = reprocess.getNextReprocessId();
        }
        return chain;
    }
}
