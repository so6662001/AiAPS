package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@TableName("bas_schedule_strategy")
public class BasScheduleStrategy implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "strategy_id", type = IdType.AUTO)
    private Long strategyId;

    private String strategyCode;
    private String strategyName;
    private String strategyType;
    private BigDecimal weightDelivery;
    private BigDecimal weightCost;
    private BigDecimal weightEfficiency;
    private BigDecimal weightQuality;
    private String applyScope;
    private Long applyWcId;
    private String applyCategory;
    private Boolean isDefault;
    private Boolean isActive;
}
