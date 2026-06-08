package com.aiaps.service.analytics;

import com.aiaps.domain.analytics.AnalyticsActionTrack;
import com.aiaps.domain.analytics.AnalyticsPageView;
import com.aiaps.domain.analytics.AnalyticsPerformance;
import com.aiaps.mapper.analytics.AnalyticsActionTrackMapper;
import com.aiaps.mapper.analytics.AnalyticsPageViewMapper;
import com.aiaps.mapper.analytics.AnalyticsPerformanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalyticsCollectService {

    private final AnalyticsPageViewMapper pageViewMapper;
    private final AnalyticsActionTrackMapper actionTrackMapper;
    private final AnalyticsPerformanceMapper performanceMapper;

    public void recordPageView(AnalyticsPageView view) {
        if (view.getEnterTime() == null) {
            view.setEnterTime(new Date());
        }
        pageViewMapper.insert(view);
    }

    public void recordAction(AnalyticsActionTrack track) {
        if (track.getEventTime() == null) {
            track.setEventTime(new Date());
        }
        actionTrackMapper.insert(track);
    }

    public void recordPerformance(AnalyticsPerformance perf) {
        if (perf.getRecordTime() == null) {
            perf.setRecordTime(new Date());
        }
        evaluateAlertLevel(perf);
        performanceMapper.insert(perf);
    }

    public void recordBatch(List<Map<String, Object>> events) {
        for (Map<String, Object> event : events) {
            String type = (String) event.get("type");
            if (type == null) continue;

            switch (type) {
                case "pageView":
                    recordPageViewFromMap(event);
                    break;
                case "action":
                    recordActionFromMap(event);
                    break;
                case "performance":
                    recordPerformanceFromMap(event);
                    break;
                default:
                    break;
            }
        }
    }

    public void evaluateAlertLevel(AnalyticsPerformance perf) {
        String level = "NORMAL";
        if ("PAGE".equals(perf.getSourceType())) {
            if (perf.getFcpMs() != null) {
                if (perf.getFcpMs() > 5000) level = "CRITICAL";
                else if (perf.getFcpMs() > 3000) level = "WARNING";
            }
        } else if ("API".equals(perf.getSourceType())) {
            if (perf.getResponseMs() != null) {
                if (perf.getResponseMs() > 5000) level = "CRITICAL";
                else if (perf.getResponseMs() > 2000) level = "WARNING";
            }
        }
        perf.setAlertLevel(level);
    }

    private void recordPageViewFromMap(Map<String, Object> map) {
        AnalyticsPageView view = new AnalyticsPageView();
        view.setPagePath((String) map.get("pagePath"));
        view.setPageName((String) map.get("pageName"));
        view.setPageModule((String) map.get("pageModule"));
        view.setFromPage((String) map.get("fromPage"));
        view.setDeviceType((String) map.get("deviceType"));
        view.setSessionId((String) map.get("sessionId"));
        view.setBrowser((String) map.get("browser"));
        if (map.get("userId") != null) {
            view.setUserName(String.valueOf(map.get("userId")));
        }
        if (map.get("durationSeconds") instanceof Number) {
            view.setDurationSeconds(((Number) map.get("durationSeconds")).intValue());
        }
        if (map.get("screenWidth") instanceof Number) {
            view.setScreenWidth(((Number) map.get("screenWidth")).intValue());
        }
        recordPageView(view);
    }

    private void recordActionFromMap(Map<String, Object> map) {
        AnalyticsActionTrack track = new AnalyticsActionTrack();
        track.setEventCode((String) map.get("eventCode"));
        track.setEventName((String) map.get("eventName"));
        track.setEventCategory((String) map.get("eventCategory"));
        track.setPagePath((String) map.get("pagePath"));
        track.setParam1Key((String) map.get("param1Key"));
        track.setParam1Value((String) map.get("param1Value"));
        track.setParam2Key((String) map.get("param2Key"));
        track.setParam2Value((String) map.get("param2Value"));
        track.setParam3Key((String) map.get("param3Key"));
        track.setParam3Value((String) map.get("param3Value"));
        track.setResultStatus((String) map.get("resultStatus"));
        track.setSessionId((String) map.get("sessionId"));
        track.setUserRole((String) map.get("userRole"));
        if (map.get("resultDurationMs") instanceof Number) {
            track.setResultDurationMs(((Number) map.get("resultDurationMs")).longValue());
        }
        recordAction(track);
    }

    private void recordPerformanceFromMap(Map<String, Object> map) {
        AnalyticsPerformance perf = new AnalyticsPerformance();
        perf.setSourceType((String) map.get("sourceType"));
        perf.setPagePath((String) map.get("pagePath"));
        perf.setApiPath((String) map.get("apiPath"));
        perf.setApiMethod((String) map.get("apiMethod"));
        perf.setSessionId((String) map.get("sessionId"));
        perf.setErrorMessage((String) map.get("errorMessage"));
        if (map.get("fcpMs") instanceof Number) {
            perf.setFcpMs(((Number) map.get("fcpMs")).intValue());
        }
        if (map.get("lcpMs") instanceof Number) {
            perf.setLcpMs(((Number) map.get("lcpMs")).intValue());
        }
        if (map.get("fidMs") instanceof Number) {
            perf.setFidMs(((Number) map.get("fidMs")).intValue());
        }
        if (map.get("loadMs") instanceof Number) {
            perf.setLoadMs(((Number) map.get("loadMs")).intValue());
        }
        if (map.get("responseMs") instanceof Number) {
            perf.setResponseMs(((Number) map.get("responseMs")).intValue());
        }
        if (map.get("httpStatus") instanceof Number) {
            perf.setHttpStatus(((Number) map.get("httpStatus")).intValue());
        }
        if (map.get("isError") instanceof Boolean) {
            perf.setIsError((Boolean) map.get("isError"));
        }
        recordPerformance(perf);
    }
}
