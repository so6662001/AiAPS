package com.aiaps.service.production;

import com.aiaps.domain.aps.ApsSchedule;
import com.aiaps.domain.base.BasMixContractPolicy;
import com.aiaps.domain.inventory.InvStock;
import com.aiaps.domain.production.PrdMixContractLog;
import com.aiaps.mapper.aps.ApsScheduleMapper;
import com.aiaps.mapper.base.BasMixContractPolicyMapper;
import com.aiaps.mapper.inventory.InvStockMapper;
import com.aiaps.mapper.production.PrdMixContractLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MixContractService {

    private final BasMixContractPolicyMapper policyMapper;
    private final PrdMixContractLogMapper mixLogMapper;
    private final InvStockMapper stockMapper;
    private final ApsScheduleMapper scheduleMapper;

    @Data
    public static class CheckResult {
        private boolean match;
        private boolean mixAllowed;
        private boolean needApproval;
        private String message;
    }

    public CheckResult check(Long scheduleId, Long stockId) {
        CheckResult result = new CheckResult();

        ApsSchedule schedule = scheduleMapper.selectById(scheduleId);
        InvStock stock = stockMapper.selectById(stockId);

        if (schedule == null || stock == null) {
            result.setMatch(false);
            result.setMixAllowed(false);
            result.setMessage("排程或库存不存在");
            return result;
        }

        String scheduleContract = schedule.getContractNo();
        String stockContract = stock.getContractNo();

        if (stockContract == null || stockContract.isEmpty()) {
            result.setMatch(true);
            return result;
        }

        if (stockContract.equals(scheduleContract)) {
            result.setMatch(true);
            return result;
        }

        result.setMatch(false);

        if (Boolean.TRUE.equals(schedule.getAllowMixContract())) {
            result.setMixAllowed(true);
            result.setNeedApproval(false);
            return result;
        }

        BasMixContractPolicy policy = policyMapper.selectByCustomer(schedule.getCustomerCode());
        if (policy == null) {
            policy = policyMapper.selectGlobal();
        }

        if (policy != null && Boolean.TRUE.equals(policy.getAllowMix())) {
            result.setMixAllowed(true);
            result.setNeedApproval(Boolean.TRUE.equals(policy.getNeedApproval()));
        } else {
            result.setMixAllowed(false);
            result.setMessage("不允许窜料");
        }

        return result;
    }

    @Transactional
    public PrdMixContractLog recordMix(Long scheduleId, String originalContract, String actualContract,
                                        Long stockId, Long materialId, BigDecimal weight,
                                        String reason, String createdBy) {
        PrdMixContractLog log = new PrdMixContractLog();
        log.setScheduleId(scheduleId);
        log.setOriginalContract(originalContract);
        log.setActualContract(actualContract);
        log.setStockId(stockId);
        log.setMaterialId(materialId);
        log.setMixWeight(weight);
        log.setApprovalStatus("PENDING");
        log.setMixReason(reason);
        log.setCreatedBy(createdBy);
        log.setCreatedTime(new Date());
        mixLogMapper.insert(log);
        return log;
    }

    @Transactional
    public void approveMix(Long mixLogId, String approvedBy) {
        PrdMixContractLog log = mixLogMapper.selectById(mixLogId);
        if (log != null) {
            log.setApprovalStatus("APPROVED");
            log.setApprovedBy(approvedBy);
            log.setApprovedTime(new Date());
            mixLogMapper.updateById(log);
        }
    }

    @Transactional
    public void rejectMix(Long mixLogId, String approvedBy) {
        PrdMixContractLog log = mixLogMapper.selectById(mixLogId);
        if (log != null) {
            log.setApprovalStatus("REJECTED");
            log.setApprovedBy(approvedBy);
            log.setApprovedTime(new Date());
            mixLogMapper.updateById(log);
        }
    }

    public List<PrdMixContractLog> getMixLogs(Long scheduleId) {
        LambdaQueryWrapper<PrdMixContractLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrdMixContractLog::getScheduleId, scheduleId);
        wrapper.orderByDesc(PrdMixContractLog::getCreatedTime);
        return mixLogMapper.selectList(wrapper);
    }

    public List<PrdMixContractLog> getMixLogsByContract(String contractNo) {
        LambdaQueryWrapper<PrdMixContractLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrdMixContractLog::getOriginalContract, contractNo)
                .or()
                .eq(PrdMixContractLog::getActualContract, contractNo);
        wrapper.orderByDesc(PrdMixContractLog::getCreatedTime);
        return mixLogMapper.selectList(wrapper);
    }
}
