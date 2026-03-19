package com.aiaps.domain.aps;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("aps_nesting_plan")
public class ApsNestingPlan implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "nesting_id", type = IdType.AUTO)
    private Long nestingId;

    private String nestingNo;
    private String nestingType;
    private Long sourceMaterialId;
    private Long sourceStockId;

    @TableField("source_PATName")
    private String sourceGradeCode;

    @TableField("source_ResNo")
    private String sourceCoilNo;

    private BigDecimal sourceWidth;
    private BigDecimal sourceThickness;
    private BigDecimal sourceWeight;
    private BigDecimal sourceLengthM;
    private BigDecimal edgeTrim;
    private BigDecimal kerfWidth;
    private Integer slitCount;
    private BigDecimal utilizationPct;
    private BigDecimal wasteWeight;
    private BigDecimal remainderWeight;
    private String nestingStatus;
    private String createdBy;
    private Date createdTime;
}
