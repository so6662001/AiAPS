package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasFurnace;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface BasFurnaceMapper extends BaseMapper<BasFurnace> {

    List<BasFurnace> selectByStatus(@Param("status") String status);
}
