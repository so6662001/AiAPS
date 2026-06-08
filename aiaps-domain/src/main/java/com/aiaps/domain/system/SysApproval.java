package com.aiaps.domain.system;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("sys_approval")
public class SysApproval implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "approval_id", type = IdType.AUTO)
    private Long approvalId;

    private String approvalType;
    private Long relatedId;
    private String relatedNo;
    private String changeSummary;
    private String changeDetail;
    private String riskLevel;
    private String approvalStatus;
    private String submittedBy;
    private Date submittedTime;
    private String approver;
    private Date approvedTime;
    private String approvalComment;
    private Long snapshotId;
    private Boolean isExecuted;
    private Date executedTime;
}
