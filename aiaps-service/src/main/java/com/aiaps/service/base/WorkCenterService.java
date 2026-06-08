package com.aiaps.service.base;

import com.aiaps.common.exception.BizException;
import com.aiaps.common.result.PageResult;
import com.aiaps.domain.base.BasWorkCenter;
import com.aiaps.mapper.base.BasWorkCenterMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class WorkCenterService {

    private final BasWorkCenterMapper workCenterMapper;

    public PageResult<BasWorkCenter> page(int pageNum, int pageSize, String wcType) {
        LambdaQueryWrapper<BasWorkCenter> wrapper = new LambdaQueryWrapper<>();
        if (wcType != null && !wcType.isEmpty()) {
            wrapper.eq(BasWorkCenter::getWcType, wcType);
        }
        wrapper.eq(BasWorkCenter::getIsActive, true);
        wrapper.orderByAsc(BasWorkCenter::getWcCode);

        Page<BasWorkCenter> page = workCenterMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);

        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public BasWorkCenter getById(Long id) {
        BasWorkCenter wc = workCenterMapper.selectById(id);
        if (wc == null) {
            throw new BizException("工作中心不存在: " + id);
        }
        return wc;
    }

    @Transactional
    public void create(BasWorkCenter workCenter) {
        workCenter.setIsActive(true);
        workCenter.setCreatedTime(new Date());
        workCenterMapper.insert(workCenter);
    }

    @Transactional
    public void update(BasWorkCenter workCenter) {
        workCenterMapper.updateById(workCenter);
    }
}
