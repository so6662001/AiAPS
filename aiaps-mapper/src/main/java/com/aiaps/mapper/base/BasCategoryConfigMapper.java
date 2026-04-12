package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasCategoryConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BasCategoryConfigMapper extends BaseMapper<BasCategoryConfig> {

    BasCategoryConfig selectByCategory(@Param("categoryCode") String categoryCode);
}
