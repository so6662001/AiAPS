package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasBomDetail;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface BasBomDetailMapper extends BaseMapper<BasBomDetail> {

    List<BasBomDetail> selectByBomId(@Param("bomId") Long bomId);
}
