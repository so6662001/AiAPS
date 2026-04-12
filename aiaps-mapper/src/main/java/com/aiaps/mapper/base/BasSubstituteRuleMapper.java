package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasSubstituteRule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface BasSubstituteRuleMapper extends BaseMapper<BasSubstituteRule> {

    List<BasSubstituteRule> selectByTypeAndSource(
            @Param("ruleType") String ruleType,
            @Param("sourceCategory") String sourceCategory,
            @Param("sourceGradeCode") String sourceGradeCode);
}
