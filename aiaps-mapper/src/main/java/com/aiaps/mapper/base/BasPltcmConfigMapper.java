package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasPltcmConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BasPltcmConfigMapper extends BaseMapper<BasPltcmConfig> {

    BasPltcmConfig selectByWcId(@Param("wcId") Long wcId);
}
