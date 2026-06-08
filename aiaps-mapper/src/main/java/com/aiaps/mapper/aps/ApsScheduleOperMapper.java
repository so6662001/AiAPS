package com.aiaps.mapper.aps;

import com.aiaps.domain.aps.ApsScheduleOper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ApsScheduleOperMapper extends BaseMapper<ApsScheduleOper> {

    List<ApsScheduleOper> selectByScheduleId(@Param("scheduleId") Long scheduleId);
}
