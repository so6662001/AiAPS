package com.aiaps.service.analytics;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class WeComNotifyService {

    public void sendMarkdown(String webhookUrl, String content) {
        try {
            JSONObject body = new JSONObject();
            body.set("msgtype", "markdown");

            JSONObject markdown = new JSONObject();
            markdown.set("content", content);
            body.set("markdown", markdown);

            HttpUtil.post(webhookUrl, body.toString());
            log.info("企微通知已发送: url={}", webhookUrl);
        } catch (Exception e) {
            log.error("企微通知发送失败: url={}", webhookUrl, e);
        }
    }

    public String buildAlertMessage(String level, String type, String target,
                                    String currentValue, String threshold,
                                    String time, String suggestion) {
        String emoji = "CRITICAL".equals(level) ? "\uD83D\uDD34" : "\uD83D\uDFE1";
        StringBuilder sb = new StringBuilder();
        sb.append(emoji).append(" **AiAPS 性能告警**\n");
        sb.append("> **级别**: ").append(level).append("\n");
        sb.append("> **类型**: ").append(type).append("\n");
        sb.append("> **目标**: ").append(target).append("\n");
        sb.append("> **当前值**: ").append(currentValue).append("\n");
        sb.append("> **阈值**: ").append(threshold).append("\n");
        sb.append("> **时间**: ").append(time).append("\n");
        sb.append("> **建议**: ").append(suggestion);
        return sb.toString();
    }

    public String buildDailySummary(Map<String, Object> summaryData) {
        StringBuilder sb = new StringBuilder();
        sb.append("\uD83D\uDCCA **AiAPS 每日性能摘要**\n");
        sb.append("> **日期**: ").append(summaryData.getOrDefault("date", "-")).append("\n");
        sb.append("> **页面健康率**: ").append(summaryData.getOrDefault("pageHealthPct", "-")).append("%\n");
        sb.append("> **API健康率**: ").append(summaryData.getOrDefault("apiHealthPct", "-")).append("%\n");
        sb.append("> **总PV**: ").append(summaryData.getOrDefault("totalPv", "-")).append("\n");
        sb.append("> **活跃用户**: ").append(summaryData.getOrDefault("activeUsers", "-")).append("\n");
        sb.append("> **告警次数**: ").append(summaryData.getOrDefault("alertCount", "-"));
        return sb.toString();
    }
}
