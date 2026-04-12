package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@TableName("bas_mold_product")
public class BasMoldProduct implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "mold_product_id", type = IdType.AUTO)
    private Long moldProductId;

    private Long moldId;

    @TableField("PrdtID")
    private Long materialId;

    private Boolean isPrimary;
    private BigDecimal efficiencyRate;
    private BigDecimal capacityPerHour;
    private Boolean thicknessAdjustable;
    private String remark;
}
