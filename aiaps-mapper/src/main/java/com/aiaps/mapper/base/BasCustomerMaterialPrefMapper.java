package com.aiaps.mapper.base;

import com.aiaps.domain.base.BasCustomerMaterialPref;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BasCustomerMaterialPrefMapper extends BaseMapper<BasCustomerMaterialPref> {

    BasCustomerMaterialPref selectByCustomer(@Param("customerCode") String customerCode);
}
