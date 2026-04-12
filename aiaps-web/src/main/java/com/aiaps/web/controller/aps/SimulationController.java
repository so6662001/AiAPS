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
    public R<Map<String, Object>> create(@RequestBody Map<String, Object> params) {
        Long scheduleId = Long.valueOf(params.get("scheduleId").toString());
        String operationType = (String) params.get("operationType");
        return R.ok(simulationService.createSimulation(scheduleId, operationType, params));
    }

    @PostMapping("/{simId}/apply")
    public R<Map<String, Object>> apply(@PathVariable Long simId) {
        return R.ok(simulationService.applySimulation(simId));
    }

    @PostMapping("/{simId}/discard")
    public R<Map<String, Object>> discard(@PathVariable Long simId) {
        return R.ok(simulationService.discardSimulation(simId));
    }
}
