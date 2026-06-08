package com.aiaps.domain.analytics;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("analytics_action_track")
public class AnalyticsActionTrack implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "track_id", type = IdType.AUTO)
    private Long trackId;

    private Long userId;
    private String userRole;
    private String eventCode;
    private String eventName;
    private String eventCategory;
    private String pagePath;
    private String param1Key;
    private String param1Value;
    private String param2Key;
    private String param2Value;
    private String param3Key;
    private String param3Value;
    private String resultStatus;
    private Long resultDurationMs;
    private Date eventTime;
    private String sessionId;
}
