package com.aiaps.mapper.inventory;

import com.aiaps.domain.inventory.InvStock;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface InvStockMapper extends BaseMapper<InvStock> {

    List<InvStock> selectAvailable(
            @Param("prdtId") Long prdtId,
            @Param("patName") String patName,
            @Param("paName") String paName);

    BigDecimal selectAvailableWeight(
            @Param("prdtId") Long prdtId,
            @Param("patName") String patName,
            @Param("paName") String paName);

    List<InvStock> selectByMaterialWithAnyGradeOrigin(@Param("prdtId") Long prdtId);
}
