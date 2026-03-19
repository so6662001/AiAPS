package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@TableName("bas_spec_formula")
public class BasSpecFormula implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "formula_id", type = IdType.AUTO)
    private Long formulaId;

    private String formulaCode;
    private String formulaName;
    private String parentCategory;
    private String childCategory;
    private String formulaType;
    private String widthFormula;
    private BigDecimal widthToleranceMin;
    private BigDecimal widthToleranceMax;
    private String thicknessFormula;
    private BigDecimal thicknessTolMin;
    private BigDecimal thicknessTolMax;
    private String lengthFormula;
    private String weightFormula;
    private String byproductFormula;
    private Boolean isActive;
    private String remark;
}
