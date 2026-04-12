package com.aiaps.domain.aps;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("aps_substitute_log")
public class ApsSubstituteLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "sub_log_id", type = IdType.AUTO)
    private Long subLogId;

    private Long scheduleId;
    private Long ruleId;
    private Long originalMaterialId;

    @TableField("original_PATName")
    private String originalGradeCode;

    private String originalSpecDesc;
    private Long substituteMaterialId;

    @TableField("substitute_PATName")
    private String substituteGradeCode;

    private Long substituteStockId;
    private String substituteSpecDesc;
    private String substituteType;
    private BigDecimal extraScrapRate;
    private String approvalStatus;
    private String approvedBy;
    private Date approvedTime;
    private String createdBy;
    private Date createdTime;
}
