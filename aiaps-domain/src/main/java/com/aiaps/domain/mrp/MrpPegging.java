package com.aiaps.domain.mrp;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@TableName("mrp_pegging")
public class MrpPegging implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "pegging_id", type = IdType.AUTO)
    private Long peggingId;

    private Long runId;
    private String demandType;
    private Long demandId;
    private String supplyType;
    private Long supplyId;
    private BigDecimal peggedQty;
    private BigDecimal peggedWeight;
}
