package com.aiaps.domain.production;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("prd_material_swap")
public class PrdMaterialSwap implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "swap_id", type = IdType.AUTO)
    private Long swapId;

    private String swapNo;
    private Long scheduleId;
    private Long schedOperId;
    private Long wcId;
    private String swapReason;
    private String reasonDesc;

    @TableField("old_PrdtID")
    private Long oldMaterialId;

    private Long oldStockId;

    @TableField("old_ResNo")
    private String oldCoilNo;

    @TableField("old_PATName")
    private String oldGradeCode;

    private BigDecimal oldUsedQty;
    private BigDecimal oldRemainQty;
    private String oldRemainAction;

    @TableField("new_PrdtID")
    private Long newMaterialId;

    private Long newStockId;

    @TableField("new_ResNo")
    private String newCoilNo;

    @TableField("new_PATName")
    private String newGradeCode;

    private BigDecimal newQty;
    private String newSource;
    private Date swapTime;
    private Integer swapDurationMin;
    private Boolean productionPaused;
    private Date pauseStart;
    private Date pauseEnd;
    private Boolean outputSplitNeeded;
    private BigDecimal outputBeforeQty;

    @TableField("output_before_PATName")
    private String outputBeforeGrade;

    private String contractNo;
    private String swapStatus;
    private String operatedBy;
    private String remark;
}
