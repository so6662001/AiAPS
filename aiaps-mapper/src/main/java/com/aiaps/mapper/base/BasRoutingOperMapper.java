package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasRoutingOper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface BasRoutingOperMapper extends BaseMapper<BasRoutingOper> {

    List<BasRoutingOper> selectByRoutingId(@Param("routingId") Long routingId);
}
