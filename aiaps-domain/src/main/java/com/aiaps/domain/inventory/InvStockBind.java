package com.aiaps.domain.inventory;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("inv_stock_bind")
public class InvStockBind implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "bind_id", type = IdType.AUTO)
    private Long bindId;

    private Long stockId;

    @TableField("CardNo")
    private String cardNo;

    @TableField("BindNo")
    private String bindNo;

    private BigDecimal bindQty;
    private BigDecimal bindWeight;
    private BigDecimal theoryWeight;
    private BigDecimal actualWeight;
    private BigDecimal productLength;
    private String lengthDisplay;
    private String bindStatus;

    @TableField("CardRemark")
    private String cardRemark;

    @TableField("CardRemark2")
    private String cardRemark2;

    private String contractNo;
    private Long scheduleId;
    private Long reportId;
    private Date createdTime;
}
