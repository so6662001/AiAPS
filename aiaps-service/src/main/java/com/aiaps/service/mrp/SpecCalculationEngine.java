package com.aiaps.service.mrp;

import com.aiaps.domain.base.BasCategoryBom;
import com.aiaps.domain.base.BasMaterial;
import com.aiaps.domain.base.BasSpecFormula;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * 规格推算引擎
 * 根据产出物料的规格参数，计算所需原料的规格区间和用量
 */
@Slf4j
@Component
public class SpecCalculationEngine {

    @Data
    public static class RawMaterialSpec {
        private String childCategory;
        private BigDecimal widthMin;
        private BigDecimal widthMax;
        private BigDecimal thicknessMin;
        private BigDecimal thicknessMax;
        private BigDecimal lengthMin;
        private BigDecimal lengthMax;
        private BigDecimal weightPerUnit;
        private BigDecimal scrapRate;
    }

    public RawMaterialSpec calculate(BasMaterial product, BigDecimal quantity,
                                     BasSpecFormula formula, BasCategoryBom categoryBom) {
        RawMaterialSpec spec = new RawMaterialSpec();
        spec.setChildCategory(formula.getChildCategory());

        Map<String, Double> params = buildParamContext(product);

        if (formula.getWidthFormula() != null) {
            BigDecimal calcWidth = evalFormula(formula.getWidthFormula(), params);
            BigDecimal tolMin = formula.getWidthToleranceMin() != null ? formula.getWidthToleranceMin() : BigDecimal.ZERO;
            BigDecimal tolMax = formula.getWidthToleranceMax() != null ? formula.getWidthToleranceMax() : BigDecimal.ZERO;
            spec.setWidthMin(calcWidth.add(tolMin));
            spec.setWidthMax(calcWidth.add(tolMax));
        }

        if (formula.getThicknessFormula() != null) {
            BigDecimal calcThk = evalFormula(formula.getThicknessFormula(), params);
            BigDecimal tolMin = formula.getThicknessTolMin() != null ? formula.getThicknessTolMin() : BigDecimal.ZERO;
            BigDecimal tolMax = formula.getThicknessTolMax() != null ? formula.getThicknessTolMax() : BigDecimal.ZERO;
            spec.setThicknessMin(calcThk.add(tolMin));
            spec.setThicknessMax(calcThk.add(tolMax));
        }

        BigDecimal yieldRate = (categoryBom != null && categoryBom.getYieldRate() != null)
                ? categoryBom.getYieldRate() : BigDecimal.ONE;
        BigDecimal scrapRate = (categoryBom != null && categoryBom.getScrapRate() != null)
                ? categoryBom.getScrapRate() : BigDecimal.ZERO;
        BigDecimal fixedScrap = (categoryBom != null && categoryBom.getFixedScrapQty() != null)
                ? categoryBom.getFixedScrapQty() : BigDecimal.ZERO;

        spec.setScrapRate(scrapRate);
        spec.setWeightPerUnit(
                quantity.divide(yieldRate, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.ONE.add(scrapRate))
                        .add(fixedScrap)
        );

        return spec;
    }

    private Map<String, Double> buildParamContext(BasMaterial product) {
        Map<String, Double> params = new HashMap<>();
        putIfNotNull(params, "side_a", product.getSideA());
        putIfNotNull(params, "side_b", product.getSideB());
        putIfNotNull(params, "thickness", product.getThickness());
        putIfNotNull(params, "outer_diameter", product.getOuterDiameter());
        putIfNotNull(params, "height", product.getHeight());
        putIfNotNull(params, "width", product.getWidth());
        putIfNotNull(params, "corner_radius", product.getCornerRadius());
        putIfNotNull(params, "flange_width", product.getFlangeWidth());
        putIfNotNull(params, "web_thickness", product.getWebThickness());
        putIfNotNull(params, "flange_thickness", product.getFlangeThickness());

        if (!params.containsKey("corner_radius") && params.containsKey("thickness")) {
            params.put("corner_radius", params.get("thickness") * 2.0);
        }
        return params;
    }

    private void putIfNotNull(Map<String, Double> map, String key, BigDecimal value) {
        if (value != null) {
            map.put(key, value.doubleValue());
        }
    }

    private BigDecimal evalFormula(String formulaStr, Map<String, Double> params) {
        try {
            String expr = formulaStr;
            for (Map.Entry<String, Double> entry : params.entrySet()) {
                expr = expr.replace("${" + entry.getKey() + "}", entry.getValue().toString());
            }

            Expression expression = new ExpressionBuilder(expr).build();
            double result = expression.evaluate();
            return BigDecimal.valueOf(result).setScale(3, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.error("公式计算失败: formula={}, params={}", formulaStr, params, e);
            throw new RuntimeException("规格推算公式计算失败: " + formulaStr, e);
        }
    }
}
