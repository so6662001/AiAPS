package com.aiaps.domain.aps;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("aps_nesting_pool")
public class ApsNestingPool implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "pool_id", type = IdType.AUTO)
    private Long poolId;
    private String poolNo;
    private Long demandLineId;
    private Long planOrderId;
    private String contractNo;
    private String customerCode;
    private String customerName;
    @TableField("PrdtID")
    private Long prdtId;
    private String categoryCode;
    @TableField("PATName")
    private String patName;
    @TableField("PAName")
    private String paName;
    private BigDecimal thickness;
    private BigDecimal width;
    private BigDecimal cutLength;
    private BigDecimal cutWidth;
    private BigDecimal requiredQty;
    private BigDecimal requiredWeight;
    private BigDecimal fulfilledQty;
    private BigDecimal fulfilledWeight;
    private BigDecimal remainingQty;
    private BigDecimal remainingWeight;
    private Boolean allowMerge;
    private String mergeGroupKey;
    private Integer priority;
    private Date requiredDate;
    private Long nestingId;
    private Long nestingDetailId;
    private String poolStatus;
    private Long runId;
    private Date createdTime;
}
