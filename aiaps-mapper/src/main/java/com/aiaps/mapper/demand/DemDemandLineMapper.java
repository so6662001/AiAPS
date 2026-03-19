package com.aiaps.mapper.demand;

import com.aiaps.domain.demand.DemDemandLine;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DemDemandLineMapper extends BaseMapper<DemDemandLine> {

    List<DemDemandLine> selectOpenDemands(
            @Param("prdtId") Long prdtId,
            @Param("patName") String patName);

    List<DemDemandLine> selectByContract(@Param("contractNo") String contractNo);
}
