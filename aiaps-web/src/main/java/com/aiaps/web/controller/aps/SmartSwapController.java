package com.aiaps.web.controller.aps;

import com.aiaps.common.result.R;
import com.aiaps.service.aps.SmartSwapService;
import com.aiaps.service.aps.SmartSwapService.MismatchDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/smart-swap")
@RequiredArgsConstructor
public class SmartSwapController {

    private final SmartSwapService smartSwapService;

    @PostMapping("/detect")
    public R<MismatchDecision> detect(
            @RequestParam Long scheduleId,
            @RequestParam Long actualStockId) {
        return R.ok(smartSwapService.detectAndDecide(scheduleId, actualStockId));
    }

    @PostMapping("/execute-swap")
    public R<Map<String, Object>> executeSwap(
            @RequestParam Long currentScheduleId,
            @RequestParam Long matchingScheduleId) {
        return R.ok(smartSwapService.executeOrderSwap(currentScheduleId, matchingScheduleId));
    }
}
