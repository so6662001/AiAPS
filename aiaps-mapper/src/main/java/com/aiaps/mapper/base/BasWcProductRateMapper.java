package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasWcProductRate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface BasWcProductRateMapper extends BaseMapper<BasWcProductRate> {

    BasWcProductRate selectRate(@Param("wcId") Long wcId, @Param("prdtId") Long prdtId);

    List<BasWcProductRate> selectByWcId(@Param("wcId") Long wcId);
}
