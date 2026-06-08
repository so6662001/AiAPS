package com.aiaps.mapper.production;

import com.aiaps.domain.production.PrdMaterialSwap;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PrdMaterialSwapMapper extends BaseMapper<PrdMaterialSwap> {

    List<PrdMaterialSwap> selectByScheduleId(@Param("scheduleId") Long scheduleId);
}
