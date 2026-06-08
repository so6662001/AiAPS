package com.aiaps.domain.analytics;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("analytics_page_view")
public class AnalyticsPageView implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "view_id", type = IdType.AUTO)
    private Long viewId;

    private Long userId;
    private String userName;
    private String userRole;
    private String pagePath;
    private String pageName;
    private String pageModule;
    private Date enterTime;
    private Date leaveTime;
    private Integer durationSeconds;
    private String fromPage;
    private String deviceType;
    private Integer screenWidth;
    private String browser;
    private String sessionId;
    private String clientIp;
}
