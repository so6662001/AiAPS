package com.aiaps.domain.mrp;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("mrp_plan_order")
public class MrpPlanOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "plan_order_id", type = IdType.AUTO)
    private Long planOrderId;

    private Long runId;
    private String planOrderNo;

    @TableField("PrdtID")
    private Long prdtId;

    @TableField("PATName")
    private String patName;

    @TableField("PAName")
    private String paName;

    private Boolean gradeFlexible;
    private Boolean originFlexible;

    private String orderType;
    private BigDecimal plannedQty;
    private BigDecimal plannedWeight;
    private Date plannedStartDate;
    private Date plannedEndDate;

    private String demandSource;
    private Long sourceDemandId;
    private Long sourceDemandLine;
    private Long parentPlanOrder;
    private Integer bomLevel;
    private String orderStatus;
    private Boolean isFirmed;
    private String contractNo;

    private BigDecimal planLength;
    private String lengthType;
    private String lengthDisplay;

    private Boolean isCategoryBom;
    private String rawCategoryCode;
    private BigDecimal rawWidthMin;
    private BigDecimal rawWidthMax;
    private BigDecimal rawThicknessMin;
    private BigDecimal rawThicknessMax;
    private String rawGradeCode;
    private String rawOriginCode;
    private Boolean rawGradeFlexible;
    private Boolean rawOriginFlexible;

    private Long matchedMaterialId;
    private Long matchedStockId;
    private String matchedCoilNo;
    private String matchedGradeCode;
    private String matchedOriginCode;
    private String matchStatus;

    private String remark;
    private Date createdTime;
}
