package com.aiaps.domain.aps;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("aps_simulation")
public class ApsSimulation implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(value = "sim_id", type = IdType.AUTO)
    private Long simId;
    private String simNo;
    private String simName;
    private String simType;
    private String simStatus;
    private String operationDesc;
    private Integer affectedCount;
    private Integer deliveryRiskCount;
    private BigDecimal costDiff;
    private BigDecimal timeDiffHours;
    private Long snapshotId;
    private String createdBy;
    private Date createdTime;
    private Date appliedTime;
}
