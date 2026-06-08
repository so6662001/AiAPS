package com.aiaps.mapper.production;

import com.aiaps.domain.production.PrdReprocess;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PrdReprocessMapper extends BaseMapper<PrdReprocess> {

    List<PrdReprocess> selectBySourceScheduleId(@Param("scheduleId") Long scheduleId);
}
