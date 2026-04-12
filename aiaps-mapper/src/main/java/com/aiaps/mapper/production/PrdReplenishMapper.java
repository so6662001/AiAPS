package com.aiaps.mapper.production;

import com.aiaps.domain.production.PrdReplenish;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PrdReplenishMapper extends BaseMapper<PrdReplenish> {

    List<PrdReplenish> selectByScheduleId(@Param("scheduleId") Long scheduleId);
}
