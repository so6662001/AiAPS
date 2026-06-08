package com.aiaps.domain.aps;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("aps_schedule_oper")
public class ApsScheduleOper implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "sched_oper_id", type = IdType.AUTO)
    private Long schedOperId;

    private Long scheduleId;
    private Integer operNo;
    private String operName;
    private Long wcId;
    private BigDecimal plannedQty;
    private BigDecimal completedQty;
    private BigDecimal scrapQty;
    private BigDecimal plannedWeight;
    private BigDecimal completedWeight;
    private BigDecimal scrapWeight;
    private BigDecimal inputWeight;

    @TableField("PATName")
    private String patName;

    @TableField("PAName")
    private String paName;

    private Long inputMaterialId;
    private Long outputMaterialId;
    private Date setupStart;
    private Date setupEnd;
    private Date operStart;
    private Date operEnd;
    private Date actualStart;
    private Date actualEnd;
    private String operStatus;
    private Boolean isLocked;
    private Integer sequenceInWc;
    private String outputFlowType;
    private String outputFlowDesc;
    private Long nextSchedOperId;
    private Long moldId;
    private Boolean isMoldChange;
    private String contractNo;
    private Boolean isContinuousPass;
    private Boolean passToNextOper;
    private String remark;
}
