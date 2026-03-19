package com.aiaps.service.base;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.base.BasBomDetail;
import com.aiaps.domain.base.BasBomHead;
import com.aiaps.domain.base.BasCategoryBom;
import com.aiaps.domain.base.BasSpecFormula;
import com.aiaps.mapper.base.BasBomDetailMapper;
import com.aiaps.mapper.base.BasBomHeadMapper;
import com.aiaps.mapper.base.BasCategoryBomMapper;
import com.aiaps.mapper.base.BasSpecFormulaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BomService {

    private final BasBomHeadMapper bomHeadMapper;
    private final BasBomDetailMapper bomDetailMapper;
    private final BasCategoryBomMapper categoryBomMapper;
    private final BasSpecFormulaMapper specFormulaMapper;

    public BasBomHead getDefaultBom(Long prdtId) {
        List<BasBomHead> boms = bomHeadMapper.selectDefaultByPrdtId(prdtId);
        if (boms == null || boms.isEmpty()) {
            return null;
        }
        return boms.get(0);
    }

    public List<BasBomDetail> getBomDetails(Long bomId) {
        return bomDetailMapper.selectByBomId(bomId);
    }

    public List<BasCategoryBom> getAllCategoryBoms() {
        return categoryBomMapper.selectAllActive();
    }

    public BasSpecFormula getFormula(String formulaCode) {
        return specFormulaMapper.selectByCode(formulaCode);
    }

    public BasBomHead getBomTree(Long prdtId) {
        BasBomHead head = getDefaultBom(prdtId);
        if (head == null) {
            throw new BizException("物料默认BOM不存在: prdtId=" + prdtId);
        }
        List<BasBomDetail> details = bomDetailMapper.selectByBomId(head.getBomId());
        head.setDetails(details);
        return head;
    }
}
