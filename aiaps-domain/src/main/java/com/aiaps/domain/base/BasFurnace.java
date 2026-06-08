package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("bas_furnace")
public class BasFurnace implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "furnace_id", type = IdType.AUTO)
    private Long furnaceId;

    private String furnaceCode;
    private String furnaceName;
    private Long wcId;
    private Integer totalLayers;
    private BigDecimal maxWeightPerLayer;
    private BigDecimal maxTotalWeight;
    private BigDecimal maxCoilDiameter;
    private BigDecimal innerDiameter;
    private BigDecimal innerHeight;
    private Integer stdCycleHours;
    private Integer stdHeatHours;
    private Integer stdHoldHours;
    private Integer stdCoolHours;
    private Integer loadHours;
    private Integer unloadHours;
    private String furnaceStatus;
    private Long currentScheduleId;
    private Date currentStartTime;
    private Date expectedEndTime;
    private Boolean isActive;
}
