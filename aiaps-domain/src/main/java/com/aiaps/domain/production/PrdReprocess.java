package com.aiaps.domain.production;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("prd_reprocess")
public class PrdReprocess implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "reprocess_id", type = IdType.AUTO)
    private Long reprocessId;

    private String reprocessNo;
    private String reprocessType;
    private String sourceType;
    private Long sourceScheduleId;
    private Long sourceStockId;
    private String sourceReturnNo;

    @TableField("input_PrdtID")
    private Long inputMaterialId;

    @TableField("input_PATName")
    private String inputGradeCode;

    private BigDecimal inputQty;

    @TableField("input_CardNo")
    private String inputBatchNo;

    @TableField("output_PrdtID")
    private Long outputMaterialId;

    @TableField("output_PATName")
    private String outputGradeCode;

    private BigDecimal outputQtyPlanned;
    private BigDecimal outputQtyActual;
    private BigDecimal scrapQty;
    private String processDesc;
    private Long wcId;
    private Long scheduleId;
    private Long demandLineId;
    private String outputFlowType;
    private String outputFlowDesc;
    private Long nextReprocessId;
    private String contractNo;
    private String reprocessStatus;
    private String createdBy;
    private Date createdTime;
    private String updatedBy;
    private Date updatedTime;
    private String remark;
}
