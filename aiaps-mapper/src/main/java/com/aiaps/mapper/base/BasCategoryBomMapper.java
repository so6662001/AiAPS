package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasCategoryBom;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface BasCategoryBomMapper extends BaseMapper<BasCategoryBom> {

    List<BasCategoryBom> selectByParentCategory(@Param("parentCategory") String parentCategory);

    List<BasCategoryBom> selectByChildCategory(@Param("childCategory") String childCategory);

    List<BasCategoryBom> selectAllActive();
}
