package com.aiaps.web.controller.base;

import com.aiaps.common.result.R;
import com.aiaps.domain.base.*;
import com.aiaps.mapper.base.BasGradeCrossSubstituteMapper;
import com.aiaps.mapper.base.BasGradeHierarchyMapper;
import com.aiaps.mapper.base.BasOriginExchangeGroupMapper;
import com.aiaps.mapper.base.BasOriginTierMapper;
import com.aiaps.mapper.base.BasCustomerMaterialPrefMapper;
import com.aiaps.service.base.GradeOriginSubstituteService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/v1/grade-origin")
@RequiredArgsConstructor
public class GradeOriginController {

    private final GradeOriginSubstituteService gradeOriginSubstituteService;
    private final BasGradeHierarchyMapper gradeHierarchyMapper;
    private final BasGradeCrossSubstituteMapper gradeCrossSubstituteMapper;
    private final BasOriginTierMapper originTierMapper;
    private final BasOriginExchangeGroupMapper originExchangeGroupMapper;
    private final BasCustomerMaterialPrefMapper customerMaterialPrefMapper;

    @PostMapping("/match")
    public R<List<GradeOriginSubstituteService.SubstituteOption>> findSubstitutes(
            @RequestBody GradeOriginMatchRequest request) {
        return R.ok(gradeOriginSubstituteService.findSubstitutes(
                request.getPrdtId(),
                request.getGradeCode(),
                request.getOriginCode(),
                request.getRequiredWeight(),
                request.getCustomerCode()));
    }

    @GetMapping("/grade/hierarchy")
    public R<List<BasGradeHierarchy>> listAllHierarchies() {
        LambdaQueryWrapper<BasGradeHierarchy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BasGradeHierarchy::getIsActive, true);
        wrapper.orderByAsc(BasGradeHierarchy::getGradeFamily);
        wrapper.orderByAsc(BasGradeHierarchy::getGradeLevel);
        return R.ok(gradeHierarchyMapper.selectList(wrapper));
    }

    @GetMapping("/grade/hierarchy/{family}")
    public R<List<BasGradeHierarchy>> getGradeHierarchyByFamily(@PathVariable String family) {
        return R.ok(gradeOriginSubstituteService.getGradeHierarchy(family));
    }

    @GetMapping("/grade/cross")
    public R<List<BasGradeCrossSubstitute>> listCrossSubstitutes() {
        LambdaQueryWrapper<BasGradeCrossSubstitute> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BasGradeCrossSubstitute::getIsActive, true);
        return R.ok(gradeCrossSubstituteMapper.selectList(wrapper));
    }

    @GetMapping("/origin/tier")
    public R<List<BasOriginTier>> listOriginTiers() {
        return R.ok(gradeOriginSubstituteService.getOriginTiers());
    }

    @GetMapping("/origin/exchange-group")
    public R<List<BasOriginExchangeGroup>> listExchangeGroups() {
        return R.ok(originExchangeGroupMapper.selectList(null));
    }

    @GetMapping("/customer/{code}/pref")
    public R<BasCustomerMaterialPref> getCustomerPref(@PathVariable String code) {
        return R.ok(gradeOriginSubstituteService.getCustomerPref(code));
    }

    @PutMapping("/customer/{code}/pref")
    public R<Void> updateCustomerPref(@PathVariable String code,
                                       @RequestBody BasCustomerMaterialPref pref) {
        pref.setCustomerCode(code);
        BasCustomerMaterialPref existing = customerMaterialPrefMapper.selectByCustomer(code);
        if (existing != null) {
            pref.setPrefId(existing.getPrefId());
            customerMaterialPrefMapper.updateById(pref);
        } else {
            if (pref.getIsActive() == null) {
                pref.setIsActive(true);
            }
            customerMaterialPrefMapper.insert(pref);
        }
        return R.ok();
    }

    @Data
    public static class GradeOriginMatchRequest {
        private Long prdtId;
        private String gradeCode;
        private String originCode;
        private BigDecimal requiredWeight;
        private String customerCode;
    }
}
