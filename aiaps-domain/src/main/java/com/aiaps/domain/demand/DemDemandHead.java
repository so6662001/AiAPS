package com.aiaps.domain.demand;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@TableName("dem_demand_head")
public class DemDemandHead implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "demand_id", type = IdType.AUTO)
    private Long demandId;

    private String demandNo;
    private String demandSource;
    private String sourceDocNo;
    private String customerCode;
    private String customerName;
    private Integer priority;
    private Date requiredDate;
    private String demandStatus;
    private Long mrpRunId;
    private String remark;
    private String createdBy;
    private Date createdTime;
    private String updatedBy;
    private Date updatedTime;

    @TableField(exist = false)
    private List<DemDemandLine> lines;
}
