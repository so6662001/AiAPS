package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasMaterial;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface BasMaterialMapper extends BaseMapper<BasMaterial> {

    List<BasMaterial> selectByCategory(@Param("categoryCode") String categoryCode);

    List<BasMaterial> selectByCategoryAndSpec(
            @Param("categoryCode") String categoryCode,
            @Param("widthMin") java.math.BigDecimal widthMin,
            @Param("widthMax") java.math.BigDecimal widthMax,
            @Param("thicknessMin") java.math.BigDecimal thicknessMin,
            @Param("thicknessMax") java.math.BigDecimal thicknessMax);
}
