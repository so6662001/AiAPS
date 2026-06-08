package com.aiaps.web.controller.trace;

import com.aiaps.common.result.R;
import com.aiaps.service.trace.TraceabilityService;
import com.aiaps.service.trace.TraceabilityService.TraceNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/trace")
@RequiredArgsConstructor
public class TraceController {

    private final TraceabilityService traceabilityService;

    @GetMapping("/barcode/{itemBarcode}")
    public R<TraceNode> traceByBarcode(@PathVariable String itemBarcode) {
        return R.ok(traceabilityService.traceByBarcode(itemBarcode));
    }

    @GetMapping("/card/{cardNo}")
    public R<TraceNode> traceByCard(@PathVariable String cardNo) {
        TraceNode forward = traceabilityService.traceForward(cardNo);
        TraceNode backward = traceabilityService.traceBackward(cardNo);
        forward.setParents(backward.getParents());
        return R.ok(forward);
    }

    @GetMapping("/contract/{contractNo}")
    public R<List<TraceNode>> traceByContract(@PathVariable String contractNo) {
        return R.ok(traceabilityService.traceByContract(contractNo));
    }

    @GetMapping("/forward/{cardNo}")
    public R<TraceNode> traceForward(@PathVariable String cardNo) {
        return R.ok(traceabilityService.traceForward(cardNo));
    }

    @GetMapping("/backward/{cardNo}")
    public R<TraceNode> traceBackward(@PathVariable String cardNo) {
        return R.ok(traceabilityService.traceBackward(cardNo));
    }
}
