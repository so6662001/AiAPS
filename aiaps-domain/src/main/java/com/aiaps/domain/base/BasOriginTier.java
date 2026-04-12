package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@TableName("bas_origin_tier")
public class BasOriginTier implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "tier_id", type = IdType.AUTO)
    private Long tierId;

    private String originCode;
    private String originName;
    private Integer qualityTier;
    private String tierName;
    private Integer pipeQualityScore;
    private Integer plateQualityScore;
    private Integer profileQualityScore;
    private Integer priceTier;
    private BigDecimal avgPremiumPerTon;
    private Boolean isActive;
}
