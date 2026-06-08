package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@TableName("bas_routing_oper")
public class BasRoutingOper implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "oper_id", type = IdType.AUTO)
    private Long operId;

    private Long routingId;
    private Integer operNo;
    private String operName;
    private Long wcId;
    private Long altWcId;
    private BigDecimal setupTime;
    private BigDecimal runTimePerUnit;
    private BigDecimal runTimePerBatch;
    private BigDecimal capacityPerHour;
    private BigDecimal transferBatch;
    private BigDecimal moveTimeHours;
    private BigDecimal overlapPct;
    private Boolean isSubcontract;
    private BigDecimal scrapRate;
    private Boolean isMilestone;
    private String remark;
}
