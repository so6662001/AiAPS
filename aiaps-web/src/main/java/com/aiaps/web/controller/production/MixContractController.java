package com.aiaps.web.controller.production;

import com.aiaps.common.result.R;
import com.aiaps.domain.base.BasMixContractPolicy;
import com.aiaps.domain.production.PrdMixContractLog;
import com.aiaps.mapper.base.BasMixContractPolicyMapper;
import com.aiaps.service.production.MixContractService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/v1/mix-contract")
@RequiredArgsConstructor
public class MixContractController {

    private final MixContractService mixContractService;
    private final BasMixContractPolicyMapper policyMapper;

    @PostMapping("/check")
    public R<MixContractService.CheckResult> check(@RequestParam Long scheduleId,
                                                    @RequestParam Long stockId) {
        return R.ok(mixContractService.check(scheduleId, stockId));
    }

    @PostMapping("/record")
    public R<PrdMixContractLog> record(@RequestParam Long scheduleId,
                                        @RequestParam String originalContract,
                                        @RequestParam String actualContract,
                                        @RequestParam Long stockId,
                                        @RequestParam Long materialId,
                                        @RequestParam BigDecimal weight,
                                        @RequestParam(required = false) String reason,
                                        @RequestParam(required = false) String createdBy) {
        PrdMixContractLog log = mixContractService.recordMix(
                scheduleId, originalContract, actualContract,
                stockId, materialId, weight, reason, createdBy);
        return R.ok(log);
    }

    @PutMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id,
                           @RequestParam(required = false) String approvedBy) {
        mixContractService.approveMix(id, approvedBy);
        return R.ok();
    }

    @PutMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id,
                          @RequestParam(required = false) String approvedBy) {
        mixContractService.rejectMix(id, approvedBy);
        return R.ok();
    }

    @GetMapping("/schedule/{scheduleId}")
    public R<List<PrdMixContractLog>> getBySchedule(@PathVariable Long scheduleId) {
        return R.ok(mixContractService.getMixLogs(scheduleId));
    }

    @GetMapping("/contract/{contractNo}")
    public R<List<PrdMixContractLog>> getByContract(@PathVariable String contractNo) {
        return R.ok(mixContractService.getMixLogsByContract(contractNo));
    }

    @GetMapping("/policy")
    public R<List<BasMixContractPolicy>> listPolicies() {
        LambdaQueryWrapper<BasMixContractPolicy> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(BasMixContractPolicy::getPolicyId);
        return R.ok(policyMapper.selectList(wrapper));
    }

    @PostMapping("/policy")
    public R<Void> createPolicy(@RequestBody BasMixContractPolicy policy) {
        policyMapper.insert(policy);
        return R.ok();
    }

    @PutMapping("/policy/{id}")
    public R<Void> updatePolicy(@PathVariable Long id,
                                @RequestBody BasMixContractPolicy policy) {
        policy.setPolicyId(id);
        policyMapper.updateById(policy);
        return R.ok();
    }
}
