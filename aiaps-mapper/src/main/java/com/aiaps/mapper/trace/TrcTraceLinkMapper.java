package com.aiaps.mapper.trace;

import com.aiaps.domain.trace.TrcTraceLink;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface TrcTraceLinkMapper extends BaseMapper<TrcTraceLink> {

    List<TrcTraceLink> selectBySourceCardNo(@Param("cardNo") String cardNo);

    List<TrcTraceLink> selectByTargetCardNo(@Param("cardNo") String cardNo);

    List<TrcTraceLink> selectByContract(@Param("contractNo") String contractNo);

    List<TrcTraceLink> selectBySchedule(@Param("scheduleId") Long scheduleId);
}
