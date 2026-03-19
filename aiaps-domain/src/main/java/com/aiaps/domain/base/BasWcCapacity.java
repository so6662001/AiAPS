package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("bas_wc_capacity")
public class BasWcCapacity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "capacity_id", type = IdType.AUTO)
    private Long capacityId;

    private Long wcId;
    private Date calDate;
    private String shiftCode;
    private BigDecimal availableHours;
    private BigDecimal availableCapacity;
    private BigDecimal plannedDowntime;
    private String capacityStatus;
    private String remark;
}
