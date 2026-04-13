package com.aiaps.domain.system;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("sys_agreement_sign")
public class SysAgreementSign implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "sign_id", type = IdType.AUTO)
    private Long signId;

    private Long enterpriseId;
    private String enterpriseName;
    private String signerUserId;
    private String signerName;
    private String signerRole;
    private Long versionId;
    private String agreementType;
    private String versionNo;
    private Boolean authAlgoTraining;
    private Boolean authRegionIndex;
    private Boolean authBenchmark;
    private Boolean authSupplyMatch;
    private Boolean authPriceAnalysis;
    private Boolean authProductImprove;
    private Boolean authCustomerSuccess;
    private Date signTime;
    private String signIp;
    private String signDevice;
    private String signSnapshotOssKey;
    private String signStatus;
    private Long supersededBy;
    private Date revokedTime;
    private String revokedReason;
}
