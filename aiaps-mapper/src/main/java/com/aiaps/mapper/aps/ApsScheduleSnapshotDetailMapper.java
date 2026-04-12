package com.aiaps.mapper.aps;

import com.aiaps.domain.aps.ApsScheduleSnapshotDetail;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ApsScheduleSnapshotDetailMapper extends BaseMapper<ApsScheduleSnapshotDetail> {

    List<ApsScheduleSnapshotDetail> selectBySnapshotId(@Param("snapshotId") Long snapshotId);
}
