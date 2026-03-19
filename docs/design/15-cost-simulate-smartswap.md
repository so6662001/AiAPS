# AiAPS — 排产策略引擎、模拟推演与上料智能调单设计

> 版本：1.0 | 最后更新：2026-03-17
>
> 覆盖三个核心增强需求：
> 1. 产线排产策略（成本优先 vs 效率优先）+ 不同机器工时差异
> 2. 排产模拟推演（调整前可视化预览全局影响）
> 3. 上料不符时的智能换料/调单决策

---

## 1. 产线排产策略引擎

### 1.1 问题描述

```
现实中不同产线/机器生产同样的产品:
  · 用时不同 (新机器快, 老机器慢)
  · 能耗不同 (大机器能耗高但产能大, 小机器能耗低但产能小)
  · 综合成本不同 (开机费+电费+人工+损耗)
  
排产时面临选择:
  · 成本优先: 选综合成本最低的产线, 可能交期晚一点
  · 效率优先: 选最快能完成的产线, 可能成本高一点
  · 交期优先: 保证交期的前提下, 再考虑成本或效率
  · 均衡策略: 在交期、成本、效率之间找平衡点

需要将这个策略做成可配置的参数, 不同产线/不同产品/不同时期可灵活切换
```

### 1.2 产线综合成本模型 (bas_wc_cost_model)

```sql
-- 每条产线/机器的综合成本模型
CREATE TABLE bas_wc_cost_model (
    cost_model_id       BIGINT IDENTITY(1,1) PRIMARY KEY,
    wc_id               BIGINT        NOT NULL,     -- 工作中心/产线
    
    -- ═══ 固定成本(每班/每天, 无论是否生产都发生) ═══
    startup_cost        DECIMAL(18,2) NOT NULL DEFAULT 0,  -- 开机费用(元/次)
    idle_cost_per_hour  DECIMAL(18,2) NOT NULL DEFAULT 0,  -- 空转成本(元/小时)
    labor_cost_per_hour DECIMAL(18,2) NOT NULL DEFAULT 0,  -- 人工成本(元/小时)
    depreciation_per_hour DECIMAL(18,2) NULL,              -- 设备折旧(元/小时)
    
    -- ═══ 变动成本(与产量相关) ═══
    power_cost_per_ton  DECIMAL(18,2) NOT NULL DEFAULT 0,  -- 电费(元/吨)
    gas_cost_per_ton    DECIMAL(18,2) NULL,                -- 气费(元/吨, 焊接用)
    consumable_per_ton  DECIMAL(18,2) NULL,                -- 耗材(元/吨, 焊丝/刀具等)
    
    -- ═══ 换产成本 ═══
    mold_change_cost    DECIMAL(18,2) NOT NULL DEFAULT 0,  -- 换模成本(元/次)
    quick_change_cost   DECIMAL(18,2) NOT NULL DEFAULT 0,  -- 快速换模成本(元/次)
    
    -- ═══ 质量成本 ═══
    avg_scrap_rate      DECIMAL(8,4)  NOT NULL DEFAULT 0.03, -- 该产线平均废品率
    scrap_cost_per_ton  DECIMAL(18,2) NULL,                 -- 废品处理成本(元/吨)
    
    effective_from      DATETIME      NULL,
    effective_to        DATETIME      NULL,
    is_active           BIT           NOT NULL DEFAULT 1,
    CONSTRAINT FK_cost_model_wc FOREIGN KEY (wc_id) 
        REFERENCES bas_work_center(wc_id)
);
```

### 1.3 产线-产品工时矩阵 (bas_wc_product_rate)

```sql
-- 不同产线生产不同产品的工时/产能差异
-- 同一产品在不同机器上的生产速度不同
CREATE TABLE bas_wc_product_rate (
    rate_id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    wc_id               BIGINT        NOT NULL,     -- 工作中心/产线
    material_id         BIGINT        NULL,         -- 具体物料(NULL=按品类)
    category_code       VARCHAR(20)   NULL,         -- 品类
    
    -- ═══ 产能参数 ═══
    capacity_per_hour   DECIMAL(18,3) NOT NULL,     -- 小时产能(吨/小时)
    setup_time_minutes  INT           NOT NULL DEFAULT 0,  -- 准备时间(分钟)
    min_batch_weight    DECIMAL(18,3) NULL,         -- 最小经济批量(吨)
    
    -- ═══ 质量参数 ═══
    expected_yield_rate DECIMAL(8,4)  NOT NULL DEFAULT 0.96, -- 预期成材率
    quality_score       INT           NULL,         -- 质量评分 1~100
    
    remark              NVARCHAR(200) NULL,
    is_active           BIT           NOT NULL DEFAULT 1,
    CONSTRAINT FK_rate_wc FOREIGN KEY (wc_id) 
        REFERENCES bas_work_center(wc_id)
);

-- 示例数据:
-- 方管100×50×4.0 在不同产线的产能差异:
-- 焊管1线(新): 5.0 T/h, 成材率96.5%, 电费42元/T
-- 焊管2线(旧): 3.5 T/h, 成材率95.0%, 电费55元/T
-- 焊管3线(小): 2.0 T/h, 成材率97.0%, 电费35元/T (小线精度高但慢)
```

### 1.4 排产策略配置 (bas_schedule_strategy)

```sql
-- 可配置的排产策略: 决定优先排哪个单/选哪条产线
CREATE TABLE bas_schedule_strategy (
    strategy_id         BIGINT IDENTITY(1,1) PRIMARY KEY,
    strategy_code       VARCHAR(30)   NOT NULL,
    strategy_name       NVARCHAR(100) NOT NULL,
    
    -- ═══ 策略类型 ═══
    strategy_type       VARCHAR(20)   NOT NULL,     -- COST=成本优先
                                                    -- SPEED=效率优先
                                                    -- DELIVERY=交期优先
                                                    -- BALANCED=均衡
                                                    -- CUSTOM=自定义权重
    
    -- ═══ 权重配置(CUSTOM模式下自定义, 其他模式有预设值) ═══
    weight_delivery     DECIMAL(5,2)  NOT NULL DEFAULT 40,  -- 交期权重(0~100)
    weight_cost         DECIMAL(5,2)  NOT NULL DEFAULT 30,  -- 成本权重
    weight_efficiency   DECIMAL(5,2)  NOT NULL DEFAULT 20,  -- 效率权重
    weight_quality      DECIMAL(5,2)  NOT NULL DEFAULT 10,  -- 质量权重
    -- 四项权重合计 = 100
    
    -- ═══ 适用范围 ═══
    apply_scope         VARCHAR(20)   NOT NULL DEFAULT 'GLOBAL',
                                                    -- GLOBAL=全局 WC=按产线 CATEGORY=按品类
    apply_wc_id         BIGINT        NULL,         -- 按产线时指定
    apply_category      VARCHAR(20)   NULL,         -- 按品类时指定
    
    is_default          BIT           NOT NULL DEFAULT 0,
    is_active           BIT           NOT NULL DEFAULT 1,
    CONSTRAINT UK_strategy_code UNIQUE (strategy_code)
);

-- 预置策略:
-- COST:      交期20 + 成本50 + 效率20 + 质量10
-- SPEED:     交期30 + 成本10 + 效率50 + 质量10
-- DELIVERY:  交期60 + 成本15 + 效率15 + 质量10
-- BALANCED:  交期30 + 成本30 + 效率25 + 质量15
```

### 1.5 排产评分算法

```java
/**
 * 排产方案评分器
 * 为每个"任务+产线"组合计算综合评分, 选最优方案
 */
public class ScheduleScorer {
    
    public static class ScoreResult {
        private Long wcId;
        private String wcName;
        
        // 各维度预估
        private BigDecimal estimatedHours;       // 预估加工时间(小时)
        private LocalDateTime estimatedStart;    // 预估最早开始时间
        private LocalDateTime estimatedEnd;      // 预估完工时间
        private BigDecimal estimatedCost;        // 预估综合成本(元)
        private BigDecimal estimatedYieldRate;   // 预估成材率
        
        // 各维度评分 (0~100)
        private double deliveryScore;   // 交期得分(越早完=越高分)
        private double costScore;       // 成本得分(越便宜=越高分)
        private double efficiencyScore; // 效率得分(越快=越高分)
        private double qualityScore;    // 质量得分(成材率高=高分)
        
        // 加权总分
        private double totalScore;
    }
    
    /**
     * 为一个排产任务计算所有可选产线的评分
     */
    public List<ScoreResult> scoreAllOptions(
            ScheduleTask task, List<Long> candidateWcIds,
            ScheduleStrategy strategy) {
        
        List<ScoreResult> results = new ArrayList<>();
        
        for (Long wcId : candidateWcIds) {
            ScoreResult r = new ScoreResult();
            r.setWcId(wcId);
            
            // 1. 计算预估加工时间
            WcProductRate rate = rateMapper.selectRate(wcId, task.getMaterialId());
            BigDecimal hours = task.getPlannedWeight()
                .divide(rate.getCapacityPerHour(), 4, RoundingMode.HALF_UP);
            BigDecimal setupHours = new BigDecimal(rate.getSetupTimeMinutes())
                .divide(new BigDecimal(60), 4, RoundingMode.HALF_UP);
            r.setEstimatedHours(hours.add(setupHours));
            
            // 2. 计算预估完工时间(找到最早可用时间槽)
            WorkCenterTimeline tl = timelineManager.getTimeline(wcId);
            TimeSlot slot = tl.findEarliestSlot(
                LocalDateTime.now(), 
                hours.add(setupHours).multiply(new BigDecimal(60)).intValue());
            r.setEstimatedStart(slot.getStart());
            r.setEstimatedEnd(slot.getEnd());
            
            // 3. 计算预估成本
            WcCostModel cost = costMapper.selectByWc(wcId);
            BigDecimal totalCost = BigDecimal.ZERO;
            totalCost = totalCost.add(cost.getStartupCost());
            totalCost = totalCost.add(cost.getLaborCostPerHour().multiply(r.getEstimatedHours()));
            totalCost = totalCost.add(cost.getPowerCostPerTon().multiply(task.getPlannedWeight()));
            if (cost.getGasCostPerTon() != null)
                totalCost = totalCost.add(cost.getGasCostPerTon().multiply(task.getPlannedWeight()));
            if (cost.getConsumablePerTon() != null)
                totalCost = totalCost.add(cost.getConsumablePerTon().multiply(task.getPlannedWeight()));
            r.setEstimatedCost(totalCost);
            
            // 4. 预估成材率
            r.setEstimatedYieldRate(rate.getExpectedYieldRate());
            
            // 5. 各维度评分
            r.setDeliveryScore(calcDeliveryScore(r.getEstimatedEnd(), task.getDueDate()));
            r.setCostScore(calcCostScore(totalCost, task.getPlannedWeight()));
            r.setEfficiencyScore(calcEfficiencyScore(r.getEstimatedHours()));
            r.setQualityScore(calcQualityScore(rate.getExpectedYieldRate()));
            
            // 6. 加权总分
            r.setTotalScore(
                r.getDeliveryScore() * strategy.getWeightDelivery() / 100
              + r.getCostScore() * strategy.getWeightCost() / 100
              + r.getEfficiencyScore() * strategy.getWeightEfficiency() / 100
              + r.getQualityScore() * strategy.getWeightQuality() / 100
            );
            
            results.add(r);
        }
        
        results.sort(Comparator.comparing(ScoreResult::getTotalScore).reversed());
        return results;
    }
    
    private double calcDeliveryScore(LocalDateTime estEnd, LocalDateTime dueDate) {
        if (dueDate == null) return 50;
        long hoursBeforeDue = Duration.between(estEnd, dueDate).toHours();
        if (hoursBeforeDue < 0) return Math.max(0, 50 + hoursBeforeDue);
        return Math.min(100, 60 + hoursBeforeDue * 0.5);
    }
    
    private double calcCostScore(BigDecimal cost, BigDecimal weight) {
        BigDecimal unitCost = cost.divide(weight, 2, RoundingMode.HALF_UP);
        // 越低越好, 假设基准200元/吨, 偏离越大分越低
        return Math.max(0, 100 - unitCost.subtract(new BigDecimal(200)).doubleValue() * 0.5);
    }
}
```

### 1.6 排产选线 UI

```
┌──────────────────────────────────────────────────────────────────────────┐
│  排产选线建议                                                            │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  任务: 方管100×50×4.0 Q235B 25T | 交期: 03-28                           │
│  当前策略: [均衡模式 ▼] (交期30% 成本30% 效率25% 质量15%)                │
│                                                                          │
│  ┌──┬──────────┬──────┬──────────┬──────┬──────┬──────┬──────┬────────┐│
│  │# │产线      │总评分│预计完工  │用时  │成本  │成材率│电费  │推荐    ││
│  ├──┼──────────┼──────┼──────────┼──────┼──────┼──────┼──────┼────────┤│
│  │1 │焊管1线   │87.5  │03-24 16h │5.0h  │¥5,850│96.5% │¥1,050│★推荐  ││
│  │  │(新,大)   │      │提前4天✅ │最快  │中等  │      │42/T  │        ││
│  ├──┼──────────┼──────┼──────────┼──────┼──────┼──────┼──────┼────────┤│
│  │2 │焊管3线   │82.3  │03-26 10h │12.5h │¥4,200│97.0% │¥875  │低成本 ││
│  │  │(小,精)   │      │提前2天✅ │最慢  │最低  │最高  │35/T  │        ││
│  ├──┼──────────┼──────┼──────────┼──────┼──────┼──────┼──────┼────────┤│
│  │3 │焊管2线   │74.8  │03-25 08h │7.1h  │¥6,250│95.0% │¥1,375│        ││
│  │  │(旧)      │      │提前3天✅ │中等  │最高  │最低  │55/T  │        ││
│  └──┴──────────┴──────┴──────────┴──────┴──────┴──────┴──────┴────────┘│
│                                                                          │
│  切换策略查看:                                                           │
│  [成本优先] → 推荐: 焊管3线(成本最低¥4,200)                             │
│  [效率优先] → 推荐: 焊管1线(最快5h完成)                                 │
│  [交期优先] → 推荐: 焊管1线(最早完工)                                   │
│                                                                          │
│  [确认选线]  [手动指定]  [查看甘特图预览]                                │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 2. 排产模拟推演（What-If 分析）

### 2.1 核心理念

```
用户的诉求: "我想把这个单子移到那个时间/那条产线, 
            但移之前我想看到全局会变成什么样"

设计思路:
  ┌──────────────────────────────────────────────────────────┐
  │  当前排程 (真实数据)     模拟排程 (内存沙盒)              │
  │                                                          │
  │  ┌────────────────┐    ┌────────────────┐               │
  │  │ 正式排产数据     │    │ 模拟副本        │               │
  │  │ (数据库)        │───►│ (内存, 不落库)   │               │
  │  │                │复制│                │               │
  │  └────────────────┘    └───────┬────────┘               │
  │                               │                         │
  │                        用户操作(拖拽/调整)                │
  │                               │                         │
  │                        ┌──────▼────────┐                │
  │                        │ 模拟结果       │                │
  │                        │               │                │
  │                        │ · 甘特图叠加   │                │
  │                        │ · 影响清单     │                │
  │                        │ · 成本对比     │                │
  │                        │ · 交期风险     │                │
  │                        └───────┬───────┘                │
  │                               │                         │
  │                        用户确认?                         │
  │                        ├── 确认 → 应用到正式数据          │
  │                        └── 取消 → 丢弃模拟, 无影响        │
  │                                                          │
  └──────────────────────────────────────────────────────────┘
```

### 2.2 模拟会话表 (aps_simulation)

```sql
CREATE TABLE aps_simulation (
    sim_id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    sim_no              VARCHAR(30)   NOT NULL,
    sim_name            NVARCHAR(100) NULL,         -- 模拟场景名称
    sim_type            VARCHAR(20)   NOT NULL,     -- MOVE=移动 INSERT=插单
                                                    -- RESCHEDULE=重排 WHATIF=假设分析
    sim_status          VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
                                                    -- ACTIVE=进行中 APPLIED=已应用 DISCARDED=已丢弃
    
    -- ═══ 模拟操作内容 ═══
    operation_desc      NVARCHAR(500) NOT NULL,     -- 操作描述
    
    -- ═══ 影响分析结果 ═══
    affected_count      INT           NULL,          -- 受影响排产任务数
    delivery_risk_count INT           NULL,          -- 交期风险数
    cost_diff           DECIMAL(18,2) NULL,          -- 成本差异(正=增加)
    time_diff_hours     DECIMAL(10,2) NULL,          -- 总用时差异(小时)
    
    created_by          VARCHAR(50)   NOT NULL,
    created_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    applied_time        DATETIME      NULL,
    CONSTRAINT UK_sim_no UNIQUE (sim_no)
);

-- 模拟中每个受影响排产任务的前后对比
CREATE TABLE aps_simulation_detail (
    sim_detail_id       BIGINT IDENTITY(1,1) PRIMARY KEY,
    sim_id              BIGINT        NOT NULL,
    schedule_id         BIGINT        NOT NULL,      -- 受影响的排产单
    change_type         VARCHAR(20)   NOT NULL,      -- TIME_CHANGE/WC_CHANGE/NEW/CANCEL
    
    -- 变更前 (当前真实值)
    before_wc_id        BIGINT        NULL,
    before_start        DATETIME      NULL,
    before_end          DATETIME      NULL,
    before_cost         DECIMAL(18,2) NULL,
    
    -- 变更后 (模拟值)
    after_wc_id         BIGINT        NULL,
    after_start         DATETIME      NULL,
    after_end           DATETIME      NULL,
    after_cost          DECIMAL(18,2) NULL,
    
    -- 交期影响
    due_date            DATETIME      NULL,
    before_on_time      BIT           NULL,          -- 变更前是否按时
    after_on_time       BIT           NULL,          -- 变更后是否按时
    
    CONSTRAINT FK_sim_detail FOREIGN KEY (sim_id) 
        REFERENCES aps_simulation(sim_id)
);
```

### 2.3 模拟推演 UI

```
┌──────────────────────────────────────────────────────────────────────────┐
│  模拟推演模式 🔮                                      [退出模拟] [应用] │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  操作: 将 SCH-013 (方管100×50×4.0 25T) 从焊管1线移到焊管3线             │
│                                                                          │
│  ┌─── 甘特图对比 ───────────────────────────────────────────────────┐  │
│  │                                                                   │  │
│  │  焊管1线  [当前] ████SCH-012████ ████SCH-013████ ████SCH-014████ │  │
│  │          [模拟] ████SCH-012████ ████SCH-014████ ←前移             │  │
│  │                                  (SCH-013移走, 014提前)            │  │
│  │                                                                   │  │
│  │  焊管3线  [当前] ██SCH-031██ ██SCH-032██ ░░░░(空闲)░░░░          │  │
│  │          [模拟] ██SCH-031██ ██SCH-032██ ████SCH-013████          │  │
│  │                                          (插入, 12.5h)            │  │
│  │                                                                   │  │
│  │  图例: [当前]= 实线色块  [模拟]= 虚线色块                          │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ┌─── 影响分析 ──────────────────────────────────────────────────┐     │
│  │                                                                │     │
│  │  ┌───────────┬──────────────────┬──────────────────┬────────┐ │     │
│  │  │           │  变更前(焊管1线)  │  变更后(焊管3线)  │  差异  │ │     │
│  │  ├───────────┼──────────────────┼──────────────────┼────────┤ │     │
│  │  │预计完工   │ 03-22 14:00      │ 03-24 16:00      │ +2天   │ │     │
│  │  │交期03-28  │ 提前6天 ✅       │ 提前4天 ✅       │ 仍满足 │ │     │
│  │  │加工时间   │ 5.0h             │ 12.5h            │ +7.5h  │ │     │
│  │  │成本      │ ¥5,850           │ ¥4,200           │ -¥1,650│ │     │
│  │  │成材率    │ 96.5%            │ 97.0%            │ +0.5%  │ │     │
│  │  │电费      │ ¥1,050           │ ¥875             │ -¥175  │ │     │
│  │  └───────────┴──────────────────┴──────────────────┴────────┘ │     │
│  │                                                                │     │
│  │  级联影响:                                                     │     │
│  │  · SCH-014 焊管1线: 提前 5h (013移走后空出时间)  ← 正面影响    │     │
│  │  · SCH-032 焊管3线: 延后 2h (013插入后挤压)      ← 交期03-30✅│     │
│  │                                                                │     │
│  │  总结: 成本降低¥1,650, 交期仍满足, 但用时增加7.5h              │     │
│  └────────────────────────────────────────────────────────────────┘     │
│                                                                          │
│  [确认应用到正式排产]  [继续调整]  [丢弃模拟]  [保存方案稍后决定]        │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### 2.4 预估完工时间计算

```java
/**
 * 预估完工时间计算器
 * 在还没有生产出来之前, 预估什么时候可以生产出来
 */
public class CompletionEstimator {
    
    public static class Estimate {
        private Long scheduleId;
        private Long wcId;
        private String wcName;
        private LocalDateTime estimatedStart;
        private LocalDateTime estimatedEnd;
        private BigDecimal estimatedHours;
        private BigDecimal estimatedCost;
        private String confidence;  // HIGH/MEDIUM/LOW
    }
    
    /**
     * 为一个待排任务预估在各产线上的完工时间
     */
    public List<Estimate> estimateCompletion(ScheduleTask task) {
        List<Estimate> estimates = new ArrayList<>();
        
        List<Long> candidateWcs = findCandidateWorkCenters(task);
        
        for (Long wcId : candidateWcs) {
            Estimate est = new Estimate();
            est.setWcId(wcId);
            
            // 查该产线对该产品的产能
            WcProductRate rate = rateMapper.selectRate(wcId, task.getMaterialId());
            if (rate == null) continue;
            
            // 加工时间 = 重量 / 小时产能 + 准备时间
            BigDecimal processHours = task.getPlannedWeight()
                .divide(rate.getCapacityPerHour(), 4, RoundingMode.HALF_UP);
            BigDecimal setupHours = BigDecimal.valueOf(rate.getSetupTimeMinutes())
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
            est.setEstimatedHours(processHours.add(setupHours));
            
            // 找最早可用时间槽(考虑已有排产占用)
            WorkCenterTimeline tl = timelineManager.getTimeline(wcId);
            int totalMinutes = processHours.add(setupHours)
                .multiply(BigDecimal.valueOf(60)).intValue();
            TimeSlot slot = tl.findEarliestSlot(LocalDateTime.now(), totalMinutes);
            
            est.setEstimatedStart(slot.getStart());
            est.setEstimatedEnd(slot.getEnd());
            
            // 置信度: 取决于该产线排产满程度
            double loadRate = tl.getLoadRate(slot.getStart().toLocalDate());
            est.setConfidence(loadRate > 0.9 ? "LOW" : loadRate > 0.7 ? "MEDIUM" : "HIGH");
            
            estimates.add(est);
        }
        
        return estimates;
    }
}
```

---

## 3. 上料不符智能换料/调单决策

### 3.1 问题描述

```
场景:
  排产: SCH-013 方管100×50×4.0 Q235B 鞍钢 25T (开平产线)
  实际上料: 热轧卷 2.5×1500 Q235B 唐钢 (产地不对, 或规格不对)

  操作员发现上料不符 → 面临决策:
  
  选项A: 换料
    · 卸下当前物料(耗时T1)
    · 找到正确物料(耗时T2)
    · 装载正确物料(耗时T3)
    · 总换料时间 = T1 + T2 + T3
    · 换料成本 = 停机成本 × 换料时间 + 搬运成本
    
  选项B: 调单(不换料, 改排产)
    · 查找: 当前上料的物料符合哪个工单?
    · 将当前工单与匹配的工单交换排产顺序
    · 用当前物料先生产匹配的工单
    · 原工单推后生产(等正确物料到位)
    
  决策依据: 换料成本 vs 调单影响
    · 如果换料成本 < 调单影响 → 换料
    · 如果换料成本 ≥ 调单影响 → 调单
    · 系统自动测算, 给出建议
```

### 3.2 换料成本参数 (bas_swap_cost_param)

```sql
CREATE TABLE bas_swap_cost_param (
    param_id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    wc_id               BIGINT        NOT NULL,     -- 工作中心/产线
    
    -- ═══ 换料时间参数 ═══
    unload_time_min     INT           NOT NULL DEFAULT 30,  -- 卸料时间(分钟)
    locate_time_min     INT           NOT NULL DEFAULT 15,  -- 找料时间(分钟)
    load_time_min       INT           NOT NULL DEFAULT 30,  -- 装料时间(分钟)
    total_swap_min      AS (unload_time_min + locate_time_min + load_time_min),
    
    -- ═══ 换料成本参数 ═══
    downtime_cost_per_min DECIMAL(18,2) NOT NULL DEFAULT 50,  -- 停机成本(元/分钟)
    handling_cost        DECIMAL(18,2) NOT NULL DEFAULT 200,  -- 搬运费(元/次)
    
    -- ═══ 换料阈值 ═══
    -- 如果换料总成本 < 阈值系数 × 调单影响成本 → 换料
    swap_threshold       DECIMAL(8,4)  NOT NULL DEFAULT 1.0,
    
    CONSTRAINT FK_swap_param_wc FOREIGN KEY (wc_id)
        REFERENCES bas_work_center(wc_id)
);
```

### 3.3 上料不符智能决策引擎

```java
/**
 * 上料不符智能决策引擎
 * 自动测算: 换料 vs 调单, 给出最优建议
 */
public class MaterialMismatchDecisionEngine {
    
    /**
     * 决策结果
     */
    public static class MismatchDecision {
        // 基本信息
        private Long currentScheduleId;        // 当前排产单
        private Long actualStockId;            // 实际上料
        private String mismatchType;           // ORIGIN/GRADE/SPEC/MATERIAL
        private String mismatchDesc;           // 不符描述
        
        // 方案A: 换料
        private int swapTimeMinutes;           // 换料总时间
        private BigDecimal swapCost;           // 换料总成本
        
        // 方案B: 调单
        private Long matchingScheduleId;       // 匹配到的可调换工单
        private String matchingScheduleNo;
        private BigDecimal orderSwapCost;      // 调单影响成本
        private List<CascadeImpact> cascadeImpacts; // 调单级联影响
        
        // 推荐
        private String recommendation;         // SWAP=换料 ORDER_SWAP=调单
        private String recommendReason;
    }

    /**
     * 上料不符时的智能决策
     * @param currentScheduleId  当前排产单
     * @param actualStockId      实际上料的库存批次
     */
    public MismatchDecision decide(Long currentScheduleId, Long actualStockId) {
        MismatchDecision d = new MismatchDecision();
        
        ScheduleTask current = scheduleMapper.selectById(currentScheduleId);
        StockItem actualStock = stockMapper.selectById(actualStockId);
        
        // 1. 识别不符类型
        d.setMismatchType(identifyMismatch(current, actualStock));
        d.setMismatchDesc(buildMismatchDesc(current, actualStock));
        
        // 2. 计算方案A: 换料成本
        SwapCostParam param = paramMapper.selectByWc(current.getWcId());
        d.setSwapTimeMinutes(param.getTotalSwapMin());
        d.setSwapCost(
            BigDecimal.valueOf(param.getTotalSwapMin())
                .multiply(param.getDowntimeCostPerMin())
                .add(param.getHandlingCost())
        );
        
        // 3. 计算方案B: 查找可调换的工单
        // 在同产线的待生产队列中, 找与实际上料匹配的工单
        List<ScheduleTask> queue = scheduleMapper.selectQueueByWc(
            current.getWcId(), "CONFIRMED");
        
        ScheduleTask matchingOrder = null;
        for (ScheduleTask candidate : queue) {
            if (candidate.getScheduleId().equals(currentScheduleId)) continue;
            if (isMaterialMatch(candidate, actualStock)) {
                matchingOrder = candidate;
                break;
            }
        }
        
        if (matchingOrder != null) {
            d.setMatchingScheduleId(matchingOrder.getScheduleId());
            d.setMatchingScheduleNo(matchingOrder.getScheduleNo());
            
            // 计算调单的级联影响
            List<CascadeImpact> impacts = simulateOrderSwap(
                currentScheduleId, matchingOrder.getScheduleId());
            d.setCascadeImpacts(impacts);
            
            // 调单影响成本 = 级联延迟总时间 × 单位延迟成本
            BigDecimal impactCost = BigDecimal.ZERO;
            for (CascadeImpact impact : impacts) {
                if (impact.getDelayHours() > 0) {
                    impactCost = impactCost.add(
                        BigDecimal.valueOf(impact.getDelayHours())
                            .multiply(param.getDowntimeCostPerMin())
                            .multiply(BigDecimal.valueOf(60)));
                }
                if (!impact.isAfterOnTime()) {
                    // 交期违反的额外惩罚成本
                    impactCost = impactCost.add(new BigDecimal("5000"));
                }
            }
            d.setOrderSwapCost(impactCost);
            
            // 4. 比较决策
            if (d.getSwapCost().compareTo(
                    d.getOrderSwapCost().multiply(param.getSwapThreshold())) < 0) {
                d.setRecommendation("SWAP");
                d.setRecommendReason("换料成本(¥" + d.getSwapCost() 
                    + ") < 调单影响(¥" + d.getOrderSwapCost() + "), 建议换料");
            } else {
                d.setRecommendation("ORDER_SWAP");
                d.setRecommendReason("换料成本(¥" + d.getSwapCost() 
                    + ") ≥ 调单影响(¥" + d.getOrderSwapCost() + "), 建议调单");
            }
        } else {
            // 没有可调换的工单 → 只能换料
            d.setRecommendation("SWAP");
            d.setRecommendReason("无匹配工单可调换, 必须换料");
        }
        
        return d;
    }
    
    /**
     * 判断一个候选工单是否与实际上料匹配
     */
    private boolean isMaterialMatch(ScheduleTask candidate, StockItem stock) {
        // 物料规格匹配(可以用规格区间判断)
        if (!specMatcher.isMatch(candidate.getMaterialId(), stock)) return false;
        // 材质匹配(精确或可替代)
        if (!candidate.getDemandGradeCode().equals(stock.getGradeCode())) {
            if (!gradeSubstituteMatcher.canSubstitute(
                    candidate.getDemandGradeCode(), stock.getGradeCode())) 
                return false;
        }
        // 重量足够
        return stock.getAvailableWeight().compareTo(candidate.getPlannedWeight()) >= 0;
    }
}
```

### 3.4 上料不符处理 UI + 通知流程

```
┌──────────────────────────────────────────────────────────────────────────┐
│  ⚠ 上料不符检测                                          [开平线 工位A] │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  当前工单: SCH-088 开平 6.0×1500 Q235B 鞍钢 25T                        │
│  实际上料: HRC-2026-0112  6.0×1500 Q235B 唐钢 22T                      │
│                                                                          │
│  不符项: 产地不一致 (鞍钢 → 唐钢)                                       │
│                                                                          │
│  ════════ 系统智能分析 ════════                                          │
│                                                                          │
│  ┌─── 方案A: 换料 ───────────────────────────────────────────────────┐ │
│  │  卸料: 30min | 找料: 15min | 装料: 30min → 总计 75min             │ │
│  │  换料成本: 75min × ¥50/min + ¥200搬运 = ¥3,950                    │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  ┌─── 方案B: 调单 ───────────────────────────────────────────────────┐ │
│  │  找到匹配工单: SCH-092 开平 6.0×1500 Q235B 唐钢 20T               │ │
│  │  (SCH-092 原排在 03-22, 与当前物料完全匹配)                        │ │
│  │                                                                    │ │
│  │  调整方案:                                                         │ │
│  │    1. SCH-092 提前到现在生产(用当前已上料的唐钢卷)                  │ │
│  │    2. SCH-088 推迟到 SCH-092 原来的位置                            │ │
│  │    3. 后续排产级联调整                                             │ │
│  │                                                                    │ │
│  │  调单影响:                                                         │ │
│  │  ┌──────────┬──────────────────────────┬──────────┬──────────┐   │ │
│  │  │排产单    │影响                      │交期      │风险      │   │ │
│  │  ├──────────┼──────────────────────────┼──────────┼──────────┤   │ │
│  │  │SCH-088  │推迟至 03-22 08:00        │03-28     │✅仍满足  │   │ │
│  │  │SCH-092  │提前至 现在 (立即生产)     │03-25     │✅提前   │   │ │
│  │  │SCH-095  │推迟 2h (被088挤压)        │03-30     │✅仍满足  │   │ │
│  │  └──────────┴──────────────────────────┴──────────┴──────────┘   │ │
│  │                                                                    │ │
│  │  调单影响成本: ¥0 (无交期违反, 无停机)                              │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  ★ 系统建议: 调单(方案B)                                                │
│    理由: 换料成本¥3,950 > 调单成本¥0, 且调单不影响任何交期               │
│                                                                          │
│  [接受建议: 执行调单]  [不接受: 执行换料]  [暂不处理, 通知排产员]         │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### 3.5 调单确认与通知流程

```
调单执行后的通知流程:

  ┌────────────────────────────────────────────────────────────────┐
  │  Step 1: 操作员点击"接受建议: 执行调单"                         │
  └──────────────┬─────────────────────────────────────────────────┘
                 │
  ┌──────────────▼─────────────────────────────────────────────────┐
  │  Step 2: 系统自动执行                                           │
  │  · 交换 SCH-088 和 SCH-092 的排产顺序                          │
  │  · SCH-092 绑定当前已上料的物料 (stock_id=HRC-0112)             │
  │  · SCH-088 更新状态: 等待正确物料                               │
  │  · 更新工作中心时间线                                          │
  │  · 记录变更日志 (change_source='MISMATCH_SWAP')                │
  └──────────────┬─────────────────────────────────────────────────┘
                 │
  ┌──────────────▼─────────────────────────────────────────────────┐
  │  Step 3: 系统自动通知 (WebSocket + 钉钉/企业微信)               │
  │                                                                 │
  │  通知排产员:                                                    │
  │  ┌──────────────────────────────────────────────────────────┐  │
  │  │ 📋 上料不符自动调单通知                                   │  │
  │  │                                                          │  │
  │  │ 产线: 开平线 工位A                                        │  │
  │  │ 原工单: SCH-088 开平Q235B鞍钢 → 已推迟至03-22             │  │
  │  │ 调入: SCH-092 开平Q235B唐钢 → 提前至当前生产              │  │
  │  │ 原因: 上料产地不符, 换料成本高于调单                       │  │
  │  │ 影响: SCH-095 延后2h, 所有工单交期仍满足                   │  │
  │  │                                                          │  │
  │  │ 操作员: 张三  |  时间: 03-20 14:30                        │  │
  │  │                                                          │  │
  │  │ [查看排产变更]  [需要修正? 联系车间]                       │  │
  │  └──────────────────────────────────────────────────────────┘  │
  │                                                                 │
  │  通知车间主任: (同上, 简化版)                                   │
  │  通知仓库: "SCH-088 需要鞍钢卷, 请备料"                        │
  └─────────────────────────────────────────────────────────────────┘
```

---

## 4. API 接口补充

```
# ═══ 排产策略 ═══
GET    /api/v1/schedule/strategy                    # 排产策略列表
PUT    /api/v1/schedule/strategy/{id}               # 修改策略权重
POST   /api/v1/schedule/score-options               # 计算各产线评分
       Body: { scheduleId/taskInfo, strategyCode }

# ═══ 产线成本模型 ═══
GET    /api/v1/wc/{id}/cost-model                   # 产线成本模型
PUT    /api/v1/wc/{id}/cost-model                   # 维护成本参数
GET    /api/v1/wc/{id}/product-rate                  # 产线产品工时表
PUT    /api/v1/wc/{id}/product-rate                  # 维护工时参数

# ═══ 预估与模拟 ═══
POST   /api/v1/schedule/estimate-completion          # 预估完工时间
POST   /api/v1/simulation                            # 创建模拟会话
POST   /api/v1/simulation/{id}/operate               # 在模拟中执行操作
GET    /api/v1/simulation/{id}/impact                 # 获取模拟影响分析
POST   /api/v1/simulation/{id}/apply                  # 应用模拟结果到正式
DELETE /api/v1/simulation/{id}                        # 丢弃模拟

# ═══ 上料不符 ═══
POST   /api/v1/mismatch/detect                       # 检测上料不符
       Body: { scheduleId, actualStockId }
POST   /api/v1/mismatch/decide                       # 智能决策(换料vs调单)
POST   /api/v1/mismatch/{id}/execute-swap             # 执行换料
POST   /api/v1/mismatch/{id}/execute-order-swap       # 执行调单
GET    /api/v1/mismatch/history                       # 不符处理历史
GET    /api/v1/wc/{id}/swap-cost-param                # 换料成本参数
PUT    /api/v1/wc/{id}/swap-cost-param                # 维护换料参数
```
