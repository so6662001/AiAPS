package com.aiaps.service.analytics;

import com.aiaps.domain.analytics.AnalyticsAlertRule;
import com.aiaps.domain.analytics.AnalyticsPerformance;
import com.aiaps.mapper.analytics.AnalyticsAlertRuleMapper;
import com.aiaps.mapper.analytics.AnalyticsPerformanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlertCheckScheduler {

    private final AnalyticsAlertRuleMapper ruleMapper;
    private final AnalyticsPerformanceMapper perfMapper;
    private final WeComNotifyService weComNotifyService;

    @Scheduled(fixedRate = 60000)
    public void checkAlerts() {
        List<AnalyticsAlertRule> rules = ruleMapper.selectActiveRules();
        for (AnalyticsAlertRule rule : rules) {
            try {
                String path = rule.getMatchPath();
                String sourceType = "API_SLOW".equals(rule.getRuleType()) || "API_ERROR".equals(rule.getRuleType())
                        ? "API" : "PAGE";
                List<AnalyticsPerformance> recent = perfMapper.selectRecentByPath(
                        path, sourceType, rule.getWindowMinutes());

                if (recent.size() < rule.getMinSampleCount()) continue;

                int metricValue = 0;
                if ("PAGE_LOAD".equals(rule.getRuleType())) {
                    metricValue = (int) recent.stream()
                            .mapToInt(p -> p.getFcpMs() != null ? p.getFcpMs() : 0)
                            .average().orElse(0);
                } else if ("API_SLOW".equals(rule.getRuleType())) {
                    metricValue = (int) recent.stream()
                            .mapToInt(p -> p.getResponseMs() != null ? p.getResponseMs() : 0)
                            .average().orElse(0);
                } else if ("API_ERROR".equals(rule.getRuleType())) {
                    long errors = recent.stream()
                            .filter(p -> Boolean.TRUE.equals(p.getIsError())).count();
                    metricValue = (int) (errors * 100 / recent.size());
                }

                String alertLevel = "NORMAL";
                if (metricValue >= rule.getCriticalThreshold()) {
                    alertLevel = "CRITICAL";
                } else if (metricValue >= rule.getWarningThreshold()) {
                    alertLevel = "WARNING";
                }

                if (!"NORMAL".equals(alertLevel)
                        && Boolean.TRUE.equals(rule.getNotifyWecom())
                        && rule.getNotifyWebhookUrl() != null) {

                    if (rule.getLastNotifyTime() != null) {
                        long minutesSinceLast =
                                (System.currentTimeMillis() - rule.getLastNotifyTime().getTime()) / 60000;
                        if (minutesSinceLast < rule.getNotifyIntervalMin()) continue;
                    }

                    String content = weComNotifyService.buildAlertMessage(
                            alertLevel,
                            rule.getRuleName(),
                            path != null ? path : "全局",
                            metricValue + ("MS".equals(rule.getThresholdUnit()) ? "ms" : "%"),
                            ("CRITICAL".equals(alertLevel) ? rule.getCriticalThreshold() : rule.getWarningThreshold())
                                    + rule.getThresholdUnit(),
                            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()),
                            "请检查系统性能"
                    );

                    String emoji = "CRITICAL".equals(alertLevel) ? "\uD83D\uDD34" : "\uD83D\uDFE1";
                    weComNotifyService.sendMarkdown(
                            rule.getNotifyWebhookUrl(),
                            emoji + " AiAPS 性能告警\n" + content);

                    rule.setLastNotifyTime(new Date());
                    ruleMapper.updateById(rule);

                    log.warn("性能告警已发送: rule={}, level={}, value={}",
                            rule.getRuleName(), alertLevel, metricValue);
                }
            } catch (Exception e) {
                log.error("告警检测异常: rule={}", rule.getRuleName(), e);
            }
        }
    }
}
