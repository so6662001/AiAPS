package com.aiaps.domain.report;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("rpt_daily_output")
public class RptDailyOutput implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "rpt_id", type = IdType.AUTO)
    private Long rptId;

    private Date rptDate;
    private Long wcId;
    private String shiftCode;
    private String categoryCode;
    private String gradeCode;
    private BigDecimal plannedQty;
    private BigDecimal actualQty;
    private BigDecimal goodQty;
    private BigDecimal scrapQty;
    private BigDecimal inputQty;
    private BigDecimal yieldRate;
    private BigDecimal productionHours;
    private BigDecimal setupHours;
    private BigDecimal downtimeHours;
    private BigDecimal idleHours;
    private Integer moldChanges;
    private Integer substituteCount;
    private Date lastAggregated;
}
