package com.aiaps.service.production;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.aps.ApsSchedule;
import com.aiaps.domain.aps.ApsScheduleOper;
import com.aiaps.domain.demand.DemDemandLine;
import com.aiaps.domain.production.PrdReport;
import com.aiaps.mapper.aps.ApsScheduleMapper;
import com.aiaps.mapper.aps.ApsScheduleOperMapper;
import com.aiaps.mapper.demand.DemDemandLineMapper;
import com.aiaps.mapper.production.PrdReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final PrdReportMapper reportMapper;
    private final ApsScheduleMapper scheduleMapper;
    private final ApsScheduleOperMapper scheduleOperMapper;
    private final DemDemandLineMapper demandLineMapper;

    @Transactional
    public void submitReport(PrdReport report) {
        reportMapper.insert(report);

        if (report.getScheduleId() != null) {
            ApsSchedule schedule = scheduleMapper.selectById(report.getScheduleId());
            if (schedule != null) {
                BigDecimal goodQty = schedule.getGoodQty() != null ? schedule.getGoodQty() : BigDecimal.ZERO;
                BigDecimal scrapQty = schedule.getScrapQty() != null ? schedule.getScrapQty() : BigDecimal.ZERO;
                BigDecimal goodWeight = schedule.getGoodWeight() != null ? schedule.getGoodWeight() : BigDecimal.ZERO;
                BigDecimal scrapWeight = schedule.getScrapWeight() != null ? schedule.getScrapWeight() : BigDecimal.ZERO;
                BigDecimal inputWeight = schedule.getInputWeight() != null ? schedule.getInputWeight() : BigDecimal.ZERO;

                schedule.setGoodQty(goodQty.add(report.getGoodQty() != null ? report.getGoodQty() : BigDecimal.ZERO));
                schedule.setScrapQty(scrapQty.add(report.getScrapQty() != null ? report.getScrapQty() : BigDecimal.ZERO));
                schedule.setGoodWeight(goodWeight.add(report.getGoodWeight() != null ? report.getGoodWeight() : BigDecimal.ZERO));
                schedule.setScrapWeight(scrapWeight.add(report.getScrapWeight() != null ? report.getScrapWeight() : BigDecimal.ZERO));
                schedule.setInputWeight(inputWeight.add(report.getInputWeight() != null ? report.getInputWeight() : BigDecimal.ZERO));

                if (schedule.getInputWeight() != null && schedule.getInputWeight().compareTo(BigDecimal.ZERO) > 0) {
                    schedule.setYieldRate(schedule.getGoodWeight()
                            .divide(schedule.getInputWeight(), 4, RoundingMode.HALF_UP));
                }

                scheduleMapper.updateById(schedule);
            }
        }

        if (report.getSchedOperId() != null) {
            ApsScheduleOper oper = scheduleOperMapper.selectById(report.getSchedOperId());
            if (oper != null) {
                BigDecimal completedQty = oper.getCompletedQty() != null ? oper.getCompletedQty() : BigDecimal.ZERO;
                BigDecimal completedWeight = oper.getCompletedWeight() != null ? oper.getCompletedWeight() : BigDecimal.ZERO;
                BigDecimal scrapQty = oper.getScrapQty() != null ? oper.getScrapQty() : BigDecimal.ZERO;
                BigDecimal scrapWeight = oper.getScrapWeight() != null ? oper.getScrapWeight() : BigDecimal.ZERO;

                oper.setCompletedQty(completedQty.add(report.getGoodQty() != null ? report.getGoodQty() : BigDecimal.ZERO));
                oper.setCompletedWeight(completedWeight.add(report.getGoodWeight() != null ? report.getGoodWeight() : BigDecimal.ZERO));
                oper.setScrapQty(scrapQty.add(report.getScrapQty() != null ? report.getScrapQty() : BigDecimal.ZERO));
                oper.setScrapWeight(scrapWeight.add(report.getScrapWeight() != null ? report.getScrapWeight() : BigDecimal.ZERO));

                if (oper.getPlannedQty() != null && oper.getCompletedQty().compareTo(oper.getPlannedQty()) >= 0) {
                    oper.setOperStatus("COMPLETED");
                }

                scheduleOperMapper.updateById(oper);
            }
        }

        updateDemandLineProgress(report);
    }

    private void updateDemandLineProgress(PrdReport report) {
        if (report.getScheduleId() == null) {
            return;
        }
        ApsSchedule schedule = scheduleMapper.selectById(report.getScheduleId());
        if (schedule == null || schedule.getSourceDemandNo() == null) {
            return;
        }
        List<DemDemandLine> lines = demandLineMapper.selectByContract(schedule.getContractNo());
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (DemDemandLine line : lines) {
            if (line.getPrdtId() != null && line.getPrdtId().equals(schedule.getPrdtId())) {
                BigDecimal producedQty = line.getProducedQty() != null ? line.getProducedQty() : BigDecimal.ZERO;
                BigDecimal producedWeight = line.getProducedWeight() != null ? line.getProducedWeight() : BigDecimal.ZERO;
                line.setProducedQty(producedQty.add(report.getGoodQty() != null ? report.getGoodQty() : BigDecimal.ZERO));
                line.setProducedWeight(producedWeight.add(report.getGoodWeight() != null ? report.getGoodWeight() : BigDecimal.ZERO));

                boolean qtyMet = line.getRequiredQty() != null
                        && line.getProducedQty().compareTo(line.getRequiredQty()) >= 0;
                boolean weightMet = line.getRequiredWeight() != null
                        && line.getProducedWeight().compareTo(line.getRequiredWeight()) >= 0;

                if (qtyMet || weightMet) {
                    line.setLineStatus("COMPLETED");
                } else {
                    line.setLineStatus("PARTIAL");
                }
                demandLineMapper.updateById(line);
            }
        }
    }

    public List<PrdReport> getReportsBySchedule(Long scheduleId) {
        return reportMapper.selectByScheduleId(scheduleId);
    }
}
