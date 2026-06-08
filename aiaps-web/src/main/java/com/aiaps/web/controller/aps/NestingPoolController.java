package com.aiaps.web.controller.aps;

import com.aiaps.common.result.R;
import com.aiaps.domain.aps.ApsNestingPool;
import com.aiaps.mapper.aps.ApsNestingPoolMapper;
import com.aiaps.service.aps.NestingAutoService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Validated
@RestController
@RequestMapping("/v1/nesting-pool")
@RequiredArgsConstructor
public class NestingPoolController {

    private final NestingAutoService nestingAutoService;
    private final ApsNestingPoolMapper nestingPoolMapper;

    @PostMapping("/collect")
    public R<Void> collectFromMrp(@RequestParam Long runId) {
        nestingAutoService.collectFromMrp(runId);
        return R.ok();
    }

    @GetMapping("/groups")
    public R<List<NestingAutoService.NestingPoolGroup>> getPoolGroups(
            @RequestParam(defaultValue = "PENDING") String status) {
        return R.ok(nestingAutoService.getPoolGroups(status));
    }

    @GetMapping("/group/{groupKey}")
    public R<List<ApsNestingPool>> getGroupItems(@PathVariable String groupKey,
                                                  @RequestParam(defaultValue = "PENDING") String status) {
        List<ApsNestingPool> items = nestingPoolMapper.selectByGroupKey(groupKey, status);
        return R.ok(items);
    }

    @PostMapping
    public R<Void> addToPool(@Valid @RequestBody ApsNestingPool pool) {
        nestingAutoService.addToPool(pool);
        return R.ok();
    }
}
