package com.aiaps.mapper.aps;

import com.aiaps.domain.aps.ApsFurnaceChargeLayer;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ApsFurnaceChargeLayerMapper extends BaseMapper<ApsFurnaceChargeLayer> {

    List<ApsFurnaceChargeLayer> selectByChargeId(@Param("chargeId") Long chargeId);
}
