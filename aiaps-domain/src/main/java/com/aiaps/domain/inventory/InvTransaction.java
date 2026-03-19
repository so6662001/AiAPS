package com.aiaps.domain.inventory;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("inv_transaction")
public class InvTransaction implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "txn_id", type = IdType.AUTO)
    private Long txnId;

    private String txnNo;
    private String txnType;
    private String txnDirection;

    @TableField("PrdtID")
    private Long prdtId;

    @TableField("PATName")
    private String patName;

    @TableField("PAName")
    private String paName;

    private Long stockId;

    @TableField("ResNo")
    private String coilNo;

    @TableField("CardNo")
    private String cardNo;

    private String batchNo;
    private BigDecimal txnQty;
    private BigDecimal txnWeight;
    private String warehouseCode;
    private String locationCode;
    private String toWarehouseCode;
    private String toLocationCode;
    private String sourceDocType;
    private Long sourceDocId;
    private String sourceDocNo;
    private BigDecimal beforeQty;
    private BigDecimal afterQty;
    private BigDecimal beforeWeight;
    private BigDecimal afterWeight;
    private BigDecimal productLength;
    private String lengthDisplay;
    private String contractNo;
    private Date txnTime;
    private String operatedBy;
    private String remark;
}
