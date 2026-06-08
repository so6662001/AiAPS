package com.aiaps.mapper.system;

import com.aiaps.domain.system.SysAgreementVersion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysAgreementVersionMapper extends BaseMapper<SysAgreementVersion> {

    SysAgreementVersion selectCurrentByType(@Param("type") String type);
}
