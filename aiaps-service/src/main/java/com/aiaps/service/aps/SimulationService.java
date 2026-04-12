package com.aiaps.service.aps;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.aps.ApsSchedule;
import com.aiaps.domain.aps.ApsScheduleOper;
import com.aiaps.mapper.aps.ApsScheduleMapper;
import com.aiaps.mapper.aps.ApsScheduleOperMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SimulationService {

    private final ApsScheduleMapper scheduleMapper;
    private final ApsScheduleOperMapper scheduleOperMapper;
    private final ScheduleModificationService modificationService;

    @Transactional
    public Map<String, Object> createSimulation(Long scheduleId, String operationType, Map<String, Object> params) {
        ApsSchedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BizException("排产不存在: " + scheduleId);
        }

        List<Long> snapshotIds = Collections.singletonList(scheduleId);
        Long snapshotId = modificationService.createSnapshot(snapshotIds, "SIMULATION", "模拟: " + operationType, "system");

        Map<String, Object> impact = modificationService.previewImpact(scheduleId, operationType, params);

        Map<String, Object> result = new HashMap<>();
        result.put("snapshotId", snapshotId);
        result.put("scheduleId", scheduleId);
        result.put("operationType", operationType);
        result.put("impact", impact);
        result.put("status", "CREATED");
        return result;
    }

    public Map<String, Object> applySimulation(Long snapshotId) {
        Map<String, Object> result = new HashMap<>();
        result.put("snapshotId", snapshotId);
        result.put("status", "APPLIED");
        result.put("message", "模拟已应用");
        return result;
    }

    @Transactional
    public Map<String, Object> discardSimulation(Long snapshotId) {
        modificationService.rollbackToSnapshot(snapshotId);

        Map<String, Object> result = new HashMap<>();
        result.put("snapshotId", snapshotId);
        result.put("status", "DISCARDED");
        result.put("message", "模拟已回滚");
        return result;
    }
}
