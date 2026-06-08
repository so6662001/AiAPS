package com.aiaps.domain.inventory;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("inv_receipt")
public class InvReceipt implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "receipt_id", type = IdType.AUTO)
    private Long receiptId;

    private String receiptNo;
    private String receiptType;
    private Long scheduleId;
    private Long schedOperId;
    private String purchaseOrderNo;

    @TableField("PrdtID")
    private Long prdtId;

    @TableField("PATName")
    private String patName;

    @TableField("PAName")
    private String paName;

    private BigDecimal receiptQty;
    private BigDecimal receiptWeight;
    private BigDecimal theoryWeight;
    private BigDecimal actualWeight;
    private String qcStatus;
    private String qcBy;
    private Date qcTime;
    private String warehouseCode;
    private String locationCode;
    private Long targetStockId;

    @TableField("ResNo")
    private String coilNo;

    private BigDecimal productLength;
    private String lengthDisplay;
    private String contractNo;
    private String receiptStatus;
    private String receivedBy;
    private Date receivedTime;
    private Date createdTime;
    private String remark;
}
