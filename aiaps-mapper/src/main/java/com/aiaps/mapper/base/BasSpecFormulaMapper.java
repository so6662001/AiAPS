package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasSpecFormula;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BasSpecFormulaMapper extends BaseMapper<BasSpecFormula> {

    BasSpecFormula selectByCode(@Param("formulaCode") String formulaCode);
}
