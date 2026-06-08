package com.aiaps.web.controller.base;

import com.aiaps.common.result.PageResult;
import com.aiaps.common.result.R;
import com.aiaps.domain.base.BasSubstituteRule;
import com.aiaps.mapper.base.BasSubstituteRuleMapper;
import com.aiaps.service.base.SubstituteService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

@Validated
@RestController
@RequestMapping("/v1/substitute")
@RequiredArgsConstructor
public class SubstituteController {

    private final SubstituteService substituteService;
    private final BasSubstituteRuleMapper substituteRuleMapper;

    @PostMapping("/match")
    public R<List<SubstituteService.SubstituteOption>> findSubstitutes(
            @RequestBody SubstituteMatchRequest request) {
        return R.ok(substituteService.findSubstitutes(
                request.getMaterialId(),
                request.getGradeCode(),
                request.getRequiredWidth(),
                request.getRequiredThickness(),
                request.getRequiredQty()));
    }

    @GetMapping("/rules")
    public R<PageResult<BasSubstituteRule>> listRules(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String ruleType) {
        LambdaQueryWrapper<BasSubstituteRule> wrapper = new LambdaQueryWrapper<>();
        if (ruleType != null && !ruleType.isEmpty()) {
            wrapper.eq(BasSubstituteRule::getRuleType, ruleType);
        }
        wrapper.orderByAsc(BasSubstituteRule::getPriority);

        Page<BasSubstituteRule> page = substituteRuleMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);

        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    @PostMapping("/rules")
    public R<Void> createRule(@Valid @RequestBody BasSubstituteRule rule) {
        if (rule.getIsActive() == null) {
            rule.setIsActive(true);
        }
        substituteRuleMapper.insert(rule);
        return R.ok();
    }

    @PutMapping("/rules/{id}")
    public R<Void> updateRule(@PathVariable Long id, @Valid @RequestBody BasSubstituteRule rule) {
        rule.setRuleId(id);
        substituteRuleMapper.updateById(rule);
        return R.ok();
    }

    @Data
    public static class SubstituteMatchRequest {
        private Long materialId;
        private String gradeCode;
        private BigDecimal requiredWidth;
        private BigDecimal requiredThickness;
        private BigDecimal requiredQty;
    }
}
