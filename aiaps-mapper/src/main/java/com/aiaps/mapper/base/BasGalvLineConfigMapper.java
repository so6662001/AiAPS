package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasGalvLineConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BasGalvLineConfigMapper extends BaseMapper<BasGalvLineConfig> {

    BasGalvLineConfig selectByWcId(@Param("wcId") Long wcId);
}
