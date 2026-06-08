package com.aiaps.domain.aps;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("aps_furnace_charge")
public class ApsFurnaceCharge implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "charge_id", type = IdType.AUTO)
    private Long chargeId;

    private String chargeNo;
    private Long furnaceId;
    private Long scheduleId;
    private Long recipeId;
    private Date planLoadStart;
    private Date planLoadEnd;
    private Date planAnnealStart;
    private Date planAnnealEnd;
    private Date planCoolEnd;
    private Date planUnloadStart;
    private Date planUnloadEnd;
    private Date actualLoadStart;
    private Date actualAnnealStart;
    private Date actualAnnealEnd;
    private Date actualCoolEnd;
    private Date actualUnloadEnd;
    private Integer totalCoils;
    private BigDecimal totalWeight;
    private Integer layersUsed;
    private Integer actualTemp;
    private Integer actualHoldHours;
    private String chargeStatus;
    private String createdBy;
    private Date createdTime;
}
