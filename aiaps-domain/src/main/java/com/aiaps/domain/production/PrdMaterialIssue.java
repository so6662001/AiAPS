package com.aiaps.domain.production;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("prd_material_issue")
public class PrdMaterialIssue implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "issue_id", type = IdType.AUTO)
    private Long issueId;

    private String issueNo;
    private String issueType;
    private Long scheduleId;
    private Long schedOperId;
    private Long wcId;

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
    private BigDecimal issueQty;
    private BigDecimal issueWeight;
    private String warehouseCode;
    private String locationCode;
    private String issueStatus;
    private String contractNo;
    private String requestedBy;
    private Date requestedTime;
    private String issuedBy;
    private Date issuedTime;
    private String receivedBy;
    private Date receivedTime;
    private String remark;
}
