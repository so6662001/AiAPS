package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@TableName("bas_temper_mill_config")
public class BasTemperMillConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "config_id", type = IdType.AUTO)
    private Long configId;

    private Long wcId;
    private BigDecimal maxSpeedMpm;
    private BigDecimal maxWidth;
    private BigDecimal minWidth;
    private BigDecimal maxThickness;
    private BigDecimal minThickness;
    private String elongationRange;
    private String roughnessRange;
    private Integer rollChangeMin;
}
