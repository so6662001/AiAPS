package com.aiaps.service.analytics;

import com.aiaps.domain.analytics.AnalyticsActionTrack;
import com.aiaps.domain.analytics.AnalyticsPageView;
import com.aiaps.domain.analytics.AnalyticsPerformance;
import com.aiaps.mapper.analytics.AnalyticsActionTrackMapper;
import com.aiaps.mapper.analytics.AnalyticsPageViewMapper;
import com.aiaps.mapper.analytics.AnalyticsPerformanceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsQueryService {

    private final AnalyticsPageViewMapper pageViewMapper;
    private final AnalyticsActionTrackMapper actionTrackMapper;
    private final AnalyticsPerformanceMapper performanceMapper;

    /**
     * A1: 页面热力 — group page views by pagePath, count PV, UV, avg duration
     */
    public List<Map<String, Object>> getPageHeat(Date dateFrom, Date dateTo) {
        LambdaQueryWrapper<AnalyticsPageView> wrapper = new LambdaQueryWrapper<>();
        if (dateFrom != null) wrapper.ge(AnalyticsPageView::getEnterTime, dateFrom);
        if (dateTo != null) wrapper.le(AnalyticsPageView::getEnterTime, dateTo);
        List<AnalyticsPageView> views = pageViewMapper.selectList(wrapper);

        Map<String, List<AnalyticsPageView>> grouped = views.stream()
                .filter(v -> v.getPagePath() != null)
                .collect(Collectors.groupingBy(AnalyticsPageView::getPagePath));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<AnalyticsPageView>> entry : grouped.entrySet()) {
            Map<String, Object> row = new HashMap<>();
            List<AnalyticsPageView> list = entry.getValue();
            row.put("pagePath", entry.getKey());
            row.put("pv", list.size());
            long uv = list.stream()
                    .map(AnalyticsPageView::getUserId)
                    .filter(Objects::nonNull)
                    .distinct().count();
            row.put("uv", uv);
            double avgDuration = list.stream()
                    .filter(v -> v.getDurationSeconds() != null)
                    .mapToInt(AnalyticsPageView::getDurationSeconds)
                    .average().orElse(0);
            row.put("avgDurationSeconds", Math.round(avgDuration));
            row.put("pageName", list.get(0).getPageName());
            result.add(row);
        }
        result.sort((a, b) -> Integer.compare((int) b.get("pv"), (int) a.get("pv")));
        return result;
    }

    /**
     * A2: 功能价值 — weighted score from PV, avg duration, action conversion, user coverage
     */
    public List<Map<String, Object>> getFeatureValue(Date dateFrom, Date dateTo) {
        List<Map<String, Object>> pageHeat = getPageHeat(dateFrom, dateTo);

        LambdaQueryWrapper<AnalyticsActionTrack> actionWrapper = new LambdaQueryWrapper<>();
        if (dateFrom != null) actionWrapper.ge(AnalyticsActionTrack::getEventTime, dateFrom);
        if (dateTo != null) actionWrapper.le(AnalyticsActionTrack::getEventTime, dateTo);
        List<AnalyticsActionTrack> actions = actionTrackMapper.selectList(actionWrapper);

        Map<String, Long> actionCountByPage = actions.stream()
                .filter(a -> a.getPagePath() != null)
                .collect(Collectors.groupingBy(AnalyticsActionTrack::getPagePath, Collectors.counting()));

        long totalUsers = pageHeat.stream()
                .mapToLong(m -> ((Number) m.get("uv")).longValue())
                .max().orElse(1);

        for (Map<String, Object> row : pageHeat) {
            String path = (String) row.get("pagePath");
            int pv = (int) row.get("pv");
            long avgDur = (long) row.get("avgDurationSeconds");
            long actionCount = actionCountByPage.getOrDefault(path, 0L);
            long uv = ((Number) row.get("uv")).longValue();

            double conversionRate = pv > 0 ? (double) actionCount / pv : 0;
            double userCoverage = totalUsers > 0 ? (double) uv / totalUsers : 0;

            double score = pv * 0.3 + avgDur * 0.2 + conversionRate * 100 * 0.3 + userCoverage * 100 * 0.2;
            row.put("actionCount", actionCount);
            row.put("conversionRate", Math.round(conversionRate * 100));
            row.put("userCoverage", Math.round(userCoverage * 100));
            row.put("score", Math.round(score * 10) / 10.0);
        }
        pageHeat.sort((a, b) -> Double.compare((double) b.get("score"), (double) a.get("score")));
        return pageHeat;
    }

    /**
     * A3: 用户活跃度 — DAU, WAU, MAU, role distribution
     */
    public Map<String, Object> getUserActivity(Date dateFrom, Date dateTo) {
        LambdaQueryWrapper<AnalyticsPageView> wrapper = new LambdaQueryWrapper<>();
        if (dateFrom != null) wrapper.ge(AnalyticsPageView::getEnterTime, dateFrom);
        if (dateTo != null) wrapper.le(AnalyticsPageView::getEnterTime, dateTo);
        List<AnalyticsPageView> views = pageViewMapper.selectList(wrapper);

        Map<String, Object> result = new HashMap<>();

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date todayStart = cal.getTime();

        cal.add(Calendar.DAY_OF_MONTH, -7);
        Date weekStart = cal.getTime();

        cal.setTime(todayStart);
        cal.add(Calendar.MONTH, -1);
        Date monthStart = cal.getTime();

        long dau = views.stream()
                .filter(v -> v.getEnterTime() != null && !v.getEnterTime().before(todayStart))
                .map(AnalyticsPageView::getUserId)
                .filter(Objects::nonNull)
                .distinct().count();

        long wau = views.stream()
                .filter(v -> v.getEnterTime() != null && !v.getEnterTime().before(weekStart))
                .map(AnalyticsPageView::getUserId)
                .filter(Objects::nonNull)
                .distinct().count();

        long mau = views.stream()
                .filter(v -> v.getEnterTime() != null && !v.getEnterTime().before(monthStart))
                .map(AnalyticsPageView::getUserId)
                .filter(Objects::nonNull)
                .distinct().count();

        result.put("dau", dau);
        result.put("wau", wau);
        result.put("mau", mau);

        Map<String, Long> roleDistribution = views.stream()
                .filter(v -> v.getUserRole() != null)
                .collect(Collectors.groupingBy(AnalyticsPageView::getUserRole,
                        Collectors.collectingAndThen(
                                Collectors.mapping(AnalyticsPageView::getUserId, Collectors.toSet()),
                                set -> (long) set.size())));
        result.put("roleDistribution", roleDistribution);

        return result;
    }

    /**
     * A6: 接口效率 — group API performance by apiPath, calculate P95 responseMs
     */
    public List<Map<String, Object>> getEfficiency(Date dateFrom, Date dateTo) {
        LambdaQueryWrapper<AnalyticsPerformance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnalyticsPerformance::getSourceType, "API");
        if (dateFrom != null) wrapper.ge(AnalyticsPerformance::getRecordTime, dateFrom);
        if (dateTo != null) wrapper.le(AnalyticsPerformance::getRecordTime, dateTo);
        List<AnalyticsPerformance> perfs = performanceMapper.selectList(wrapper);

        Map<String, List<AnalyticsPerformance>> grouped = perfs.stream()
                .filter(p -> p.getApiPath() != null)
                .collect(Collectors.groupingBy(AnalyticsPerformance::getApiPath));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<AnalyticsPerformance>> entry : grouped.entrySet()) {
            Map<String, Object> row = new HashMap<>();
            List<AnalyticsPerformance> list = entry.getValue();
            row.put("apiPath", entry.getKey());
            row.put("callCount", list.size());

            List<Integer> responseTimes = list.stream()
                    .map(AnalyticsPerformance::getResponseMs)
                    .filter(Objects::nonNull)
                    .sorted()
                    .collect(Collectors.toList());

            if (!responseTimes.isEmpty()) {
                double avg = responseTimes.stream().mapToInt(i -> i).average().orElse(0);
                row.put("avgResponseMs", Math.round(avg));
                int p95Index = (int) Math.ceil(responseTimes.size() * 0.95) - 1;
                if (p95Index < 0) p95Index = 0;
                row.put("p95ResponseMs", responseTimes.get(p95Index));
            } else {
                row.put("avgResponseMs", 0);
                row.put("p95ResponseMs", 0);
            }

            long errorCount = list.stream().filter(p -> Boolean.TRUE.equals(p.getIsError())).count();
            row.put("errorCount", errorCount);
            row.put("errorRate", list.size() > 0 ? Math.round((double) errorCount / list.size() * 100) : 0);

            result.add(row);
        }
        result.sort((a, b) -> Integer.compare((int) b.get("p95ResponseMs"), (int) a.get("p95ResponseMs")));
        return result;
    }

    /**
     * Performance overview for dashboard
     */
    public Map<String, Object> getPerformanceOverview() {
        Map<String, Object> result = new HashMap<>();

        LambdaQueryWrapper<AnalyticsPerformance> pageWrapper = new LambdaQueryWrapper<>();
        pageWrapper.eq(AnalyticsPerformance::getSourceType, "PAGE");
        List<AnalyticsPerformance> pages = performanceMapper.selectList(pageWrapper);

        long healthyPages = pages.stream()
                .filter(p -> p.getFcpMs() != null && p.getFcpMs() < 3000)
                .count();
        result.put("pageHealthPct", pages.isEmpty() ? 100 : Math.round((double) healthyPages / pages.size() * 100));
        result.put("totalPageSamples", pages.size());

        LambdaQueryWrapper<AnalyticsPerformance> apiWrapper = new LambdaQueryWrapper<>();
        apiWrapper.eq(AnalyticsPerformance::getSourceType, "API");
        List<AnalyticsPerformance> apis = performanceMapper.selectList(apiWrapper);

        long healthyApis = apis.stream()
                .filter(p -> p.getResponseMs() != null && p.getResponseMs() < 2000)
                .count();
        result.put("apiHealthPct", apis.isEmpty() ? 100 : Math.round((double) healthyApis / apis.size() * 100));
        result.put("totalApiSamples", apis.size());

        result.put("recentAlerts", getRecentAlerts(5));

        return result;
    }

    /**
     * Recent alerts — performance records with WARNING or CRITICAL level
     */
    public List<AnalyticsPerformance> getRecentAlerts(int limit) {
        LambdaQueryWrapper<AnalyticsPerformance> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(AnalyticsPerformance::getAlertLevel, "WARNING", "CRITICAL");
        wrapper.orderByDesc(AnalyticsPerformance::getRecordTime);
        wrapper.last("OFFSET 0 ROWS FETCH NEXT " + limit + " ROWS ONLY");
        return performanceMapper.selectList(wrapper);
    }
}
