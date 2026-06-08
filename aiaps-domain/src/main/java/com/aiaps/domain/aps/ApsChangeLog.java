package com.aiaps.domain.aps;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("aps_change_log")
public class ApsChangeLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "change_id", type = IdType.AUTO)
    private Long changeId;

    private String changeScope;
    private Long relatedId;
    private String relatedNo;
    private String changeType;
    private String changeSource;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String changeReason;
    private String triggerEvent;
    private Long approvalId;
    private Long snapshotId;
    private String changedBy;
    private Date changedTime;
}
