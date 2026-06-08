package com.aiaps.domain.trace;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("trc_trace_link")
public class TrcTraceLink implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "trace_id", type = IdType.AUTO)
    private Long traceId;

    private String sourceType;
    private Long sourceStockId;
    @TableField("source_CardNo")
    private String sourceCardNo;
    @TableField("source_BindNo")
    private String sourceBindNo;
    @TableField("source_ResNo")
    private String sourceResNo;
    @TableField("source_PrdtID")
    private Long sourcePrdtId;
    @TableField("source_PATName")
    private String sourcePatName;
    @TableField("source_PAName")
    private String sourcePaName;
    private BigDecimal sourceWeight;

    private String processType;
    private Long scheduleId;
    private String scheduleNo;
    private Long nestingId;

    private String targetType;
    private Long targetStockId;
    @TableField("target_CardNo")
    private String targetCardNo;
    @TableField("target_BindNo")
    private String targetBindNo;
    @TableField("target_ResNo")
    private String targetResNo;
    @TableField("target_PrdtID")
    private Long targetPrdtId;
    @TableField("target_PATName")
    private String targetPatName;
    @TableField("target_PAName")
    private String targetPaName;
    private BigDecimal targetWeight;

    private String contractNo;
    private Date traceTime;
    private String operatedBy;
    private String remark;
}
