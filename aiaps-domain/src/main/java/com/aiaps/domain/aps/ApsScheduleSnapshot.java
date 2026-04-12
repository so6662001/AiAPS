package com.aiaps.domain.aps;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("aps_schedule_snapshot")
public class ApsScheduleSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "snapshot_id", type = IdType.AUTO)
    private Long snapshotId;

    private String snapshotNo;
    private String snapshotType;
    private String snapshotScope;
    private String description;
    private String createdBy;
    private Date createdTime;
}
