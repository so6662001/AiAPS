package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@TableName("bas_pltcm_config")
public class BasPltcmConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "config_id", type = IdType.AUTO)
    private Long configId;

    private Long wcId;
    private String lineType;
    private BigDecimal maxSpeedMpm;
    private BigDecimal maxEntryThickness;
    private BigDecimal minExitThickness;
    private BigDecimal maxWidth;
    private BigDecimal minWidth;
    private BigDecimal maxReductionPct;
    private Integer pickleTankCount;
    private String pickleType;
    private BigDecimal pickleSpeedMpm;
    private Integer standCount;
    private Integer rollChangeMin;
    private String welderType;
    private Integer weldTimeSec;
    private BigDecimal maxWeldThickness;
    private Integer widthChangeMin;
    private Integer thicknessChangeMin;
    private Integer gradeChangeMin;
}
