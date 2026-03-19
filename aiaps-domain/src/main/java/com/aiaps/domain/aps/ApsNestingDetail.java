package com.aiaps.domain.aps;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@TableName("aps_nesting_detail")
public class ApsNestingDetail implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "nesting_detail_id", type = IdType.AUTO)
    private Long nestingDetailId;

    private Long nestingId;
    private Integer lineNo;

    @TableField("output_PrdtID")
    private Long outputPrdtId;

    private BigDecimal outputWidth;
    private BigDecimal outputThickness;
    private Integer outputCount;
    private BigDecimal outputLength;
    private BigDecimal outputWeight;
    private BigDecimal totalWeight;
    private Long demandLineId;
    private Long scheduleId;
    private String outputType;
    private String contractNo;
    private String remark;
}
