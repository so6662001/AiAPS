package com.aiaps.service.mrp;

import com.aiaps.domain.base.BasCategoryBom;
import com.aiaps.domain.base.BasMaterial;
import com.aiaps.domain.base.BasSpecFormula;
import com.aiaps.domain.demand.DemDemandLine;
import com.aiaps.domain.inventory.InvStock;
import com.aiaps.mapper.base.BasCategoryBomMapper;
import com.aiaps.mapper.base.BasMaterialMapper;
import com.aiaps.mapper.base.BasSpecFormulaMapper;
import com.aiaps.mapper.demand.DemDemandLineMapper;
import com.aiaps.mapper.inventory.InvStockMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReverseMatchEngine {

    private final InvStockMapper stockMapper;
    private final BasCategoryBomMapper categoryBomMapper;
    private final BasMaterialMapper materialMapper;
    private final BasSpecFormulaMapper specFormulaMapper;
    private final DemDemandLineMapper demandLineMapper;
    private final SpecCalculationEngine specEngine;

    @Data
    public static class MatchResult {
        private Long rawStockId;
        private String rawResNo;
        private String rawSpec;
        private String patName;
        private String paName;
        private BigDecimal rawWeight;
        private List<ProductOption> productOptions = new ArrayList<>();
    }

    @Data
    public static class ProductOption {
        private Long productPrdtId;
        private String productName;
        private String productSpec;
        private BigDecimal producibleWeight;
        private BigDecimal yieldRate;
        private List<MatchedDemand> matchedDemands = new ArrayList<>();
        private BigDecimal totalDemandWeight;
        private int demandPriority;
    }

    @Data
    public static class MatchedDemand {
        private Long demandLineId;
        private String contractNo;
        private String customerName;
        private BigDecimal requiredWeight;
        private String demandSource;
        private Date requiredDate;
    }

    /**
     * For given raw material stock items, find all products that can be produced
     * and match to existing demands
     */
    public List<MatchResult> reverseMatch(List<Long> rawStockIds) {
        List<MatchResult> results = new ArrayList<>();

        for (Long stockId : rawStockIds) {
            InvStock rawStock = stockMapper.selectById(stockId);
            if (rawStock == null) continue;

            BigDecimal available = rawStock.getOnHandWeight();
            if (available == null || available.compareTo(BigDecimal.ZERO) <= 0) continue;

            BasMaterial rawMaterial = materialMapper.selectById(rawStock.getPrdtId());
            if (rawMaterial == null) continue;

            MatchResult result = new MatchResult();
            result.setRawStockId(stockId);
            result.setRawResNo(rawStock.getResNo());
            result.setRawSpec(rawMaterial.getSpecDesc());
            result.setPatName(rawStock.getPatName());
            result.setPaName(rawStock.getPaName());
            result.setRawWeight(available);

            String rawCategory = rawMaterial.getCategoryCode();
            List<BasCategoryBom> parentBoms = categoryBomMapper.selectByChildCategory(rawCategory);

            for (BasCategoryBom bom : parentBoms) {
                String parentCategory = bom.getParentCategory();
                BasSpecFormula formula = null;
                if (bom.getCalcFormulaCode() != null) {
                    formula = specFormulaMapper.selectByCode(bom.getCalcFormulaCode());
                }

                List<BasMaterial> products = materialMapper.selectByCategory(parentCategory);

                for (BasMaterial product : products) {
                    if (formula != null) {
                        try {
                            SpecCalculationEngine.RawMaterialSpec spec =
                                specEngine.calculate(product, BigDecimal.ONE, formula, bom);

                            boolean widthMatch = true;
                            boolean thicknessMatch = true;

                            if (spec.getWidthMin() != null && rawMaterial.getWidth() != null) {
                                widthMatch = rawMaterial.getWidth().compareTo(spec.getWidthMin()) >= 0
                                          && rawMaterial.getWidth().compareTo(spec.getWidthMax()) <= 0;
                            }
                            if (spec.getThicknessMin() != null && rawMaterial.getThickness() != null) {
                                thicknessMatch = rawMaterial.getThickness().compareTo(spec.getThicknessMin()) >= 0
                                              && rawMaterial.getThickness().compareTo(spec.getThicknessMax()) <= 0;
                            }

                            if (widthMatch && thicknessMatch) {
                                ProductOption option = new ProductOption();
                                option.setProductPrdtId(product.getPrdtId());
                                option.setProductName(product.getPrdtName());
                                option.setProductSpec(product.getSpecDesc());

                                BigDecimal yield = bom.getYieldRate() != null ? bom.getYieldRate() : new BigDecimal("0.96");
                                option.setYieldRate(yield);
                                option.setProducibleWeight(available.multiply(yield).setScale(3, RoundingMode.HALF_UP));

                                List<DemDemandLine> demands = demandLineMapper.selectOpenDemands(
                                    product.getPrdtId(), rawStock.getPatName());

                                BigDecimal totalDemand = BigDecimal.ZERO;
                                for (DemDemandLine d : demands) {
                                    MatchedDemand md = new MatchedDemand();
                                    md.setDemandLineId(d.getDemandLineId());
                                    md.setContractNo(d.getContractNo());
                                    md.setRequiredWeight(d.getRequiredWeight());
                                    md.setRequiredDate(d.getRequiredDate());
                                    option.getMatchedDemands().add(md);
                                    if (d.getRequiredWeight() != null) totalDemand = totalDemand.add(d.getRequiredWeight());
                                }
                                option.setTotalDemandWeight(totalDemand);
                                option.setDemandPriority(demands.isEmpty() ? 10 :
                                    ("MTO".equals(demands.get(0).getLineStatus()) ? 90 : 50));

                                result.getProductOptions().add(option);
                            }
                        } catch (Exception e) {
                            log.debug("Spec calculation failed for product {} with raw {}: {}",
                                product.getPrdtId(), stockId, e.getMessage());
                        }
                    }
                }
            }

            result.getProductOptions().sort(Comparator
                .comparing((ProductOption o) -> o.getMatchedDemands().isEmpty() ? 1 : 0)
                .thenComparing(Comparator.comparingInt(ProductOption::getDemandPriority).reversed()));

            results.add(result);
        }

        return results;
    }
}
