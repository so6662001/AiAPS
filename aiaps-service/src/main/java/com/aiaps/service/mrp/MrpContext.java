package com.aiaps.service.mrp;

import com.aiaps.domain.base.BasBomHead;
import com.aiaps.domain.base.BasCategoryBom;
import com.aiaps.domain.base.BasMaterial;
import com.aiaps.domain.base.BasSpecFormula;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class MrpContext {

    private Map<Long, BasMaterial> materialMap = new HashMap<>();
    private Map<Long, BasBomHead> bomMap = new HashMap<>();
    private List<BasCategoryBom> categoryBoms = new ArrayList<>();
    private Map<String, BasSpecFormula> formulaMap = new HashMap<>();
    private List<PlannedOrderDto> plannedOrders = new ArrayList<>();

    @Data
    public static class PlannedOrderDto {
        private Long planOrderId;
        private Long runId;
        private String planOrderNo;
        private Long prdtId;
        private String patName;
        private String paName;
        private Boolean gradeFlexible;
        private Boolean originFlexible;
        private String orderType;
        private BigDecimal plannedQty;
        private BigDecimal plannedWeight;
        private Date plannedStartDate;
        private Date plannedEndDate;
        private String demandSource;
        private Long sourceDemandId;
        private Long sourceDemandLine;
        private Long parentPlanOrder;
        private Integer bomLevel;
        private String orderStatus;
        private Boolean isFirmed;
        private String contractNo;
        private BigDecimal planLength;
        private String lengthType;
        private String lengthDisplay;
        private Boolean isCategoryBom;
        private String rawCategoryCode;
        private BigDecimal rawWidthMin;
        private BigDecimal rawWidthMax;
        private BigDecimal rawThicknessMin;
        private BigDecimal rawThicknessMax;
        private String rawGradeCode;
        private String rawOriginCode;
        private Boolean rawGradeFlexible;
        private Boolean rawOriginFlexible;
        private Long matchedMaterialId;
        private Long matchedStockId;
        private String matchedCoilNo;
        private String matchedGradeCode;
        private String matchedOriginCode;
        private String matchStatus;
        private String remark;
        private Date createdTime;
    }

    public BasMaterial getMaterial(Long id) {
        return materialMap.get(id);
    }

    public BasBomHead getDefaultBom(Long prdtId) {
        return bomMap.get(prdtId);
    }

    public BigDecimal getSafetyStockWeight(Long prdtId, String patName) {
        BasMaterial material = materialMap.get(prdtId);
        if (material == null || material.getSafetyStockQty() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal safetyQty = material.getSafetyStockQty();
        if (material.getTheoryWeightPerPc() != null) {
            return safetyQty.multiply(material.getTheoryWeightPerPc());
        }
        return safetyQty;
    }

    public void addPlannedOrder(PlannedOrderDto order) {
        plannedOrders.add(order);
    }

    public List<BasCategoryBom> getCategoryBoms(String parentCategory) {
        return categoryBoms.stream()
                .filter(cb -> parentCategory.equals(cb.getParentCategory()))
                .collect(Collectors.toList());
    }
}
