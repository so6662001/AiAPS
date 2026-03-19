package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@TableName("bas_bom_head")
public class BasBomHead implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "bom_id", type = IdType.AUTO)
    private Long bomId;

    private String bomCode;

    @TableField("PrdtID")
    private Long prdtId;

    private String bomVersion;
    private String bomType;
    private BigDecimal baseQty;
    private Date effectiveFrom;
    private Date effectiveTo;
    private Boolean isDefault;
    private Boolean isActive;
    private String remark;
    private String createdBy;
    private Date createdTime;

    @TableField(exist = false)
    private List<BasBomDetail> details;
}
