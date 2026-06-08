package com.aiaps.domain.aps;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("aps_schedule_snapshot_detail")
public class ApsScheduleSnapshotDetail implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "detail_id", type = IdType.AUTO)
    private Long detailId;

    private Long snapshotId;
    private Long scheduleId;
    private String scheduleNo;

    @TableField("PrdtID")
    private Long prdtId;

    private BigDecimal plannedQty;
    private BigDecimal plannedWeight;
    private Date scheduleStart;
    private Date scheduleEnd;
    private Long wcId;
    private Long moldId;
    private Integer priority;
    private String scheduleStatus;
    private Boolean isLocked;
    private String outputFlowType;
    private Long rawStockId;

    @TableField("PATName")
    private String gradeCode;

    private String contractNo;
}
