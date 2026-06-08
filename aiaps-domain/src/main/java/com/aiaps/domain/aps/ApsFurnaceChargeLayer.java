package com.aiaps.domain.aps;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@TableName("aps_furnace_charge_layer")
public class ApsFurnaceChargeLayer implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "layer_id", type = IdType.AUTO)
    private Long layerId;

    private Long chargeId;
    private Integer layerNo;
    private Long stockId;

    @TableField("CardNo")
    private String cardNo;

    @TableField("ResNo")
    private String resNo;

    @TableField("PrdtID")
    private Long prdtId;

    @TableField("PATName")
    private String patName;

    @TableField("PAName")
    private String paName;

    private BigDecimal coilWeight;
    private BigDecimal coilOuterDia;
    private BigDecimal thickness;
    private BigDecimal width;
    private String contractNo;
    private Long sourceScheduleId;
    private Long nextScheduleId;
    private String annealResult;
    private String qcRemark;

    @TableField("CardRemark")
    private String cardRemark;

    @TableField("CardRemark2")
    private String cardRemark2;
}
