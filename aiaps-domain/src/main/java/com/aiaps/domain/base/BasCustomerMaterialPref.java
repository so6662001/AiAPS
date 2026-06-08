package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("bas_customer_material_pref")
public class BasCustomerMaterialPref implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "pref_id", type = IdType.AUTO)
    private Long prefId;

    private String customerCode;
    private String gradeSubstitutePolicy;
    private Boolean gradeAutoApprove;
    private String originSubstitutePolicy;
    private Boolean originAutoApprove;
    private String specialRulesDesc;
    private Boolean isActive;
    private String remark;
}
