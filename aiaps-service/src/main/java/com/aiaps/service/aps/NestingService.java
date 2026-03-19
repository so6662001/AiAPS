package com.aiaps.service.aps;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.aps.ApsNestingDetail;
import com.aiaps.domain.aps.ApsNestingPlan;
import com.aiaps.domain.inventory.InvStock;
import com.aiaps.mapper.aps.ApsNestingDetailMapper;
import com.aiaps.mapper.aps.ApsNestingPlanMapper;
import com.aiaps.mapper.inventory.InvStockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NestingService {

    private final ApsNestingPlanMapper nestingPlanMapper;
    private final ApsNestingDetailMapper nestingDetailMapper;
    private final InvStockMapper stockMapper;

    @Transactional
    public void createNestingPlan(ApsNestingPlan plan, List<ApsNestingDetail> details) {
        if (plan.getNestingStatus() == null) {
            plan.setNestingStatus("DRAFT");
        }
        plan.setCreatedTime(new Date());
        nestingPlanMapper.insert(plan);

        if (details != null) {
            for (int i = 0; i < details.size(); i++) {
                ApsNestingDetail detail = details.get(i);
                detail.setNestingId(plan.getNestingId());
                if (detail.getLineNo() == null) {
                    detail.setLineNo(i + 1);
                }
                nestingDetailMapper.insert(detail);
            }
        }
    }

    @Transactional
    public ApsNestingPlan optimizeSlit(Long sourceStockId, List<BigDecimal> requiredWidths) {
        InvStock stock = stockMapper.selectById(sourceStockId);
        if (stock == null) {
            throw new BizException("库存记录不存在: " + sourceStockId);
        }

        BigDecimal sourceWidth = stock.getActualWidth();
        if (sourceWidth == null || sourceWidth.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("原材料宽度无效");
        }

        List<BigDecimal> sorted = new ArrayList<>(requiredWidths);
        sorted.sort(Comparator.reverseOrder());

        BigDecimal remainingWidth = sourceWidth;
        BigDecimal kerfWidth = new BigDecimal("3");
        BigDecimal edgeTrim = new BigDecimal("5");

        remainingWidth = remainingWidth.subtract(edgeTrim.multiply(new BigDecimal("2")));

        ApsNestingPlan plan = new ApsNestingPlan();
        plan.setNestingNo("NST" + System.currentTimeMillis());
        plan.setNestingType("SLIT");
        plan.setSourceStockId(sourceStockId);
        plan.setSourceWidth(sourceWidth);
        plan.setSourceThickness(stock.getActualThickness());
        plan.setSourceWeight(stock.getOnHandWeight());
        plan.setEdgeTrim(edgeTrim);
        plan.setKerfWidth(kerfWidth);
        plan.setNestingStatus("DRAFT");
        plan.setCreatedTime(new Date());

        List<ApsNestingDetail> details = new ArrayList<>();
        int lineNo = 1;
        int slitCount = 0;

        for (BigDecimal width : sorted) {
            BigDecimal needed = width.add(kerfWidth);
            if (remainingWidth.compareTo(needed) >= 0) {
                ApsNestingDetail detail = new ApsNestingDetail();
                detail.setLineNo(lineNo++);
                detail.setOutputWidth(width);
                detail.setOutputThickness(stock.getActualThickness());
                detail.setOutputCount(1);
                detail.setOutputType("PRODUCT");
                details.add(detail);

                remainingWidth = remainingWidth.subtract(needed);
                slitCount++;
            }
        }

        plan.setSlitCount(slitCount);

        BigDecimal totalUsedWidth = sourceWidth.subtract(remainingWidth).subtract(edgeTrim.multiply(new BigDecimal("2")));
        if (sourceWidth.compareTo(BigDecimal.ZERO) > 0) {
            plan.setUtilizationPct(totalUsedWidth.divide(sourceWidth, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")));
        }

        nestingPlanMapper.insert(plan);

        for (ApsNestingDetail detail : details) {
            detail.setNestingId(plan.getNestingId());
            nestingDetailMapper.insert(detail);
        }

        return plan;
    }
}
