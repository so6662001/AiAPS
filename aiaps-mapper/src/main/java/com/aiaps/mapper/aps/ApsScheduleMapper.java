package com.aiaps.mapper.aps;

import com.aiaps.domain.aps.ApsSchedule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ApsScheduleMapper extends BaseMapper<ApsSchedule> {

    List<ApsSchedule> selectByContract(@Param("contractNo") String contractNo);

    List<ApsSchedule> selectQueueByWc(
            @Param("wcId") Long wcId,
            @Param("status") String status);
}
