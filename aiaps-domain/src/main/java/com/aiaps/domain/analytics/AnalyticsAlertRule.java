package com.aiaps.domain.analytics;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("analytics_alert_rule")
public class AnalyticsAlertRule implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "rule_id", type = IdType.AUTO)
    private Long ruleId;

    private String ruleName;
    private String ruleType;
    private String matchPath;
    private String matchMethod;
    private Integer warningThreshold;
    private Integer criticalThreshold;
    private String thresholdUnit;
    private Integer windowMinutes;
    private Integer minSampleCount;
    private Boolean notifyWecom;
    private String notifyWebhookUrl;
    private Integer notifyIntervalMin;
    private Date lastNotifyTime;
    private Boolean isActive;
}
