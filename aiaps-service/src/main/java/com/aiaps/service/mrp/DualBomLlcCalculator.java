package com.aiaps.service.mrp;

import com.aiaps.domain.base.BasCategoryBom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 双轨BOM低阶码计算器
 * Phase 1: 品类BOM → 品类级LLC
 * Phase 2: 离散BOM → 物料级LLC
 * Phase 3: 合并 → 最终LLC
 */
@Slf4j
@Component
public class DualBomLlcCalculator {

    public static class BomRelation {
        public Long parentMaterialId;
        public Long childMaterialId;
    }

    /**
     * 计算双轨LLC
     * @param discreteRelations 离散BOM的父子关系
     * @param categoryBoms 品类BOM列表
     * @param materialCategoryMap 物料ID→品类编码的映射
     * @return key="M:物料ID" 或 "C:品类编码", value=LLC等级
     */
    public Map<String, Integer> calculate(
            List<BomRelation> discreteRelations,
            List<BasCategoryBom> categoryBoms,
            Map<Long, String> materialCategoryMap) {

        // Phase 1: 品类级LLC
        Map<String, Integer> categoryLlc = new HashMap<>();
        for (BasCategoryBom cb : categoryBoms) {
            categoryLlc.putIfAbsent(cb.getParentCategory(), 0);
            categoryLlc.putIfAbsent(cb.getChildCategory(), 0);
        }
        boolean changed = true;
        int iterations = 0;
        while (changed && iterations < 100) {
            changed = false;
            iterations++;
            for (BasCategoryBom cb : categoryBoms) {
                int parentLlc = categoryLlc.getOrDefault(cb.getParentCategory(), 0);
                int childLlc = categoryLlc.getOrDefault(cb.getChildCategory(), 0);
                if (parentLlc + 1 > childLlc) {
                    categoryLlc.put(cb.getChildCategory(), parentLlc + 1);
                    changed = true;
                }
            }
        }
        log.info("品类级LLC计算完成, 共{}个品类, 迭代{}次", categoryLlc.size(), iterations);

        // Phase 2: 物料级LLC
        Map<Long, Integer> materialLlc = new HashMap<>();
        for (BomRelation r : discreteRelations) {
            materialLlc.putIfAbsent(r.parentMaterialId, 0);
            materialLlc.putIfAbsent(r.childMaterialId, 0);
        }
        changed = true;
        iterations = 0;
        while (changed && iterations < 100) {
            changed = false;
            iterations++;
            for (BomRelation r : discreteRelations) {
                int parentLlc = materialLlc.getOrDefault(r.parentMaterialId, 0);
                int childLlc = materialLlc.getOrDefault(r.childMaterialId, 0);
                if (parentLlc + 1 > childLlc) {
                    materialLlc.put(r.childMaterialId, parentLlc + 1);
                    changed = true;
                }
            }
        }
        log.info("物料级LLC计算完成, 共{}个物料, 迭代{}次", materialLlc.size(), iterations);

        // Phase 3: 合并
        Map<String, Integer> finalLlc = new TreeMap<>();
        for (Map.Entry<Long, Integer> entry : materialLlc.entrySet()) {
            Long matId = entry.getKey();
            int matLlc = entry.getValue();
            String category = materialCategoryMap.get(matId);
            int catLlc = (category != null) ? categoryLlc.getOrDefault(category, 0) : 0;
            finalLlc.put("M:" + matId, Math.max(matLlc, catLlc));
        }
        for (Map.Entry<String, Integer> entry : categoryLlc.entrySet()) {
            String key = "C:" + entry.getKey();
            finalLlc.putIfAbsent(key, entry.getValue());
        }

        log.info("双轨LLC合并完成, 共{}个条目", finalLlc.size());
        return finalLlc;
    }
}
