package com.aiaps.service.base;

import com.aiaps.common.exception.BizException;
import com.aiaps.common.result.PageResult;
import com.aiaps.domain.base.BasMaterial;
import com.aiaps.mapper.base.BasMaterialMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class MaterialService {

    private final BasMaterialMapper materialMapper;

    public PageResult<BasMaterial> page(int pageNum, int pageSize,
                                         String categoryCode, String keyword) {
        LambdaQueryWrapper<BasMaterial> wrapper = new LambdaQueryWrapper<>();
        if (categoryCode != null && !categoryCode.isEmpty()) {
            wrapper.eq(BasMaterial::getCategoryCode, categoryCode);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w
                    .like(BasMaterial::getPrdtNo, keyword)
                    .or().like(BasMaterial::getPrdtName, keyword)
                    .or().like(BasMaterial::getSpecDesc, keyword));
        }
        wrapper.eq(BasMaterial::getIsActive, true);
        wrapper.orderByDesc(BasMaterial::getCreatedTime);

        Page<BasMaterial> page = materialMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);

        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public BasMaterial getById(Long id) {
        BasMaterial material = materialMapper.selectById(id);
        if (material == null) {
            throw new BizException("物料不存在: " + id);
        }
        return material;
    }

    @Transactional
    public void create(BasMaterial material) {
        material.setIsActive(true);
        material.setCreatedTime(new Date());
        materialMapper.insert(material);
    }

    @Transactional
    public void update(BasMaterial material) {
        material.setUpdatedTime(new Date());
        materialMapper.updateById(material);
    }

    @Transactional
    public void delete(Long id) {
        BasMaterial material = new BasMaterial();
        material.setPrdtId(id);
        material.setIsActive(false);
        material.setUpdatedTime(new Date());
        materialMapper.updateById(material);
    }
}
