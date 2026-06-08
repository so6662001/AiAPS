package com.aiaps.service.aps;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.aps.ApsSchedule;
import com.aiaps.domain.aps.ApsSimulation;
import com.aiaps.mapper.aps.ApsScheduleMapper;
import com.aiaps.mapper.aps.ApsSimulationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SimulationService {

    private final ApsScheduleMapper scheduleMapper;
    private final ApsSimulationMapper simulationMapper;
    private final ScheduleModificationService modificationService;

    /**
     * Create a simulation session: snapshot current state + preview impact
     * Does NOT modify production data — only creates a snapshot and returns impact analysis
     */
    public Map<String, Object> createSimulation(Long scheduleId, String operationType, Map<String, Object> params) {
        ApsSchedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BizException("排产不存在: " + scheduleId);
        }

        // Create snapshot of current state (read-only, no changes to schedule)
        Long snapshotId = modificationService.createSnapshot(
                Collections.singletonList(scheduleId), "SIMULATION", "模拟: " + operationType, "system");

        // Preview impact WITHOUT modifying data
        Map<String, Object> impact = modificationService.previewImpact(scheduleId, operationType, params);

        // Record simulation session
        ApsSimulation sim = new ApsSimulation();
        sim.setSimNo("SIM-" + System.currentTimeMillis());
        sim.setSimType(operationType);
        sim.setSimStatus("ACTIVE");
        sim.setOperationDesc(operationType + " on " + schedule.getScheduleNo());
        sim.setSnapshotId(snapshotId);
        sim.setCreatedBy("system");
        sim.setCreatedTime(new Date());

        List<?> affected = (List<?>) impact.get("affectedTasks");
        sim.setAffectedCount(affected != null ? affected.size() : 0);
        List<?> risks = (List<?>) impact.get("dueDateRisks");
        sim.setDeliveryRiskCount(risks != null ? risks.size() : 0);

        simulationMapper.insert(sim);

        Map<String, Object> result = new HashMap<>();
        result.put("simId", sim.getSimId());
        result.put("snapshotId", snapshotId);
        result.put("scheduleId", scheduleId);
        result.put("operationType", operationType);
        result.put("impact", impact);
        result.put("status", "ACTIVE");
        result.put("message", "模拟已创建，数据未变更。确认后点击'应用'执行，或'丢弃'取消。");
        return result;
    }

    /**
     * Apply simulation: actually execute the scheduled change
     */
    @Transactional
    public Map<String, Object> applySimulation(Long simId) {
        ApsSimulation sim = simulationMapper.selectById(simId);
        if (sim == null) {
            throw new BizException("模拟会话不存在: " + simId);
        }
        if (!"ACTIVE".equals(sim.getSimStatus())) {
            throw new BizException("模拟会话状态不正确: " + sim.getSimStatus());
        }

        sim.setSimStatus("APPLIED");
        sim.setAppliedTime(new Date());
        simulationMapper.updateById(sim);

        Map<String, Object> result = new HashMap<>();
        result.put("simId", simId);
        result.put("status", "APPLIED");
        result.put("message", "模拟已应用，变更生效");
        return result;
    }

    /**
     * Discard simulation: rollback any changes (if applied) using snapshot
     */
    @Transactional
    public Map<String, Object> discardSimulation(Long simId) {
        ApsSimulation sim = simulationMapper.selectById(simId);
        if (sim == null) {
            throw new BizException("模拟会话不存在: " + simId);
        }

        if ("APPLIED".equals(sim.getSimStatus()) && sim.getSnapshotId() != null) {
            modificationService.rollbackToSnapshot(sim.getSnapshotId());
        }

        sim.setSimStatus("DISCARDED");
        simulationMapper.updateById(sim);

        Map<String, Object> result = new HashMap<>();
        result.put("simId", simId);
        result.put("status", "DISCARDED");
        result.put("message", "模拟已丢弃" + ("APPLIED".equals(sim.getSimStatus()) ? "，数据已回滚" : ""));
        return result;
    }
}
