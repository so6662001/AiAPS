package com.aiaps.mapper.production;

import com.aiaps.domain.production.PrdReport;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PrdReportMapper extends BaseMapper<PrdReport> {

    List<PrdReport> selectByScheduleId(@Param("scheduleId") Long scheduleId);
}
