package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("bas_work_center")
public class BasWorkCenter implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "wc_id", type = IdType.AUTO)
    private Long wcId;

    private String wcCode;
    private String wcName;
    private String wcType;
    private String workshopCode;
    private String factoryCode;
    private String capacityUnit;
    private BigDecimal stdCapacity;
    private BigDecimal efficiencyRate;
    private BigDecimal utilizationRate;
    private String shiftMode;
    private BigDecimal hoursPerShift;
    private Integer maxParallelJobs;
    private Integer setupTimeMinutes;
    private BigDecimal queueTimeHours;
    private Boolean isBottleneck;
    private Boolean isActive;
    private String createdBy;
    private Date createdTime;
}
