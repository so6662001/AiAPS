package com.aiaps.service.inventory;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.production.PrdMaterialIssue;
import com.aiaps.mapper.production.PrdMaterialIssueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class MaterialIssueService {

    private final PrdMaterialIssueMapper materialIssueMapper;
    private final StockService stockService;

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
