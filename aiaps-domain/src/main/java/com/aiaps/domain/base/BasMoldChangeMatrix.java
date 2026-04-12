package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("bas_mold_change_matrix")
public class BasMoldChangeMatrix implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "change_matrix_id", type = IdType.AUTO)
    private Long changeMatrixId;

    private Long wcId;
    private Long fromMoldId;
    private Long toMoldId;
    private Integer changeTimeMinutes;
    private Boolean isQuickChange;
    private Integer quickChangeMinutes;
    private String remark;
}
