package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("bas_wc_cost_model")
public class BasWcCostModel implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "cost_model_id", type = IdType.AUTO)
    private Long costModelId;

    private Long wcId;
    private BigDecimal startupCost;
    private BigDecimal idleCostPerHour;
    private BigDecimal laborCostPerHour;
    private BigDecimal depreciationPerHour;
    private BigDecimal powerCostPerTon;
    private BigDecimal gasCostPerTon;
    private BigDecimal consumablePerTon;
    private BigDecimal moldChangeCost;
    private BigDecimal quickChangeCost;
    private BigDecimal avgScrapRate;
    private BigDecimal scrapCostPerTon;
    private Date effectiveFrom;
    private Date effectiveTo;
    private Boolean isActive;
}
