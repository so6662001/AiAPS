package com.aiaps.domain.inventory;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("inv_stock")
public class InvStock implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "stock_id", type = IdType.AUTO)
    private Long stockId;

    @TableField("PrdtID")
    private Long prdtId;

    @TableField("PATName")
    private String patName;

    @TableField("PAName")
    private String paName;

    private String warehouseCode;
    private String locationCode;

    @TableField("CardNo")
    private String cardNo;

    @TableField("ResNo")
    private String resNo;

    @TableField("BindNo")
    private String bindNo;

    private String heatNo;
    private String certNo;

    private BigDecimal onHandQty;
    private BigDecimal reservedQty;
    private BigDecimal inTransitQty;
    private BigDecimal inProcessQty;
    private BigDecimal qualityHoldQty;

    private BigDecimal onHandWeight;
    private BigDecimal reservedWeight;
    private BigDecimal inTransitWeight;
    private BigDecimal inProcessWeight;
    private BigDecimal qualityHoldWeight;

    private BigDecimal actualThickness;
    private BigDecimal actualWidth;
    private BigDecimal actualLength;
    private BigDecimal actualWeight;
    private BigDecimal coilOuterDia;
    private BigDecimal coilInnerDia;

    private BigDecimal stockLength;
    private String lengthType;
    private String lengthDisplay;

    private BigDecimal unitPrice;
    private Date purchaseDate;

    @TableField("EnterDate")
    private Date enterDate;

    private String contractNo;

    @TableField("CardRemark")
    private String cardRemark;

    @TableField("CardRemark2")
    private String cardRemark2;

    private Date lastUpdated;

    @Version
    @TableField("version")
    private Integer version;
}
