package com.aiaps.web.controller.mrp;

import com.aiaps.common.result.R;
import com.aiaps.service.mrp.ReverseMatchEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/v1/reverse-match")
@RequiredArgsConstructor
@Validated
public class ReverseMatchController {

    private final ReverseMatchEngine reverseMatchEngine;

    @PostMapping
    public R<List<ReverseMatchEngine.MatchResult>> reverseMatch(@RequestBody List<Long> rawStockIds) {
        return R.ok(reverseMatchEngine.reverseMatch(rawStockIds));
    }
}
