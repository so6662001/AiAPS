package com.aiaps.web.controller.aps;

import com.aiaps.common.result.R;
import com.aiaps.service.aps.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    @PostMapping
    public R<Map<String, Object>> createSimulation(
            @RequestParam Long scheduleId,
            @RequestParam String operationType,
            @RequestBody(required = false) Map<String, Object> params) {
        return R.ok(simulationService.createSimulation(scheduleId, operationType, params));
    }

    @PostMapping("/{snapshotId}/apply")
    public R<Map<String, Object>> applySimulation(@PathVariable Long snapshotId) {
        return R.ok(simulationService.applySimulation(snapshotId));
    }

    @PostMapping("/{snapshotId}/discard")
    public R<Map<String, Object>> discardSimulation(@PathVariable Long snapshotId) {
        return R.ok(simulationService.discardSimulation(snapshotId));
    }
}
