package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@TableName("bas_anneal_recipe")
public class BasAnnealRecipe implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "recipe_id", type = IdType.AUTO)
    private Long recipeId;

    private String recipeCode;
    private String recipeName;

    @TableField("applicable_PATName")
    private String applicableGrade;

    private BigDecimal thicknessMin;
    private BigDecimal thicknessMax;
    private Integer targetTemp;
    private Integer heatRate;
    private Integer holdTemp;
    private Integer holdHours;
    private String coolMethod;
    private Integer totalCycleHours;
    private String compatGroup;
    private Boolean isActive;
}
