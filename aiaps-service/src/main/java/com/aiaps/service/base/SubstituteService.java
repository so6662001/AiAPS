package com.aiaps.service.base;

import com.aiaps.domain.aps.ApsSubstituteLog;
import com.aiaps.domain.base.BasSubstituteRule;
import com.aiaps.domain.inventory.InvStock;
import com.aiaps.mapper.aps.ApsSubstituteLogMapper;
import com.aiaps.mapper.base.BasSubstituteRuleMapper;
import com.aiaps.mapper.inventory.InvStockMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubstituteService {

    private final BasSubstituteRuleMapper substituteRuleMapper;
    private final InvStockMapper invStockMapper;
    private final ApsSubstituteLogMapper substituteLogMapper;

    public List<SubstituteOption> findSubstitutes(Long materialId, String gradeCode,
                                                   BigDecimal requiredWidth, BigDecimal requiredThickness,
                                                   BigDecimal requiredQty) {
        List<SubstituteOption> options = new ArrayList<>();

        List<BasSubstituteRule> gradeRules = substituteRuleMapper.selectByTypeAndSource(
                "GRADE", null, gradeCode);
        for (BasSubstituteRule rule : gradeRules) {
            List<InvStock> stocks = invStockMapper.selectAvailable(
                    materialId, rule.getTargetGradeCode(), null);
            for (InvStock stock : stocks) {
                BigDecimal available = stock.getOnHandWeight()
                        .subtract(stock.getReservedWeight() != null ? stock.getReservedWeight() : BigDecimal.ZERO)
                        .subtract(stock.getQualityHoldWeight() != null ? stock.getQualityHoldWeight() : BigDecimal.ZERO);
                if (available.compareTo(BigDecimal.ZERO) > 0) {
                    SubstituteOption opt = buildOption(rule, stock);
                    options.add(opt);
                }
            }
        }

        if (requiredThickness != null) {
            List<BasSubstituteRule> thicknessRules = substituteRuleMapper.selectByTypeAndSource(
                    "THICKNESS", null, null);
            for (BasSubstituteRule rule : thicknessRules) {
                BigDecimal minThk = requiredThickness.add(
                        rule.getAllowOverMin() != null ? rule.getAllowOverMin() : BigDecimal.ZERO);
                BigDecimal maxThk = requiredThickness.add(
                        rule.getAllowOverMax() != null ? rule.getAllowOverMax() : BigDecimal.ZERO);

                LambdaQueryWrapper<InvStock> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(InvStock::getPrdtId, materialId);
                wrapper.ge(InvStock::getActualThickness, minThk);
                wrapper.le(InvStock::getActualThickness, maxThk);
                wrapper.apply("(on_hand_weight - reserved_weight - quality_hold_weight) > 0");

                List<InvStock> stocks = invStockMapper.selectList(wrapper);
                for (InvStock stock : stocks) {
                    SubstituteOption opt = buildOption(rule, stock);
                    options.add(opt);
                }
            }
        }

        if (requiredWidth != null) {
            List<BasSubstituteRule> widthRules = substituteRuleMapper.selectByTypeAndSource(
                    "WIDTH", null, null);
            for (BasSubstituteRule rule : widthRules) {
                BigDecimal minW = requiredWidth.add(
                        rule.getAllowOverMin() != null ? rule.getAllowOverMin() : BigDecimal.ZERO);
                BigDecimal maxW = requiredWidth.add(
                        rule.getAllowOverMax() != null ? rule.getAllowOverMax() : BigDecimal.ZERO);

                LambdaQueryWrapper<InvStock> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(InvStock::getPrdtId, materialId);
                wrapper.ge(InvStock::getActualWidth, minW);
                wrapper.le(InvStock::getActualWidth, maxW);
                wrapper.apply("(on_hand_weight - reserved_weight - quality_hold_weight) > 0");

                List<InvStock> stocks = invStockMapper.selectList(wrapper);
                for (InvStock stock : stocks) {
                    SubstituteOption opt = buildOption(rule, stock);
                    opt.setExtraScrapRate(rule.getScrapRateAdjust());
                    options.add(opt);
                }
            }
        }

        options.sort(Comparator
                .comparing((SubstituteOption o) -> o.isNeedApproval() ? 1 : 0)
                .thenComparing(SubstituteOption::getPriority)
                .thenComparing(o -> o.getExtraScrapRate() != null ? o.getExtraScrapRate() : BigDecimal.ZERO));

        return options;
    }

    @Transactional
    public void recordSubstitution(Long scheduleId, Long ruleId,
                                   Long originalMaterialId, String originalGrade,
                                   Long substituteMaterialId, String substituteGrade,
                                   Long substituteStockId, String substituteType,
                                   BigDecimal extraScrapRate) {
        ApsSubstituteLog logEntry = new ApsSubstituteLog();
        logEntry.setScheduleId(scheduleId);
        logEntry.setRuleId(ruleId);
        logEntry.setOriginalMaterialId(originalMaterialId);
        logEntry.setOriginalGradeCode(originalGrade);
        logEntry.setSubstituteMaterialId(substituteMaterialId);
        logEntry.setSubstituteGradeCode(substituteGrade);
        logEntry.setSubstituteStockId(substituteStockId);
        logEntry.setSubstituteType(substituteType);
        logEntry.setExtraScrapRate(extraScrapRate);
        logEntry.setApprovalStatus("PENDING");
        logEntry.setCreatedTime(new Date());
        substituteLogMapper.insert(logEntry);
    }

    private SubstituteOption buildOption(BasSubstituteRule rule, InvStock stock) {
        SubstituteOption opt = new SubstituteOption();
        opt.setRuleId(rule.getRuleId());
        opt.setStockItem(stock);
        opt.setSubstituteType(rule.getRuleType());
        opt.setExtraScrapRate(rule.getScrapRateAdjust());
        boolean needApproval = (rule.getNeedCustomerConfirm() != null && rule.getNeedCustomerConfirm())
                || (rule.getNeedTechConfirm() != null && rule.getNeedTechConfirm());
        opt.setNeedApproval(needApproval);
        opt.setPriority(rule.getPriority() != null ? rule.getPriority() : 50);
        return opt;
    }

    @Data
    public static class SubstituteOption {
        private Long ruleId;
        private InvStock stockItem;
        private String substituteType;
        private BigDecimal extraScrapRate;
        private boolean needApproval;
        private int priority;
    }
}
