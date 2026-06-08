package com.aiaps.domain.aps;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@TableName("aps_nesting_2d_layout")
public class ApsNesting2dLayout implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "layout_id", type = IdType.AUTO)
    private Long layoutId;
    private Long nestingDetailId;
    private Long nestingId;
    private Integer sheetNo;
    private Long poolId;
    private String contractNo;
    private BigDecimal pieceWidth;
    private BigDecimal pieceLength;
    private Integer pieceQty;
    private BigDecimal posX;
    private BigDecimal posY;
    private Boolean isRotated;
    private String pieceType;
    private String remark;
}
