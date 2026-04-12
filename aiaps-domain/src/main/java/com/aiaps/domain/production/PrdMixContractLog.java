package com.aiaps.domain.production;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("prd_mix_contract_log")
public class PrdMixContractLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "mix_log_id", type = IdType.AUTO)
    private Long mixLogId;

    private Long scheduleId;
    private String originalContract;
    private String actualContract;
    private Long stockId;

    @TableField("PrdtID")
    private Long materialId;

    private BigDecimal mixWeight;
    private String approvalStatus;
    private String approvedBy;
    private Date approvedTime;
    private String mixReason;
    private String createdBy;
    private Date createdTime;
}
