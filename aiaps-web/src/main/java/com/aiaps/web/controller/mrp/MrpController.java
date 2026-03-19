package com.aiaps.web.controller.mrp;

import com.aiaps.common.result.PageResult;
import com.aiaps.common.result.R;
import com.aiaps.domain.mrp.MrpPlanOrder;
import com.aiaps.domain.mrp.MrpRunLog;
import com.aiaps.mapper.mrp.MrpPlanOrderMapper;
import com.aiaps.service.mrp.MrpEngineService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/mrp")
@RequiredArgsConstructor
public class MrpController {

    private final MrpEngineService mrpEngineService;
    private final MrpPlanOrderMapper planOrderMapper;

    @PostMapping("/run")
    public R<Void> run(
            @RequestParam String runType,
            @RequestParam(defaultValue = "30") Integer horizonDays,
            @RequestParam String runBy) {
        mrpEngineService.runMrp(runType, horizonDays, runBy);
        return R.ok();
    }

    @GetMapping("/run/{runId}/progress")
    public R<MrpRunLog> getRunProgress(@PathVariable Long runId) {
        return R.ok(mrpEngineService.getRunProgress(runId));
    }

    @PutMapping("/run/{runId}/cancel")
    public R<Void> cancelRun(@PathVariable Long runId) {
        mrpEngineService.cancelRun(runId);
        return R.ok();
    }

    @GetMapping("/plan-order")
    public R<PageResult<MrpPlanOrder>> listPlanOrders(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String patName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String contractNo) {
        LambdaQueryWrapper<MrpPlanOrder> wrapper = new LambdaQueryWrapper<>();
        if (patName != null && !patName.isEmpty()) {
            wrapper.eq(MrpPlanOrder::getPatName, patName);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(MrpPlanOrder::getOrderStatus, status);
        }
        if (contractNo != null && !contractNo.isEmpty()) {
            wrapper.eq(MrpPlanOrder::getContractNo, contractNo);
        }
        wrapper.orderByDesc(MrpPlanOrder::getCreatedTime);
        Page<MrpPlanOrder> page = planOrderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    @PutMapping("/plan-order/confirm")
    public R<Void> confirmPlanOrders(@RequestBody List<Long> ids) {
        for (Long id : ids) {
            MrpPlanOrder order = planOrderMapper.selectById(id);
            if (order != null) {
                order.setOrderStatus("CONFIRMED");
                order.setIsFirmed(true);
                planOrderMapper.updateById(order);
            }
        }
        return R.ok();
    }

    @PutMapping("/plan-order/cancel")
    public R<Void> cancelPlanOrders(@RequestBody List<Long> ids) {
        for (Long id : ids) {
            MrpPlanOrder order = planOrderMapper.selectById(id);
            if (order != null) {
                order.setOrderStatus("CANCELLED");
                planOrderMapper.updateById(order);
            }
        }
        return R.ok();
    }
}
