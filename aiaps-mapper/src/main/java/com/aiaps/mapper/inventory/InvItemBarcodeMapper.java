package com.aiaps.mapper.inventory;

import com.aiaps.domain.inventory.InvItemBarcode;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InvItemBarcodeMapper extends BaseMapper<InvItemBarcode> {

    InvItemBarcode selectByBarcode(@Param("itemBarcode") String itemBarcode);
}
