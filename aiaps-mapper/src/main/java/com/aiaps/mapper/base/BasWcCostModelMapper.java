package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasWcCostModel;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BasWcCostModelMapper extends BaseMapper<BasWcCostModel> {

    BasWcCostModel selectByWcId(@Param("wcId") Long wcId);
}
