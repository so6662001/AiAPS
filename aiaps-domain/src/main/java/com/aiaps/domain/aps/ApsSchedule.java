package com.aiaps.domain.aps;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@TableName("aps_schedule")
public class ApsSchedule implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "schedule_id", type = IdType.AUTO)
    private Long scheduleId;

    @NotBlank
    private String scheduleNo;
    private Long planOrderId;

    @NotNull
    @TableField("PrdtID")
    private Long prdtId;

    @NotBlank
    private String demandGradeCode;
    private String demandOriginCode;
    private String actualGradeCode;
    private String actualOriginCode;
    private String outputGradeCode;
    private String outputOriginCode;
    private Boolean gradeSubstituted;
    private Boolean originSubstituted;

    @NotNull
    private BigDecimal plannedQty;
    @NotNull
    private BigDecimal plannedWeight;
    private BigDecimal goodQty;
    private BigDecimal goodWeight;
    private BigDecimal scrapQty;
    private BigDecimal scrapWeight;
    private BigDecimal inputWeight;
    private BigDecimal yieldRate;

    @NotNull
    private Date scheduleStart;
    @NotNull
    private Date scheduleEnd;
    private Integer priority;

    private String demandSource;
    private String sourceDemandNo;
    private String customerCode;
    private String customerName;
    private String contractNo;

    private BigDecimal productLength;
    private String lengthType;
    private String lengthDisplay;

    private Long rawMaterialId;
    private Long rawStockId;
    private String rawGradeCode;
    private String rawOriginCode;
    private String rawCoilNo;

    private Long moldId;
    private BigDecimal moldAccumulatedBefore;
    private BigDecimal moldAccumulatedAfter;
    private Boolean moldChangeRequired;
    private Long moldChangeScheduleId;

    private String outputFlowType;
    private String outputFlowDesc;
    private String nextOperName;
    private Long nextScheduleId;
    private Long nextWcId;
    private String targetWarehouse;
    private String directCustomer;

    private String schedulePhase;
    private String scheduleLevel;
    private Long parentScheduleId;
    private Boolean isMultiOutput;
    private Long nestingPlanId;

    private Boolean isCombinedProcess;
    private String combinedProcessType;

    private Long furnaceId;
    private Long chargeId;
    private Integer furnaceLayer;
    private Long annealRecipeId;
    private String zincCoatType;
    private BigDecimal zincWeightG;

    private Boolean allowMixContract;

    private String scheduleStatus;
    private Boolean isLocked;
    private String lockReason;
    private Integer scheduleVersion;
    private String remark;
    private String createdBy;
    private Date createdTime;
    private String updatedBy;
    private Date updatedTime;

    @TableField(exist = false)
    private List<ApsScheduleOper> opers;
}
