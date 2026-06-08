package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@TableName("bas_substitute_rule")
public class BasSubstituteRule implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "rule_id", type = IdType.AUTO)
    private Long ruleId;

    private String ruleCode;
    private String ruleName;
    private String ruleType;
    private String sourceCategory;

    @TableField("source_PATName")
    private String sourceGradeCode;

    private Long sourceMaterialId;

    @TableField("target_PATName")
    private String targetGradeCode;

    private Long targetMaterialId;
    private String dimensionType;
    private BigDecimal allowOverMin;
    private BigDecimal allowOverMax;
    private BigDecimal allowUnderMin;
    private BigDecimal allowUnderMax;
    private BigDecimal scrapRateAdjust;
    private BigDecimal costAdjustPct;
    private String qualityImpact;
    private Boolean needCustomerConfirm;
    private Boolean needTechConfirm;
    private Boolean autoSubstitute;
    private Integer priority;
    private Boolean isActive;
    private String remark;
}
