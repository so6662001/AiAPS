package com.aiaps.domain.demand;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("dem_demand_line")
public class DemDemandLine implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "demand_line_id", type = IdType.AUTO)
    private Long demandLineId;

    private Long demandId;
    private Integer lineNo;

    @TableField("PrdtID")
    private Long prdtId;

    @TableField("PATName")
    private String patName;

    @TableField("PAName")
    private String paName;

    private Boolean gradeFlexible;
    private Boolean originFlexible;

    private BigDecimal requiredQty;
    private BigDecimal requiredWeight;
    private BigDecimal priceWeight;
    private Date requiredDate;

    private BigDecimal allocatedQty;
    private BigDecimal allocatedWeight;
    private BigDecimal producedQty;
    private BigDecimal producedWeight;
    private BigDecimal deliveredQty;
    private BigDecimal deliveredWeight;

    private String lineStatus;
    private String specDesc;
    private String contractNo;

    private BigDecimal requiredLength;
    private String lengthType;
    private String lengthDisplay;

    private String remark;
}
