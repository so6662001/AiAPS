package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@TableName("bas_wc_product_rate")
public class BasWcProductRate implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "rate_id", type = IdType.AUTO)
    private Long rateId;

    private Long wcId;

    @TableField("PrdtID")
    private Long materialId;

    private String categoryCode;
    private BigDecimal capacityPerHour;
    private Integer setupTimeMinutes;
    private BigDecimal minBatchWeight;
    private BigDecimal expectedYieldRate;
    private Integer qualityScore;
    private String remark;
    private Boolean isActive;
}
