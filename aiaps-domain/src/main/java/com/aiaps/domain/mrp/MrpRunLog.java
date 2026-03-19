package com.aiaps.domain.mrp;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("mrp_run_log")
public class MrpRunLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "run_id", type = IdType.AUTO)
    private Long runId;

    private String runNo;
    private String runType;
    private String runScope;
    private String scopeFilter;
    private Integer planHorizonDays;
    private String runStatus;
    private Date startTime;
    private Date endTime;
    private Integer demandCount;
    private Integer planOrderCount;
    private Integer purchaseCount;
    private String errorMessage;
    private String runBy;
    private Date createdTime;
}
