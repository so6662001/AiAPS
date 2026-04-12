package com.aiaps.service.inventory;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.aps.ApsSchedule;
import com.aiaps.domain.inventory.InvStock;
import com.aiaps.domain.production.PrdMaterialIssue;
import com.aiaps.mapper.aps.ApsScheduleMapper;
import com.aiaps.mapper.inventory.InvStockMapper;
import com.aiaps.mapper.production.PrdMaterialIssueMapper;
import com.aiaps.service.production.MixContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class MaterialIssueService {

    private final PrdMaterialIssueMapper materialIssueMapper;
    private final StockService stockService;
    private final MixContractService mixContractService;
    private final ApsScheduleMapper scheduleMapper;
    private final InvStockMapper stockMapper;

    @Transactional
    public void createIssue(PrdMaterialIssue issue) {
        if (issue.getIssueStatus() == null) {
            issue.setIssueStatus("PENDING");
        }
        issue.setRequestedTime(new Date());
        materialIssueMapper.insert(issue);
    }

    @Transactional
    public void approveIssue(Long issueId) {
        PrdMaterialIssue issue = materialIssueMapper.selectById(issueId);
        if (issue == null) {
            throw new BizException("领料单不存在: " + issueId);
        }
        if (!"PENDING".equals(issue.getIssueStatus())) {
            throw new BizException("领料单状态不允许审批: " + issue.getIssueStatus());
        }
        issue.setIssueStatus("APPROVED");
        materialIssueMapper.updateById(issue);
    }

    @Transactional
    public void executeIssue(Long issueId, String operatedBy) {
        PrdMaterialIssue issue = materialIssueMapper.selectById(issueId);
        if (issue == null) {
            throw new BizException("领料单不存在: " + issueId);
        }
        if (!"APPROVED".equals(issue.getIssueStatus())) {
            throw new BizException("领料单状态不允许执行: " + issue.getIssueStatus());
        }

        // Check contract compatibility before issuing
        if (issue.getScheduleId() != null && issue.getStockId() != null) {
            MixContractService.CheckResult mixCheck = mixContractService.check(issue.getScheduleId(), issue.getStockId());
            if (!mixCheck.isMatch() && !mixCheck.isMixAllowed()) {
                throw new BizException("窜料检查未通过: " + mixCheck.getMessage());
            }
            if (!mixCheck.isMatch() && mixCheck.isMixAllowed()) {
                ApsSchedule schedule = scheduleMapper.selectById(issue.getScheduleId());
                InvStock stock = stockMapper.selectById(issue.getStockId());
                if (schedule != null && stock != null) {
                    mixContractService.recordMix(
                            issue.getScheduleId(),
                            schedule.getContractNo(),
                            stock.getContractNo(),
                            issue.getStockId(),
                            issue.getPrdtId(),
                            issue.getIssueWeight(),
                            "领料时窜料",
                            operatedBy
                    );
                }
            }
        }

        issue.setIssueStatus("ISSUED");
        issue.setIssuedBy(operatedBy);
        issue.setIssuedTime(new Date());
        materialIssueMapper.updateById(issue);

        stockService.stockOut(
                issue.getStockId(),
                issue.getIssueQty(),
                issue.getIssueWeight(),
                "ISSUE",
                issue.getIssueId(),
                issue.getIssueNo(),
                operatedBy
        );
    }
}
