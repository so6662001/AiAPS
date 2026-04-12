package com.aiaps.service.base;

import com.aiaps.domain.base.*;
import com.aiaps.domain.inventory.InvStock;
import com.aiaps.mapper.base.*;
import com.aiaps.mapper.inventory.InvStockMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GradeOriginSubstituteService {

    private final BasGradeHierarchyMapper gradeHierarchyMapper;
    private final BasGradeCrossSubstituteMapper gradeCrossSubstituteMapper;
    private final BasOriginTierMapper originTierMapper;
    private final BasOriginExchangeGroupMapper originExchangeGroupMapper;
    private final BasOriginExchangeMemberMapper originExchangeMemberMapper;
    private final BasCustomerMaterialPrefMapper customerMaterialPrefMapper;
    private final InvStockMapper invStockMapper;

    @Data
    public static class SubstituteOption {
        private Long stockId;
        private String substituteGrade;
        private String substituteOrigin;
        private int gradeLevelDiff;
        private int originTierDiff;
        private boolean isCrossFamily;
        private boolean isInExchangeGroup;
        private BigDecimal availableWeight;
        private BigDecimal matchScore;
        private boolean autoApproved;
        private boolean needTechConfirm;
        private boolean needCustomerConfirm;
        private String approvalReason;
    }

    public List<SubstituteOption> findSubstitutes(Long prdtId, String gradeCode, String originCode,
                                                   BigDecimal requiredWeight, String customerCode) {
        BasCustomerMaterialPref pref = getCustomerPref(customerCode);

        BasGradeHierarchy sourceGrade = gradeHierarchyMapper.selectByGradeCode(gradeCode);
        BasOriginTier sourceTier = originTierMapper.selectByOriginCode(originCode);

        List<InvStock> allStocks = invStockMapper.selectByMaterialWithAnyGradeOrigin(prdtId);

        List<SubstituteOption> options = new ArrayList<>();

        for (InvStock stock : allStocks) {
            BigDecimal available = stock.getOnHandWeight()
                    .subtract(stock.getReservedWeight() != null ? stock.getReservedWeight() : BigDecimal.ZERO)
                    .subtract(stock.getQualityHoldWeight() != null ? stock.getQualityHoldWeight() : BigDecimal.ZERO);
            if (available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            String stockGrade = stock.getPatName();
            String stockOrigin = stock.getPaName();

            GradeEvaluation gradeEval = evaluateGrade(gradeCode, stockGrade, sourceGrade, pref);
            if (!gradeEval.allowed) {
                continue;
            }

            OriginEvaluation originEval = evaluateOrigin(originCode, stockOrigin, sourceTier, pref);
            if (!originEval.allowed) {
                continue;
            }

            SubstituteOption opt = new SubstituteOption();
            opt.setStockId(stock.getStockId());
            opt.setSubstituteGrade(stockGrade);
            opt.setSubstituteOrigin(stockOrigin);
            opt.setGradeLevelDiff(gradeEval.levelDiff);
            opt.setOriginTierDiff(originEval.tierDiff);
            opt.setCrossFamily(gradeEval.crossFamily);
            opt.setInExchangeGroup(originEval.inExchangeGroup);
            opt.setAvailableWeight(available);

            boolean techConfirm = gradeEval.needTechConfirm || originEval.needTechConfirm;
            boolean custConfirm = gradeEval.needCustomerConfirm || originEval.needCustomerConfirm;
            boolean autoApproved = !techConfirm && !custConfirm;

            if (pref.getGradeAutoApprove() != null && pref.getGradeAutoApprove()
                    && pref.getOriginAutoApprove() != null && pref.getOriginAutoApprove()) {
                autoApproved = true;
                techConfirm = false;
                custConfirm = false;
            }

            opt.setAutoApproved(autoApproved);
            opt.setNeedTechConfirm(techConfirm);
            opt.setNeedCustomerConfirm(custConfirm);
            opt.setApprovalReason(buildApprovalReason(gradeEval, originEval));

            BigDecimal gradeScore = calculateGradeScore(gradeEval);
            BigDecimal originScore = calculateOriginScore(originEval);
            BigDecimal approvalScore = autoApproved ? new BigDecimal("100")
                    : (techConfirm && custConfirm ? BigDecimal.ZERO : new BigDecimal("50"));
            BigDecimal qtyScore = calculateQtyScore(available, requiredWeight);

            BigDecimal matchScore = gradeScore.multiply(new BigDecimal("0.40"))
                    .add(originScore.multiply(new BigDecimal("0.30")))
                    .add(approvalScore.multiply(new BigDecimal("0.20")))
                    .add(qtyScore.multiply(new BigDecimal("0.10")))
                    .setScale(2, RoundingMode.HALF_UP);
            opt.setMatchScore(matchScore);

            options.add(opt);
        }

        options.sort(Comparator.comparing(SubstituteOption::getMatchScore).reversed());

        return options;
    }

    public List<BasGradeHierarchy> getGradeHierarchy(String family) {
        return gradeHierarchyMapper.selectByFamily(family);
    }

    public List<BasOriginTier> getOriginTiers() {
        LambdaQueryWrapper<BasOriginTier> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BasOriginTier::getIsActive, true);
        wrapper.orderByAsc(BasOriginTier::getQualityTier);
        return originTierMapper.selectList(wrapper);
    }

    public BasCustomerMaterialPref getCustomerPref(String customerCode) {
        if (customerCode != null && !customerCode.isEmpty()) {
            BasCustomerMaterialPref pref = customerMaterialPrefMapper.selectByCustomer(customerCode);
            if (pref != null) {
                return pref;
            }
        }
        BasCustomerMaterialPref defaultPref = new BasCustomerMaterialPref();
        defaultPref.setGradeSubstitutePolicy("SAME_FAMILY_UP");
        defaultPref.setGradeAutoApprove(false);
        defaultPref.setOriginSubstitutePolicy("TIER_UP");
        defaultPref.setOriginAutoApprove(false);
        defaultPref.setIsActive(true);
        return defaultPref;
    }

    public boolean isGradeSubstituteAllowed(String sourceGrade, String targetGrade, String customerCode) {
        if (sourceGrade.equals(targetGrade)) {
            return true;
        }
        BasCustomerMaterialPref pref = getCustomerPref(customerCode);
        BasGradeHierarchy sourceHierarchy = gradeHierarchyMapper.selectByGradeCode(sourceGrade);
        GradeEvaluation eval = evaluateGrade(sourceGrade, targetGrade, sourceHierarchy, pref);
        return eval.allowed;
    }

    private static class GradeEvaluation {
        boolean allowed;
        int levelDiff;
        boolean crossFamily;
        boolean needTechConfirm;
        boolean needCustomerConfirm;
        String reason;
    }

    private GradeEvaluation evaluateGrade(String sourceGrade, String targetGrade,
                                           BasGradeHierarchy sourceHierarchy, BasCustomerMaterialPref pref) {
        GradeEvaluation eval = new GradeEvaluation();
        String policy = pref.getGradeSubstitutePolicy();

        if (sourceGrade.equals(targetGrade)) {
            eval.allowed = true;
            eval.levelDiff = 0;
            eval.crossFamily = false;
            eval.reason = "Same grade";
            return eval;
        }

        if ("STRICT".equals(policy)) {
            eval.allowed = false;
            eval.reason = "Strict policy: no grade substitution allowed";
            return eval;
        }

        BasGradeHierarchy targetHierarchy = gradeHierarchyMapper.selectByGradeCode(targetGrade);

        if (sourceHierarchy != null && targetHierarchy != null
                && sourceHierarchy.getGradeFamily().equals(targetHierarchy.getGradeFamily())) {
            eval.crossFamily = false;
            eval.levelDiff = targetHierarchy.getGradeLevel() - sourceHierarchy.getGradeLevel();

            if (eval.levelDiff > 0) {
                eval.allowed = "SAME_FAMILY_UP".equals(policy) || "SAME_FAMILY_ANY".equals(policy)
                        || "CROSS_FAMILY".equals(policy) || "ANY".equals(policy);
                eval.reason = "Same family, higher level (+" + eval.levelDiff + ")";
            } else {
                eval.allowed = "SAME_FAMILY_ANY".equals(policy)
                        || "CROSS_FAMILY".equals(policy) || "ANY".equals(policy);
                eval.needTechConfirm = true;
                eval.reason = "Same family, lower level (" + eval.levelDiff + ")";
            }
        } else {
            eval.crossFamily = true;
            if ("CROSS_FAMILY".equals(policy) || "ANY".equals(policy)) {
                BasGradeCrossSubstitute crossRule = gradeCrossSubstituteMapper.selectCross(sourceGrade, targetGrade);
                if (crossRule != null) {
                    eval.allowed = true;
                    eval.needTechConfirm = crossRule.getNeedTechConfirm() != null && crossRule.getNeedTechConfirm();
                    eval.needCustomerConfirm = crossRule.getNeedCustomerConfirm() != null && crossRule.getNeedCustomerConfirm();
                    eval.reason = "Cross-family substitution (" + crossRule.getSubstituteDirection() + ")";
                    if (sourceHierarchy != null && targetHierarchy != null) {
                        eval.levelDiff = targetHierarchy.getGradeLevel() - sourceHierarchy.getGradeLevel();
                    }
                } else {
                    eval.allowed = "ANY".equals(policy);
                    eval.needTechConfirm = true;
                    eval.needCustomerConfirm = true;
                    eval.reason = "Cross-family without predefined rule";
                }
            } else {
                eval.allowed = false;
                eval.reason = "Cross-family not allowed by policy";
            }
        }

        return eval;
    }

    private static class OriginEvaluation {
        boolean allowed;
        int tierDiff;
        boolean inExchangeGroup;
        boolean needTechConfirm;
        boolean needCustomerConfirm;
        String reason;
    }

    private OriginEvaluation evaluateOrigin(String sourceOrigin, String targetOrigin,
                                             BasOriginTier sourceTier, BasCustomerMaterialPref pref) {
        OriginEvaluation eval = new OriginEvaluation();
        String policy = pref.getOriginSubstitutePolicy();

        if (sourceOrigin != null && sourceOrigin.equals(targetOrigin)) {
            eval.allowed = true;
            eval.tierDiff = 0;
            eval.reason = "Same origin";
            return eval;
        }

        if ("STRICT".equals(policy)) {
            eval.allowed = false;
            eval.reason = "Strict policy: no origin substitution allowed";
            return eval;
        }

        boolean inGroup = false;
        if (sourceOrigin != null && targetOrigin != null) {
            inGroup = originExchangeGroupMapper.isInSameGroup(sourceOrigin, targetOrigin);
        }
        eval.inExchangeGroup = inGroup;

        if (inGroup) {
            eval.allowed = true;
            eval.tierDiff = 0;
            eval.reason = "In same exchange group";
            return eval;
        }

        BasOriginTier targetTier = originTierMapper.selectByOriginCode(targetOrigin);

        if (sourceTier != null && targetTier != null) {
            eval.tierDiff = sourceTier.getQualityTier() - targetTier.getQualityTier();

            if (eval.tierDiff > 0) {
                eval.allowed = "TIER_UP".equals(policy) || "SAME_TIER".equals(policy) || "ANY".equals(policy);
                eval.reason = "Better origin tier (+" + eval.tierDiff + ")";
            } else if (eval.tierDiff == 0) {
                eval.allowed = "SAME_TIER".equals(policy) || "TIER_UP".equals(policy) || "ANY".equals(policy);
                eval.reason = "Same origin tier";
            } else {
                eval.allowed = "ANY".equals(policy);
                eval.needTechConfirm = true;
                eval.needCustomerConfirm = true;
                eval.reason = "Lower origin tier (" + eval.tierDiff + ")";
            }
        } else {
            eval.allowed = "ANY".equals(policy) || "WHITELIST".equals(policy);
            eval.needTechConfirm = true;
            eval.reason = "Origin tier info not available";
        }

        return eval;
    }

    private BigDecimal calculateGradeScore(GradeEvaluation eval) {
        if (!eval.crossFamily && eval.levelDiff == 0) {
            return new BigDecimal("100");
        }
        if (!eval.crossFamily && eval.levelDiff > 0) {
            return new BigDecimal("80").subtract(new BigDecimal(eval.levelDiff * 5));
        }
        if (!eval.crossFamily && eval.levelDiff < 0) {
            return new BigDecimal("50").add(new BigDecimal(eval.levelDiff * 10));
        }
        return new BigDecimal("30");
    }

    private BigDecimal calculateOriginScore(OriginEvaluation eval) {
        if (eval.tierDiff == 0 && !eval.inExchangeGroup) {
            return new BigDecimal("100");
        }
        if (eval.inExchangeGroup) {
            return new BigDecimal("90");
        }
        if (eval.tierDiff > 0) {
            return new BigDecimal("80");
        }
        if (eval.tierDiff < 0) {
            return new BigDecimal("40").add(new BigDecimal(eval.tierDiff * 10));
        }
        return new BigDecimal("50");
    }

    private BigDecimal calculateQtyScore(BigDecimal available, BigDecimal required) {
        if (required == null || required.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("100");
        }
        if (available.compareTo(required) >= 0) {
            return new BigDecimal("100");
        }
        return available.multiply(new BigDecimal("100"))
                .divide(required, 0, RoundingMode.HALF_UP);
    }

    private String buildApprovalReason(GradeEvaluation gradeEval, OriginEvaluation originEval) {
        StringBuilder sb = new StringBuilder();
        if (gradeEval.reason != null) {
            sb.append("Grade: ").append(gradeEval.reason);
        }
        if (originEval.reason != null) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append("Origin: ").append(originEval.reason);
        }
        return sb.toString();
    }
}
