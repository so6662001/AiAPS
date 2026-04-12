package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasGradeHierarchy;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface BasGradeHierarchyMapper extends BaseMapper<BasGradeHierarchy> {

    BasGradeHierarchy selectByGradeCode(@Param("gradeCode") String gradeCode);

    List<BasGradeHierarchy> selectByFamily(@Param("family") String family);
}
