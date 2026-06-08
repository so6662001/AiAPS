package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("bas_origin_exchange_member")
public class BasOriginExchangeMember implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "member_id", type = IdType.AUTO)
    private Long memberId;

    private Long groupId;
    private String originCode;
}
