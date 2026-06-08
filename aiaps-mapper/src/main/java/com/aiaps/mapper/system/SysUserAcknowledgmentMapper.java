package com.aiaps.mapper.system;

import com.aiaps.domain.system.SysUserAcknowledgment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserAcknowledgmentMapper extends BaseMapper<SysUserAcknowledgment> {

    SysUserAcknowledgment selectByUserAndVersion(@Param("userId") String userId, @Param("versionId") Long versionId);
}
