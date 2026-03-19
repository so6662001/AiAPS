package com.aiaps.web.controller.demand;

import com.aiaps.common.result.PageResult;
import com.aiaps.common.result.R;
import com.aiaps.domain.demand.DemDemandHead;
import com.aiaps.domain.demand.DemDemandLine;
import com.aiaps.mapper.demand.DemDemandHeadMapper;
import com.aiaps.service.demand.DemandService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/demand")
@RequiredArgsConstructor
public class DemandController {

    private final DemandService demandService;
    private final DemDemandHeadMapper demandHeadMapper;

    @GetMapping
    public R<PageResult<DemDemandHead>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<DemDemandHead> wrapper = new LambdaQueryWrapper<>();
        if (source != null && !source.isEmpty()) {
            wrapper.eq(DemDemandHead::getDemandSource, source);
        }
        if (customerCode != null && !customerCode.isEmpty()) {
            wrapper.eq(DemDemandHead::getCustomerCode, customerCode);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(DemDemandHead::getDemandStatus, status);
        }
        wrapper.orderByDesc(DemDemandHead::getCreatedTime);
        Page<DemDemandHead> page = demandHeadMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<DemDemandHead> getById(@PathVariable Long id) {
        return R.ok(demandService.getDemandWithLines(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody DemDemandHead head) {
        demandService.createDemand(head, head.getLines());
        return R.ok();
    }

    @GetMapping("/contract/{contractNo}")
    public R<List<DemDemandLine>> getByContract(@PathVariable String contractNo) {
        return R.ok(demandService.getDemandsByContract(contractNo));
    }

    @GetMapping("/open")
    public R<List<DemDemandLine>> getOpenDemands(
            @RequestParam(required = false) Long prdtId,
            @RequestParam(required = false) String patName) {
        return R.ok(demandService.getOpenDemands(prdtId, patName));
    }
}
