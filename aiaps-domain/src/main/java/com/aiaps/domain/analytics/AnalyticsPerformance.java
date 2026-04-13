package com.aiaps.domain.analytics;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("analytics_performance")
public class AnalyticsPerformance implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "perf_id", type = IdType.AUTO)
    private Long perfId;

    private String sourceType;
    private Long userId;
    private String sessionId;
    private String pagePath;
    private Integer fcpMs;
    private Integer lcpMs;
    private Integer fidMs;
    private Integer loadMs;
    private Integer longTaskCount;
    private String apiPath;
    private String apiMethod;
    private Integer responseMs;
    private Integer httpStatus;
    private Boolean isError;
    private String errorMessage;
    private String alertLevel;
    private Boolean alertSent;
    private Date recordTime;
}
