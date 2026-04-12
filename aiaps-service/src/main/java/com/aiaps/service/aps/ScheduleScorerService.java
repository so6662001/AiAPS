package com.aiaps.service.aps;

import com.aiaps.domain.base.BasScheduleStrategy;
import com.aiaps.domain.base.BasWcCostModel;
import com.aiaps.domain.base.BasWcProductRate;
import com.aiaps.domain.base.BasWorkCenter;
import com.aiaps.domain.aps.ApsScheduleOper;
import com.aiaps.mapper.base.BasWcCostModelMapper;
import com.aiaps.mapper.base.BasWcProductRateMapper;
import com.aiaps.mapper.base.BasScheduleStrategyMapper;
import com.aiaps.mapper.base.BasWorkCenterMapper;
import com.aiaps.mapper.aps.ApsScheduleOperMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleScorerService {

    private final BasWcCostModelMapper costModelMapper;
    private final BasWcProductRateMapper productRateMapper;
    private final BasScheduleStrategyMapper strategyMapper;
    private final BasWorkCenterMapper workCenterMapper;
    private final ApsScheduleOperMapper scheduleOperMapper;

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    @Data
    public static class ScoreResult {
        private Long wcId;
        private String wcName;
        private BigDecimal estimatedHours;
        private Date estimatedStart;
        private Date estimatedEnd;
        private BigDecimal estimatedCost;
        private BigDecimal estimatedYieldRate;
        private double deliveryScore;
        private double costScore;
        private double efficiencyScore;
        private double qualityScore;
        private double totalScore;
    }

    public List<ScoreResult> scoreAllOptions(Long prdtId, BigDecimal plannedWeight, Date dueDate, String strategyCode) {
        BasScheduleStrategy strategy = loadStrategy(strategyCode);

        BigDecimal wDelivery = strategy.getWeightDelivery() != null ? strategy.getWeightDelivery() : BigDecimal.valueOf(25);
        BigDecimal wCost = strategy.getWeightCost() != null ? strategy.getWeightCost() : BigDecimal.valueOf(25);
        BigDecimal wEfficiency = strategy.getWeightEfficiency() != null ? strategy.getWeightEfficiency() : BigDecimal.valueOf(25);
        BigDecimal wQuality = strategy.getWeightQuality() != null ? strategy.getWeightQuality() : BigDecimal.valueOf(25);

        List<BasWcProductRate> allRates = productRateMapper.selectList(
                new LambdaQueryWrapper<BasWcProductRate>()
                        .eq(BasWcProductRate::getMaterialId, prdtId)
                        .eq(BasWcProductRate::getIsActive, true));

        List<ScoreResult> results = new ArrayList<>();
        for (BasWcProductRate rate : allRates) {
            ScoreResult sr = buildScoreResult(rate, plannedWeight);
            if (sr == null) continue;

            if (dueDate != null) {
                long daysLate = (sr.getEstimatedEnd().getTime() - dueDate.getTime()) / DAY_MS;
                sr.setDeliveryScore(Math.max(0, 100 - Math.max(0, daysLate) * 10.0));
            } else {
                sr.setDeliveryScore(100);
            }

            BigDecimal costPerTon = plannedWeight.compareTo(BigDecimal.ZERO) > 0
                    ? sr.getEstimatedCost().divide(plannedWeight, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            sr.setCostScore(Math.max(0, 100 - (costPerTon.doubleValue() - 200) * 0.5));
            sr.setEfficiencyScore(Math.max(0, 100 - sr.getEstimatedHours().doubleValue() * 5));
            sr.setQualityScore(sr.getEstimatedYieldRate().doubleValue() * 100);

            double total = sr.getDeliveryScore() * wDelivery.doubleValue() / 100
                    + sr.getCostScore() * wCost.doubleValue() / 100
                    + sr.getEfficiencyScore() * wEfficiency.doubleValue() / 100
                    + sr.getQualityScore() * wQuality.doubleValue() / 100;
            sr.setTotalScore(total);

            results.add(sr);
        }

        results.sort(Comparator.comparingDouble(ScoreResult::getTotalScore).reversed());
        return results;
    }

    public List<ScoreResult> estimateCompletion(Long prdtId, BigDecimal plannedWeight) {
        List<BasWcProductRate> allRates = productRateMapper.selectList(
                new LambdaQueryWrapper<BasWcProductRate>()
                        .eq(BasWcProductRate::getMaterialId, prdtId)
                        .eq(BasWcProductRate::getIsActive, true));

        List<ScoreResult> results = new ArrayList<>();
        for (BasWcProductRate rate : allRates) {
            ScoreResult sr = buildScoreResult(rate, plannedWeight);
            if (sr != null) {
                results.add(sr);
            }
        }
        return results;
    }

    private ScoreResult buildScoreResult(BasWcProductRate rate, BigDecimal plannedWeight) {
        BasWorkCenter wc = workCenterMapper.selectById(rate.getWcId());
        if (wc == null) return null;

        BigDecimal capacityPerHour = rate.getCapacityPerHour();
        if (capacityPerHour == null || capacityPerHour.compareTo(BigDecimal.ZERO) <= 0) return null;

        BigDecimal productionHours = plannedWeight.divide(capacityPerHour, 4, RoundingMode.HALF_UP);
        int setupMin = rate.getSetupTimeMinutes() != null ? rate.getSetupTimeMinutes() : 0;
        BigDecimal totalHours = productionHours.add(BigDecimal.valueOf(setupMin).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP));

        BasWcCostModel costModel = costModelMapper.selectByWcId(rate.getWcId());
        BigDecimal totalCost = BigDecimal.ZERO;
        if (costModel != null) {
            BigDecimal startup = costModel.getStartupCost() != null ? costModel.getStartupCost() : BigDecimal.ZERO;
            BigDecimal labor = costModel.getLaborCostPerHour() != null ? costModel.getLaborCostPerHour() : BigDecimal.ZERO;
            BigDecimal power = costModel.getPowerCostPerTon() != null ? costModel.getPowerCostPerTon() : BigDecimal.ZERO;
            BigDecimal gas = costModel.getGasCostPerTon() != null ? costModel.getGasCostPerTon() : BigDecimal.ZERO;
            BigDecimal consumable = costModel.getConsumablePerTon() != null ? costModel.getConsumablePerTon() : BigDecimal.ZERO;
            totalCost = startup
                    .add(labor.multiply(totalHours))
                    .add(power.multiply(plannedWeight))
                    .add(gas.multiply(plannedWeight))
                    .add(consumable.multiply(plannedWeight));
        }

        BigDecimal yieldRate = rate.getExpectedYieldRate() != null ? rate.getExpectedYieldRate() : BigDecimal.ONE;

        Date earliestStart = findEarliestAvailable(rate.getWcId());
        long durationMs = totalHours.multiply(BigDecimal.valueOf(3600000)).longValue();
        Date estimatedEnd = new Date(earliestStart.getTime() + durationMs);

        ScoreResult sr = new ScoreResult();
        sr.setWcId(rate.getWcId());
        sr.setWcName(wc.getWcName());
        sr.setEstimatedHours(totalHours.setScale(2, RoundingMode.HALF_UP));
        sr.setEstimatedStart(earliestStart);
        sr.setEstimatedEnd(estimatedEnd);
        sr.setEstimatedCost(totalCost.setScale(2, RoundingMode.HALF_UP));
        sr.setEstimatedYieldRate(yieldRate);
        return sr;
    }

    private Date findEarliestAvailable(Long wcId) {
        List<ApsScheduleOper> opers = scheduleOperMapper.selectList(
                new LambdaQueryWrapper<ApsScheduleOper>()
                        .eq(ApsScheduleOper::getWcId, wcId)
                        .isNotNull(ApsScheduleOper::getOperEnd)
                        .orderByDesc(ApsScheduleOper::getOperEnd)
                        .last("LIMIT 1"));
        if (opers != null && !opers.isEmpty() && opers.get(0).getOperEnd() != null) {
            return opers.get(0).getOperEnd();
        }
        return new Date();
    }

    private BasScheduleStrategy loadStrategy(String strategyCode) {
        if (strategyCode != null && !strategyCode.isEmpty()) {
            List<BasScheduleStrategy> list = strategyMapper.selectList(
                    new LambdaQueryWrapper<BasScheduleStrategy>()
                            .eq(BasScheduleStrategy::getStrategyCode, strategyCode)
                            .eq(BasScheduleStrategy::getIsActive, true));
            if (!list.isEmpty()) return list.get(0);
        }
        BasScheduleStrategy def = strategyMapper.selectDefault();
        if (def != null) return def;

        BasScheduleStrategy fallback = new BasScheduleStrategy();
        fallback.setWeightDelivery(BigDecimal.valueOf(25));
        fallback.setWeightCost(BigDecimal.valueOf(25));
        fallback.setWeightEfficiency(BigDecimal.valueOf(25));
        fallback.setWeightQuality(BigDecimal.valueOf(25));
        return fallback;
    }
}
