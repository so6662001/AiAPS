package com.aiaps.domain.inventory;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("inv_item_barcode")
public class InvItemBarcode implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    @TableField("ItemBarcode")
    private String itemBarcode;

    private Long stockId;

    @TableField("CardNo")
    private String cardNo;

    @TableField("BindNo")
    private String bindNo;

    private Long bindId;

    @TableField("PrdtID")
    private Long prdtId;

    @TableField("PrdtNo")
    private String prdtNo;

    @TableField("PrdtName")
    private String prdtName;

    @TableField("PATName")
    private String patName;

    @TableField("PAName")
    private String paName;

    private BigDecimal itemLength;
    private BigDecimal itemWeight;
    private String specDisplay;
    private String qcStatus;
    private String defectDesc;
    private String itemStatus;
    private Long scheduleId;
    private String contractNo;

    @TableField("ResNo")
    private String resNo;

    private String shipDocNo;
    private Date shipTime;
    private Date createdTime;
}
