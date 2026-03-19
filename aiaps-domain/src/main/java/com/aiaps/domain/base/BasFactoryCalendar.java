package com.aiaps.domain.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("bas_factory_calendar")
public class BasFactoryCalendar implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "calendar_id", type = IdType.AUTO)
    private Long calendarId;

    private String factoryCode;
    private Date calDate;
    private String dayType;
    private String shift1Start;
    private String shift1End;
    private String shift2Start;
    private String shift2End;
    private String shift3Start;
    private String shift3End;
    private String remark;
}
