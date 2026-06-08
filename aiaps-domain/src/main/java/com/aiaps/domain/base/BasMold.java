package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("bas_mold")
public class BasMold implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "mold_id", type = IdType.AUTO)
    private Long moldId;

    private String moldCode;
    private String moldName;
    private String moldType;
    private String moldGroup;
    private String applicableCategory;
    private String specRangeDesc;
    private BigDecimal minSideA;
    private BigDecimal maxSideA;
    private BigDecimal minSideB;
    private BigDecimal maxSideB;
    private BigDecimal minThickness;
    private BigDecimal maxThickness;
    private BigDecimal maxProductionQty;
    private String maxProductionUnit;
    private Integer maxProductionPcs;
    private BigDecimal warningPct;
    private Integer repairCycleDays;
    private String repairDesc;
    private String moldStatus;
    private Long currentWcId;
    private BigDecimal currentAccumulated;
    private Date lastRepairDate;
    private Date nextRepairDate;
    private Boolean isActive;
    private String createdBy;
    private Date createdTime;
}
