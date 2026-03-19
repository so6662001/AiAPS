package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("bas_routing_head")
public class BasRoutingHead implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "routing_id", type = IdType.AUTO)
    private Long routingId;

    private String routingCode;

    @TableField("PrdtID")
    private Long prdtId;

    private String routingVersion;
    private Boolean isDefault;
    private Boolean isActive;
    private String remark;
    private String createdBy;
    private Date createdTime;
}
