package com.aiaps.web.controller.aps;

import com.aiaps.common.result.PageResult;
import com.aiaps.common.result.R;
import com.aiaps.domain.aps.ApsChangeLog;
import com.aiaps.domain.aps.ApsScheduleSnapshot;
import com.aiaps.service.aps.ScheduleModificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aiaps.mapper.aps.ApsScheduleSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/schedule-mod")
@RequiredArgsConstructor
public class ScheduleModificationController {

    private final ScheduleModificationService modificationService;
    private final ApsScheduleSnapshotMapper snapshotMapper;

    @PostMapping("/impact-preview")
    public R<Map<String, Object>> impactPreview(
            @RequestParam Long scheduleId,
            @RequestParam String changeType,
            @RequestBody(required = false) Map<String, Object> changeParams) {
        return R.ok(modificationService.previewImpact(scheduleId, changeType, changeParams));
    }

    @PostMapping("/snapshot")
    public R<Long> createSnapshot(
            @RequestParam List<Long> scheduleIds,
            @RequestParam(required = false) String description) {
        return R.ok(modificationService.createSnapshot(scheduleIds, "MANUAL", description, null));
    }

    @GetMapping("/snapshot/list")
    public R<PageResult<ApsScheduleSnapshot>> listSnapshots(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        LambdaQueryWrapper<ApsScheduleSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ApsScheduleSnapshot::getCreatedTime);
        Page<ApsScheduleSnapshot> page = snapshotMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    @GetMapping("/snapshot/{id}/compare")
    public R<List<Map<String, Object>>> compareSnapshot(@PathVariable Long id) {
        return R.ok(modificationService.compareSnapshots(id));
    }

    @PostMapping("/snapshot/{id}/rollback")
    public R<Void> rollbackSnapshot(@PathVariable Long id) {
        modificationService.rollbackToSnapshot(id);
        return R.ok();
    }

    @GetMapping("/change-log/{scheduleId}")
    public R<List<ApsChangeLog>> getScheduleChangeLog(@PathVariable Long scheduleId) {
        return R.ok(modificationService.getChangeHistory(scheduleId, "SCHEDULE"));
    }

    @GetMapping("/change-log/plan/{planOrderId}")
    public R<List<ApsChangeLog>> getPlanChangeLog(@PathVariable Long planOrderId) {
        return R.ok(modificationService.getChangeHistory(planOrderId, "PLAN"));
    }
}
