package com.aiaps.mapper.analytics;

import com.aiaps.domain.analytics.AnalyticsAlertRule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AnalyticsAlertRuleMapper extends BaseMapper<AnalyticsAlertRule> {

    List<AnalyticsAlertRule> selectActiveRules();
}
