package com.aiaps.service.aps;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.aps.ApsChangeLog;
import com.aiaps.domain.aps.ApsSchedule;
import com.aiaps.domain.aps.ApsScheduleOper;
import com.aiaps.domain.aps.ApsScheduleSnapshot;
import com.aiaps.domain.aps.ApsScheduleSnapshotDetail;
import com.aiaps.domain.mrp.MrpPlanOrder;
import com.aiaps.mapper.aps.ApsChangeLogMapper;
import com.aiaps.mapper.aps.ApsScheduleMapper;
import com.aiaps.mapper.aps.ApsScheduleOperMapper;
import com.aiaps.mapper.aps.ApsScheduleSnapshotDetailMapper;
import com.aiaps.mapper.aps.ApsScheduleSnapshotMapper;
import com.aiaps.mapper.mrp.MrpPlanOrderMapper;
import com.aiaps.mapper.system.SysApprovalMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleModificationService {

    private final ApsScheduleMapper scheduleMapper;
    private final ApsScheduleOperMapper scheduleOperMapper;
    private final ApsChangeLogMapper changeLogMapper;
    private final ApsScheduleSnapshotMapper snapshotMapper;
    private final ApsScheduleSnapshotDetailMapper snapshotDetailMapper;
    private final SysApprovalMapper approvalMapper;
    private final MrpPlanOrderMapper planOrderMapper;

    public Map<String, Object> previewImpact(Long scheduleId, String changeType, Map<String, Object> changeParams) {
        Map<String, Object> impact = new HashMap<>();
        ApsSchedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BizException("排产不存在");
        }

        List<Map<String, Object>> affectedTasks = new ArrayList<>();
        List<Map<String, Object>> dueDateRisks = new ArrayList<>();

        if ("QTY_CHANGE".equals(changeType)) {
            BigDecimal newWeight = new BigDecimal(changeParams.get("newWeight").toString());
            BigDecimal diff = newWeight.subtract(
                    schedule.getPlannedWeight() != null ? schedule.getPlannedWeight() : BigDecimal.ZERO);

            impact.put("weightChange", diff);
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                impact.put("timeImpact", "结束时间将延后");
                List<ApsScheduleOper> nextOpers = findSubsequentOpers(schedule);
                for (ApsScheduleOper op : nextOpers) {
                    Map<String, Object> affected = new HashMap<>();
                    affected.put("scheduleId", op.getScheduleId());
                    affected.put("impact", "可能需要后移");
                    affectedTasks.add(affected);
                }
            }
        } else if ("MOVE".equals(changeType) || "DATE_CHANGE".equals(changeType)) {
            affectedTasks = checkMoveConflicts(schedule, changeParams);
        }

        if (schedule.getPlanOrderId() != null) {
            MrpPlanOrder po = planOrderMapper.selectById(schedule.getPlanOrderId());
            if (po != null && po.getPlannedEndDate() != null) {
                dueDateRisks.add(Map.of("dueDate", po.getPlannedEndDate(), "risk", "需要评估"));
            }
        }

        impact.put("affectedTasks", affectedTasks);
        impact.put("dueDateRisks", dueDateRisks);
        impact.put("riskLevel", affectedTasks.size() > 3 ? "HIGH" : affectedTasks.isEmpty() ? "LOW" : "MEDIUM");
        return impact;
    }

    @Transactional
    public Long createSnapshot(List<Long> scheduleIds, String snapshotType, String description, String createdBy) {
        ApsScheduleSnapshot snapshot = new ApsScheduleSnapshot();
        snapshot.setSnapshotNo("SNAP-" + System.currentTimeMillis());
        snapshot.setSnapshotType(snapshotType);
        snapshot.setSnapshotScope(scheduleIds.size() == 1 ? "SINGLE" : "BATCH");
        snapshot.setDescription(description);
        snapshot.setCreatedBy(createdBy);
        snapshot.setCreatedTime(new Date());
        snapshotMapper.insert(snapshot);

        for (Long sid : scheduleIds) {
            ApsSchedule s = scheduleMapper.selectById(sid);
            if (s == null) continue;
            ApsScheduleSnapshotDetail detail = new ApsScheduleSnapshotDetail();
            detail.setSnapshotId(snapshot.getSnapshotId());
            detail.setScheduleId(s.getScheduleId());
            detail.setScheduleNo(s.getScheduleNo());
            detail.setPrdtId(s.getPrdtId());
            detail.setPlannedQty(s.getPlannedQty());
            detail.setPlannedWeight(s.getPlannedWeight());
            detail.setScheduleStart(s.getScheduleStart());
            detail.setScheduleEnd(s.getScheduleEnd());
            detail.setPriority(s.getPriority());
            detail.setScheduleStatus(s.getScheduleStatus());
            detail.setIsLocked(s.getIsLocked());
            detail.setOutputFlowType(s.getOutputFlowType());
            detail.setContractNo(s.getContractNo());
            detail.setGradeCode(s.getDemandGradeCode());
            snapshotDetailMapper.insert(detail);
        }

        return snapshot.getSnapshotId();
    }

    @Transactional
    public void rollbackToSnapshot(Long snapshotId) {
        List<ApsScheduleSnapshotDetail> details = snapshotDetailMapper.selectBySnapshotId(snapshotId);
        for (ApsScheduleSnapshotDetail d : details) {
            ApsSchedule schedule = scheduleMapper.selectById(d.getScheduleId());
            if (schedule == null) continue;
            schedule.setPlannedQty(d.getPlannedQty());
            schedule.setPlannedWeight(d.getPlannedWeight());
            schedule.setScheduleStart(d.getScheduleStart());
            schedule.setScheduleEnd(d.getScheduleEnd());
            schedule.setPriority(d.getPriority());
            schedule.setScheduleStatus(d.getScheduleStatus());
            schedule.setIsLocked(d.getIsLocked());
            schedule.setUpdatedTime(new Date());
            scheduleMapper.updateById(schedule);

            recordChangeLog("SCHEDULE", schedule.getScheduleId(), schedule.getScheduleNo(),
                    "ROLLBACK", "USER", "回滚到快照" + snapshotId, null);
        }
    }

    public void recordChangeLog(String scope, Long relatedId, String relatedNo,
                                String changeType, String source, String reason, String changedBy) {
        ApsChangeLog log = new ApsChangeLog();
        log.setChangeScope(scope);
        log.setRelatedId(relatedId);
        log.setRelatedNo(relatedNo);
        log.setChangeType(changeType);
        log.setChangeSource(source);
        log.setChangeReason(reason);
        log.setChangedBy(changedBy != null ? changedBy : "system");
        log.setChangedTime(new Date());
        changeLogMapper.insert(log);
    }

    public List<ApsChangeLog> getChangeHistory(Long relatedId, String scope) {
        return changeLogMapper.selectByRelated(relatedId, scope);
    }

    public List<Map<String, Object>> compareSnapshots(Long snapshotId) {
        List<ApsScheduleSnapshotDetail> snapDetails = snapshotDetailMapper.selectBySnapshotId(snapshotId);
        List<Map<String, Object>> diffs = new ArrayList<>();
        for (ApsScheduleSnapshotDetail d : snapDetails) {
            ApsSchedule current = scheduleMapper.selectById(d.getScheduleId());
            if (current == null) continue;
            Map<String, Object> diff = new HashMap<>();
            diff.put("scheduleId", d.getScheduleId());
            diff.put("scheduleNo", d.getScheduleNo());
            boolean changed = false;
            if (!Objects.equals(d.getPlannedWeight(), current.getPlannedWeight())) {
                diff.put("weightBefore", d.getPlannedWeight());
                diff.put("weightAfter", current.getPlannedWeight());
                changed = true;
            }
            if (!Objects.equals(d.getScheduleStart(), current.getScheduleStart())) {
                diff.put("startBefore", d.getScheduleStart());
                diff.put("startAfter", current.getScheduleStart());
                changed = true;
            }
            if (!Objects.equals(d.getScheduleEnd(), current.getScheduleEnd())) {
                diff.put("endBefore", d.getScheduleEnd());
                diff.put("endAfter", current.getScheduleEnd());
                changed = true;
            }
            if (!Objects.equals(d.getScheduleStatus(), current.getScheduleStatus())) {
                diff.put("statusBefore", d.getScheduleStatus());
                diff.put("statusAfter", current.getScheduleStatus());
                changed = true;
            }
            if (changed) {
                diffs.add(diff);
            }
        }
        return diffs;
    }

    private List<ApsScheduleOper> findSubsequentOpers(ApsSchedule schedule) {
        if (schedule.getScheduleEnd() == null) return Collections.emptyList();
        List<ApsScheduleOper> opers = scheduleOperMapper.selectList(
                new LambdaQueryWrapper<ApsScheduleOper>()
                        .ge(ApsScheduleOper::getOperStart, schedule.getScheduleStart())
                        .ne(ApsScheduleOper::getScheduleId, schedule.getScheduleId())
                        .orderByAsc(ApsScheduleOper::getOperStart));
        return opers.stream().limit(5).collect(Collectors.toList());
    }

    private List<Map<String, Object>> checkMoveConflicts(ApsSchedule schedule, Map<String, Object> params) {
        return new ArrayList<>();
    }
}
