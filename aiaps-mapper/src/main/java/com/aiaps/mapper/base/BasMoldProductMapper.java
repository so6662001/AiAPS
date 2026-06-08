package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasMoldProduct;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface BasMoldProductMapper extends BaseMapper<BasMoldProduct> {

    List<BasMoldProduct> selectByMoldId(@Param("moldId") Long moldId);

    List<BasMoldProduct> selectByMaterialId(@Param("prdtId") Long prdtId);
}
