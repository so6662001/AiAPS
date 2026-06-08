package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("bas_origin_exchange_group")
public class BasOriginExchangeGroup implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "group_id", type = IdType.AUTO)
    private Long groupId;

    private String groupCode;
    private String groupName;
    private String applicableCategory;
    private String applicableGradeFamily;
    private String exchangeType;
    private String remark;
}
