package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@TableName("bas_swap_cost_param")
public class BasSwapCostParam implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "param_id", type = IdType.AUTO)
    private Long paramId;

    private Long wcId;
    private Integer unloadTimeMin;
    private Integer locateTimeMin;
    private Integer loadTimeMin;
    private BigDecimal downtimeCostPerMin;
    private BigDecimal handlingCost;
    private BigDecimal swapThreshold;
}
