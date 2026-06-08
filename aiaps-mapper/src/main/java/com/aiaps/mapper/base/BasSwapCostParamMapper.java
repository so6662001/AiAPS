package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasSwapCostParam;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BasSwapCostParamMapper extends BaseMapper<BasSwapCostParam> {

    BasSwapCostParam selectByWcId(@Param("wcId") Long wcId);
}
