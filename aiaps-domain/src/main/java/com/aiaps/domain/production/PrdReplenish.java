package com.aiaps.domain.production;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("prd_replenish")
public class PrdReplenish implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "replenish_id", type = IdType.AUTO)
    private Long replenishId;

    private String replenishNo;
    private Long scheduleId;
    private Long schedOperId;
    private String replenishReason;
    private String reasonDesc;
    private Long originalMaterialId;
    private Long originalStockId;

    @TableField("original_ResNo")
    private String originalCoilNo;

    @TableField("original_PATName")
    private String originalGradeCode;

    private BigDecimal plannedUsage;
    private BigDecimal actualUsage;

    @TableField("replenish_PrdtID")
    private Long replenishMaterialId;

    private BigDecimal replenishQty;
    private BigDecimal replenishWeight;

    @TableField("replenish_PATName")
    private String replenishGradeCode;

    private Boolean isSameSpec;
    private Boolean isSubstitute;
    private Long substituteRuleId;
    private String sourceType;
    private Long sourceStockId;

    @TableField("source_ResNo")
    private String sourceCoilNo;

    private String sourceWarehouse;
    private String replenishStatus;
    private String urgency;
    private String contractNo;
    private String requestedBy;
    private Date requestedTime;
    private String approvedBy;
    private Date approvedTime;
    private Date issuedTime;
    private Date receivedTime;
    private String remark;
}
