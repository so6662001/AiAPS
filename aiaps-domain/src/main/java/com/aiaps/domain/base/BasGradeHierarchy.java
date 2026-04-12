package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@TableName("bas_grade_hierarchy")
public class BasGradeHierarchy implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "hierarchy_id", type = IdType.AUTO)
    private Long hierarchyId;

    private String gradeFamily;
    private String gradeCode;
    private Integer gradeLevel;
    private String standardCode;
    private BigDecimal yieldStrengthMin;
    private BigDecimal tensileStrengthMin;
    private BigDecimal elongationMin;
    private Integer impactTemp;
    private BigDecimal impactValueMin;
    private Boolean isActive;
}
