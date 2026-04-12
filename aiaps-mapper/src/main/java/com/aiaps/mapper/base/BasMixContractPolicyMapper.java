package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasMixContractPolicy;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BasMixContractPolicyMapper extends BaseMapper<BasMixContractPolicy> {

    BasMixContractPolicy selectByCustomer(@Param("customerCode") String customerCode);

    BasMixContractPolicy selectGlobal();
}
