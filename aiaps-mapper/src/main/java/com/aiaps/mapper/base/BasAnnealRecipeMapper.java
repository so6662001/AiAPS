package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasAnnealRecipe;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface BasAnnealRecipeMapper extends BaseMapper<BasAnnealRecipe> {

    List<BasAnnealRecipe> selectCompatible(@Param("gradeCode") String gradeCode);
}
