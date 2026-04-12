package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasGradeCrossSubstitute;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BasGradeCrossSubstituteMapper extends BaseMapper<BasGradeCrossSubstitute> {

    BasGradeCrossSubstitute selectCross(@Param("source") String source, @Param("target") String target);
}
