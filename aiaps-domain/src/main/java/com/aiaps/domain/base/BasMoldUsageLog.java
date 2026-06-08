package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("bas_mold_usage_log")
public class BasMoldUsageLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "usage_id", type = IdType.AUTO)
    private Long usageId;

    private Long moldId;
    private Long wcId;
    private Long scheduleId;
    private Date usageDate;
    private BigDecimal usageQty;
    private Integer usagePcs;
    private BigDecimal accumulatedBefore;
    private BigDecimal accumulatedAfter;
    private String moldEvent;
    private String operatorCode;
    private String remark;
}
