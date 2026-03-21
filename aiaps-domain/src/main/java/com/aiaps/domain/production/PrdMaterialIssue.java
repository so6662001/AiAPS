package com.aiaps.domain.production;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("prd_material_issue")
public class PrdMaterialIssue implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "issue_id", type = IdType.AUTO)
    private Long issueId;

    @NotBlank
    private String issueNo;
    @NotBlank
    private String issueType;
    @NotNull
    private Long scheduleId;
    private Long schedOperId;
    @NotNull
    private Long wcId;

    @NotNull
    @TableField("PrdtID")
    private Long prdtId;

    @NotBlank
    @TableField("PATName")
    private String patName;

    @TableField("PAName")
    private String paName;

    @NotNull
    private Long stockId;

    @TableField("ResNo")
    private String coilNo;

    @TableField("CardNo")
    private String cardNo;

    private String batchNo;
    @NotNull
    private BigDecimal issueQty;
    @NotNull
    private BigDecimal issueWeight;
    @NotBlank
    private String warehouseCode;
    private String locationCode;
    private String issueStatus;
    private String contractNo;
    @NotBlank
    private String requestedBy;
    private Date requestedTime;
    private String issuedBy;
    private Date issuedTime;
    private String receivedBy;
    private Date receivedTime;
    private String remark;
}
