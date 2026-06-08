package com.aiaps.domain.demand;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("dem_demand_line")
public class DemDemandLine implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "demand_line_id", type = IdType.AUTO)
    private Long demandLineId;

    @NotNull
    private Long demandId;
    private Integer lineNo;

    @NotNull
    @TableField("PrdtID")
    private Long prdtId;

    @NotBlank
    @TableField("PATName")
    private String patName;

    @TableField("PAName")
    private String paName;

    private Boolean gradeFlexible;
    private Boolean originFlexible;

    @NotNull
    private BigDecimal requiredQty;
    private BigDecimal requiredWeight;
    private BigDecimal priceWeight;
    @NotNull
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
