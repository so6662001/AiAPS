package com.aiaps.domain.system;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("sys_user_acknowledgment")
public class SysUserAcknowledgment implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "ack_id", type = IdType.AUTO)
    private Long ackId;

    private String userId;
    private String userName;
    private Long enterpriseId;
    private Long versionId;
    private Date ackTime;
    private String ackIp;
}
