package com.aiaps.domain.production;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("prd_report")
public class PrdReport implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "report_id", type = IdType.AUTO)
    private Long reportId;

    @NotNull
    private Long scheduleId;
    @NotNull
    private Long schedOperId;
    private Date reportTime;
    @NotBlank
    private String shiftCode;
    @NotNull
    private BigDecimal reportQty;
    @NotNull
    private BigDecimal goodQty;
    private BigDecimal scrapQty;
    private BigDecimal reworkQty;
    private BigDecimal reportWeight;
    private BigDecimal goodWeight;
    private BigDecimal scrapWeight;
    private BigDecimal inputWeight;

    @TableField("PATName")
    private String patName;

    @TableField("PAName")
    private String paName;

    @TableField("ResNo")
    private String coilNo;

    @TableField("output_CardNo")
    private String outputBatchNo;

    @TableField("output_ResNo")
    private String outputCoilNo;

    private BigDecimal productLength;
    private String lengthDisplay;
    private String contractNo;
    private Date startTime;
    private Date endTime;
    private String operatorCode;
    private String remark;
}
