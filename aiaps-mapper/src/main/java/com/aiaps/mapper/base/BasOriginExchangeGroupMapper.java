package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasOriginExchangeGroup;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BasOriginExchangeGroupMapper extends BaseMapper<BasOriginExchangeGroup> {

    boolean isInSameGroup(@Param("origin1") String origin1, @Param("origin2") String origin2);
}
