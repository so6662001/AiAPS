package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasOriginTier;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BasOriginTierMapper extends BaseMapper<BasOriginTier> {

    BasOriginTier selectByOriginCode(@Param("originCode") String originCode);
}
