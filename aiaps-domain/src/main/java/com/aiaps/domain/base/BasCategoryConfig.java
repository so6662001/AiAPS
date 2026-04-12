package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("bas_category_config")
public class BasCategoryConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "config_id", type = IdType.AUTO)
    private Long configId;

    private String categoryCode;
    private Boolean enableItemBarcode;
    private String barcodeRule;
    private String barcodePrefix;
    private Boolean enableLengthTrack;
    private Integer defaultBindQty;
}
