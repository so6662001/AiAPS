package com.aiaps.service.util;

import org.springframework.stereotype.Component;

@Component
public class SpecDisplayBuilder {

    /**
     * Build full spec display text
     * Example: "125×125×2.75 × 9m Q235B 鞍钢"
     */
    public String build(String specDesc, String lengthDisplay, String gradeCode, String originCode) {
        StringBuilder sb = new StringBuilder();
        if (specDesc != null && !specDesc.isEmpty()) {
            sb.append(specDesc);
        }
        if (lengthDisplay != null && !lengthDisplay.isEmpty()) {
            sb.append(" × ").append(lengthDisplay);
        }
        if (gradeCode != null && !gradeCode.isEmpty()) {
            sb.append(" ").append(gradeCode);
        }
        if (originCode != null && !originCode.isEmpty()) {
            sb.append(" ").append(originCode);
        }
        return sb.toString();
    }

    /**
     * Build for printing (with category name prefix)
     * Example: "方管 125×125×2.75 × 9m Q235B"
     */
    public String buildForPrint(String categoryName, String specDesc, String lengthDisplay, String gradeCode) {
        StringBuilder sb = new StringBuilder();
        if (categoryName != null && !categoryName.isEmpty()) {
            sb.append(categoryName).append(" ");
        }
        sb.append(build(specDesc, lengthDisplay, gradeCode, null));
        return sb.toString();
    }

    /**
     * Format length for display
     * 9000mm → "9m", 6000mm → "6m", null → null
     */
    public String formatLength(java.math.BigDecimal lengthMm) {
        if (lengthMm == null) return null;
        double m = lengthMm.doubleValue() / 1000.0;
        if (m == Math.floor(m)) {
            return String.valueOf((int) m) + "m";
        }
        return String.format("%.1fm", m);
    }
}
