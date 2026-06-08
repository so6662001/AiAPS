package com.aiaps.mapper.analytics;

import com.aiaps.domain.analytics.AnalyticsPerformance;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AnalyticsPerformanceMapper extends BaseMapper<AnalyticsPerformance> {

    List<AnalyticsPerformance> selectRecentByPath(
            @Param("path") String path,
            @Param("sourceType") String sourceType,
            @Param("minutes") int minutes);
}
