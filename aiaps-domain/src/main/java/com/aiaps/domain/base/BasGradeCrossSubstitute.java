package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@TableName("bas_grade_cross_substitute")
public class BasGradeCrossSubstitute implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "cross_sub_id", type = IdType.AUTO)
    private Long crossSubId;

    @TableField("source_PATName")
    private String sourceGradeCode;

    @TableField("target_PATName")
    private String targetGradeCode;

    private String substituteDirection;
    private String applicableCategory;
    private Boolean autoAllowed;
    private Boolean needTechConfirm;
    private Boolean needCustomerConfirm;
    private BigDecimal costImpactPct;
    private String qualityNote;
    private Boolean isActive;
    private String remark;
}
