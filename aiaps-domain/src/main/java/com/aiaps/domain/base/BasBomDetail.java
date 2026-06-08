package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("bas_bom_detail")
public class BasBomDetail implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "bom_detail_id", type = IdType.AUTO)
    private Long bomDetailId;

    private Long bomId;
    private Integer lineNo;

    @TableField("child_PrdtID")
    private Long childPrdtId;

    private BigDecimal qtyPer;
    private BigDecimal scrapRate;
    private BigDecimal fixedScrapQty;
    private Integer operationNo;
    private String supplyType;
    private Date effectiveFrom;
    private Date effectiveTo;
    private String substituteGroup;
    private Boolean isActive;
}
