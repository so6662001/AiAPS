package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasBomHead;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface BasBomHeadMapper extends BaseMapper<BasBomHead> {

    List<BasBomHead> selectDefaultByPrdtId(@Param("prdtId") Long prdtId);

    List<BasBomHead> selectAllActiveWithDetails();
}
