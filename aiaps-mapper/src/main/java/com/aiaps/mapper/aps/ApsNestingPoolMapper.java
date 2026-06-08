package com.aiaps.mapper.aps;

import com.aiaps.domain.aps.ApsNestingPool;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ApsNestingPoolMapper extends BaseMapper<ApsNestingPool> {
    List<ApsNestingPool> selectByRunId(@Param("runId") Long runId);
    List<ApsNestingPool> selectByGroupKey(@Param("groupKey") String groupKey, @Param("status") String status);
    List<String> selectDistinctGroupKeys(@Param("status") String status);
}
