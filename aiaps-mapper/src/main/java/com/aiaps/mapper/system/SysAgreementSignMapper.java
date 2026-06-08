package com.aiaps.mapper.system;

import com.aiaps.domain.system.SysAgreementSign;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysAgreementSignMapper extends BaseMapper<SysAgreementSign> {

    SysAgreementSign selectActiveByEnterprise(@Param("enterpriseId") Long enterpriseId, @Param("type") String type);
}
