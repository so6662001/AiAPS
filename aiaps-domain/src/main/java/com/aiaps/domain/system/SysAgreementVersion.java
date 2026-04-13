package com.aiaps.domain.system;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("sys_agreement_version")
public class SysAgreementVersion implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "version_id", type = IdType.AUTO)
    private Long versionId;

    private String agreementType;
    private String versionNo;
    private String versionTitle;
    private Date effectiveDate;
    private String ossKey;
    private String ossUrl;
    private String contentHash;
    private String changeSummary;
    private Long prevVersionId;
    private Boolean isCurrent;
    private Boolean isPublished;
    private String createdBy;
    private Date createdTime;
}
