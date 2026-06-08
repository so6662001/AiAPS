package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@TableName("bas_category_bom")
public class BasCategoryBom implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "cat_bom_id", type = IdType.AUTO)
    private Long catBomId;

    private String catBomCode;
    private String parentCategory;
    private String childCategory;
    private String calcFormulaCode;
    private BigDecimal scrapRate;
    private BigDecimal fixedScrapQty;
    private BigDecimal yieldRate;
    private Boolean isActive;
    private String remark;
}
