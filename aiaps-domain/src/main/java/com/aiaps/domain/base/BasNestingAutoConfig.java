package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@TableName("bas_nesting_auto_config")
public class BasNestingAutoConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "config_id", type = IdType.AUTO)
    private Long configId;
    private Boolean mrpAutoNesting;
    private Integer minMergeCount;
    private BigDecimal minUtilizationPct;
    private Boolean autoSchedule;
    private Boolean autoLinkNext;
    private String enabledCategories;
    private Integer maxContractsPerNest;
    private Integer maxCoilCount;
    private Boolean isActive;
}
