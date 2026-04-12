package com.aiaps.mapper.aps;

import com.aiaps.domain.aps.ApsChangeLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ApsChangeLogMapper extends BaseMapper<ApsChangeLog> {

    List<ApsChangeLog> selectByRelated(@Param("relatedId") Long relatedId, @Param("scope") String scope);
}
