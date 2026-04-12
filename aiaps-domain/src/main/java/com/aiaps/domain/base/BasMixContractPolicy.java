package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("bas_mix_contract_policy")
public class BasMixContractPolicy implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "policy_id", type = IdType.AUTO)
    private Long policyId;

    private String policyScope;
    private String customerCode;
    private Boolean allowMix;
    private Boolean needApproval;
    private String mixScope;
    private String remark;
    private Boolean isActive;
}
