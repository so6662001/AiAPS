# AiAPS — 模具管理、替代料、排产分组与产出流向设计（V2.1 增强）

> 版本：2.1 | 最后更新：2026-03-17
>
> 本文档是对排产引擎的进一步增强，覆盖四个核心生产实务问题。

---

## 0. 本次增强解决的问题

| # | 问题 | 业务场景 | 设计方案 |
|---|------|---------|---------|
| 1 | 模具产能与换模 | 模具有最大生产量限制，到量必须换模维修，不同机组换模时间不同 | 模具主数据 + 模具寿命追踪 + 排产自动拆单 |
| 2 | 替代料 | 生产中原料不足时，可用相近规格/材质的原料替代 | 替代料规则 + 排产时自动/手动替代匹配 |
| 3 | 同规格不同壁厚分组排产 | 相同模具可生产的相近规格产品连排，减少换模 | 模具兼容组 + 排产分组优化算法 |
| 4 | 产出物料流向 | 排产产出是成品入库，还是流向下一道工序 | 排产表增加流向字段 + 工序关联 |

---

## 1. 模具管理与产能模型

### 1.1 为什么模具是排产的关键约束

```
钢铁行业的模具/工装约束:

  管材生产:
    ├── 成型模具 → 决定管材外形尺寸(方/圆/异型)
    ├── 焊接辊   → 焊缝质量
    └── 定径模具 → 精确尺寸控制

    约束:
    · 每套模具有最大使用量(如: 成型模具累计生产 500T 后必须维修)
    · 达到上限 → 必须停机换模 → 换模时间因机组而异(30min ~ 4h)
    · 换模后旧模具送维修 → 维修周期(如 3~7 天)
    · 同一模具可兼容的产品范围(同外形不同壁厚可能共用模具)

  开平/剪切:
    ├── 剪刃   → 累计剪切次数后需要磨刃/更换
    └── 矫平辊 → 累计吨数后需要研磨

  折弯:
    ├── 上模(冲头)  → 决定折弯角度和半径
    └── 下模(凹模)  → 决定V型槽宽度
    
    约束:
    · 不同板厚需要不同V型槽宽度的下模
    · 换模时间较短(10~30min)，但频繁换模影响效率
```

### 1.2 模具主数据 (bas_mold)

```sql
CREATE TABLE bas_mold (
    mold_id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    mold_code           VARCHAR(30)   NOT NULL,     -- 模具编码
    mold_name           NVARCHAR(100) NOT NULL,     -- 模具名称
    mold_type           VARCHAR(20)   NOT NULL,     -- FORMING=成型模 SIZING=定径模 
                                                    -- WELDING=焊接辊 SHEAR=剪刃 
                                                    -- BEND_UPPER=折弯上模 BEND_LOWER=折弯下模
                                                    -- LEVEL_ROLL=矫平辊 OTHER=其他
    mold_group          VARCHAR(30)   NULL,         -- 模具组(同组模具可替换)
    
    -- ═══ 模具适用范围 ═══
    applicable_category VARCHAR(20)   NULL,         -- 适用产品品类: RECT_PIPE/ROUND_PIPE/...
    spec_range_desc     NVARCHAR(200) NULL,         -- 适用规格范围描述
    -- 适用规格的数值化范围(用于程序匹配)
    min_side_a          DECIMAL(10,3) NULL,         -- 最小边长A / 最小外径
    max_side_a          DECIMAL(10,3) NULL,         -- 最大边长A / 最大外径
    min_side_b          DECIMAL(10,3) NULL,         -- 最小边长B (矩管)
    max_side_b          DECIMAL(10,3) NULL,         -- 最大边长B
    min_thickness       DECIMAL(10,3) NULL,         -- 最小壁厚 / 最小板厚
    max_thickness       DECIMAL(10,3) NULL,         -- 最大壁厚 / 最大板厚
    
    -- ═══ 产能与寿命 ═══
    max_production_qty  DECIMAL(18,3) NOT NULL,     -- 单次最大生产量(吨)，到量必须换模
    max_production_unit VARCHAR(10)   NOT NULL DEFAULT 'T',  -- 最大生产量单位: T/PCS/M
    max_production_pcs  INT           NULL,         -- 最大生产件数(如剪切次数)
    warning_pct         DECIMAL(5,2)  NOT NULL DEFAULT 80,   -- 预警百分比(达到80%提醒)
    
    -- ═══ 维修参数 ═══
    repair_cycle_days   INT           NULL,         -- 标准维修周期(天)
    repair_desc         NVARCHAR(200) NULL,         -- 维修说明
    
    -- ═══ 状态 ═══
    mold_status         VARCHAR(10)   NOT NULL DEFAULT 'IDLE',
                                                    -- IDLE=空闲 IN_USE=使用中 REPAIR=维修中 
                                                    -- SCRAPPED=报废
    current_wc_id       BIGINT        NULL,         -- 当前安装在哪个工作中心
    current_accumulated DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 当前已累计生产量
    last_repair_date    DATETIME      NULL,         -- 上次维修日期
    next_repair_date    DATETIME      NULL,         -- 计划下次维修日期
    
    is_active           BIT           NOT NULL DEFAULT 1,
    created_by          VARCHAR(50)   NULL,
    created_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    updated_by          VARCHAR(50)   NULL,
    updated_time        DATETIME      NULL,
    CONSTRAINT UK_mold_code UNIQUE (mold_code)
);

CREATE INDEX IX_mold_status ON bas_mold(mold_status);
CREATE INDEX IX_mold_category ON bas_mold(applicable_category);
CREATE INDEX IX_mold_wc ON bas_mold(current_wc_id);
```

### 1.3 模具-工作中心安装关系 (bas_mold_wc_bindable)

```sql
-- 定义哪些模具可以安装到哪些工作中心(机组)
-- 以及在不同机组上的换模时间
CREATE TABLE bas_mold_wc_config (
    config_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    mold_id             BIGINT        NOT NULL,
    wc_id               BIGINT        NOT NULL,     -- 可安装的工作中心
    change_time_minutes INT           NOT NULL,      -- 在此工作中心上的换模时间(分钟)
    is_preferred        BIT           NOT NULL DEFAULT 0,  -- 是否优选工作中心
    remark              NVARCHAR(200) NULL,
    CONSTRAINT FK_mold_config_mold FOREIGN KEY (mold_id)
        REFERENCES bas_mold(mold_id),
    CONSTRAINT FK_mold_config_wc FOREIGN KEY (wc_id)
        REFERENCES bas_work_center(wc_id),
    CONSTRAINT UK_mold_wc UNIQUE (mold_id, wc_id)
);
```

### 1.4 换模时间矩阵 (bas_mold_change_matrix)

```sql
-- 精细化换模时间: 从一套模具换到另一套模具所需时间
-- 不同机组、不同前后模具组合，换模时间不同
CREATE TABLE bas_mold_change_matrix (
    change_matrix_id    BIGINT IDENTITY(1,1) PRIMARY KEY,
    wc_id               BIGINT        NOT NULL,     -- 工作中心(机组)
    from_mold_id        BIGINT        NULL,         -- 前一套模具(NULL=新开机)
    to_mold_id          BIGINT        NOT NULL,     -- 后一套模具
    change_time_minutes INT           NOT NULL,      -- 换模时间(分钟)
    -- 同规格不同壁厚的快速换模(只需微调，不换整套模具)
    is_quick_change     BIT           NOT NULL DEFAULT 0,
    quick_change_minutes INT          NULL,          -- 快速换模时间(分钟)
    remark              NVARCHAR(200) NULL,
    CONSTRAINT FK_change_wc FOREIGN KEY (wc_id)
        REFERENCES bas_work_center(wc_id),
    CONSTRAINT FK_change_from FOREIGN KEY (from_mold_id)
        REFERENCES bas_mold(mold_id),
    CONSTRAINT FK_change_to FOREIGN KEY (to_mold_id)
        REFERENCES bas_mold(mold_id)
);

CREATE INDEX IX_change_matrix ON bas_mold_change_matrix(wc_id, from_mold_id, to_mold_id);
```

### 1.5 模具使用记录 (bas_mold_usage_log)

```sql
-- 记录模具每次使用的生产量，用于累计追踪寿命
CREATE TABLE bas_mold_usage_log (
    usage_id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    mold_id             BIGINT        NOT NULL,
    wc_id               BIGINT        NOT NULL,
    schedule_id         BIGINT        NULL,         -- 关联排产单
    usage_date          DATETIME      NOT NULL DEFAULT GETDATE(),
    usage_qty           DECIMAL(18,3) NOT NULL,     -- 本次使用量(吨)
    usage_pcs           INT           NULL,         -- 本次使用件数
    accumulated_before  DECIMAL(18,3) NOT NULL,     -- 使用前累计
    accumulated_after   DECIMAL(18,3) NOT NULL,     -- 使用后累计
    mold_event          VARCHAR(20)   NOT NULL DEFAULT 'PRODUCTION',
                                                    -- PRODUCTION=生产使用 INSTALL=上模 
                                                    -- REMOVE=拆模 REPAIR=送修 
                                                    -- REPAIR_DONE=修复 RESET=重置累计
    operator_code       VARCHAR(30)   NULL,
    remark              NVARCHAR(200) NULL,
    CONSTRAINT FK_usage_mold FOREIGN KEY (mold_id)
        REFERENCES bas_mold(mold_id)
);

CREATE INDEX IX_usage_mold ON bas_mold_usage_log(mold_id, usage_date);
```

### 1.6 模具与产品的兼容关系 (bas_mold_product)

```sql
-- 定义模具可以生产哪些产品(物料)
-- 关键: 同一套模具可能兼容多个相近规格(如同外形不同壁厚)
CREATE TABLE bas_mold_product (
    mold_product_id     BIGINT IDENTITY(1,1) PRIMARY KEY,
    mold_id             BIGINT        NOT NULL,
    material_id         BIGINT        NOT NULL,     -- 可生产的产品物料
    is_primary          BIT           NOT NULL DEFAULT 1,  -- 是否主产品(模具主要为此设计)
    efficiency_rate     DECIMAL(8,4)  NOT NULL DEFAULT 1.0, -- 生产效率系数(非主产品可能效率低)
    capacity_per_hour   DECIMAL(18,3) NULL,         -- 使用此模具时的小时产能(可覆盖工作中心默认值)
    -- 壁厚调整标记: 同模具不同壁厚只需微调(快速换模)
    thickness_adjustable BIT          NOT NULL DEFAULT 0,
    remark              NVARCHAR(200) NULL,
    CONSTRAINT FK_mold_product_mold FOREIGN KEY (mold_id)
        REFERENCES bas_mold(mold_id),
    CONSTRAINT FK_mold_product_mat FOREIGN KEY (material_id)
        REFERENCES bas_material(material_id),
    CONSTRAINT UK_mold_product UNIQUE (mold_id, material_id)
);
```

### 1.7 模具产能对排产的影响

```
排产引擎的模具约束检查流程:

  对每个排产任务(产品+数量):

  ┌──────────────────────────────────────────────────┐
  │ Step 1: 查找该产品需要的模具                      │
  │   · 查 bas_mold_product 找到兼容的模具列表        │
  │   · 筛选 mold_status != 'SCRAPPED'               │
  └──────────────────┬───────────────────────────────┘
                     │
  ┌──────────────────▼───────────────────────────────┐
  │ Step 2: 检查模具剩余寿命                          │
  │   · 剩余寿命 = max_production_qty - current_accumulated │
  │                                                   │
  │   · 如果 排产数量 ≤ 剩余寿命 → 正常排产            │
  │   · 如果 排产数量 > 剩余寿命 → 需要拆单:           │
  │     ├── 第一段: 用当前模具生产剩余量               │
  │     ├── 插入换模时间                              │
  │     └── 第二段: 换新模具(或修复后模具)续产          │
  │                                                   │
  │   · 如果 剩余寿命 < 警戒值(warning_pct) → 预警     │
  └──────────────────┬───────────────────────────────┘
                     │
  ┌──────────────────▼───────────────────────────────┐
  │ Step 3: 计算换模时间                              │
  │   · 查 bas_mold_change_matrix 获取换模时间         │
  │   · 同规格不同壁厚 → 查 is_quick_change            │
  │     如果是快速换模 → 使用 quick_change_minutes      │
  │     否则 → 使用 change_time_minutes                │
  │   · 不同机组换模时间不同 → 按实际机组匹配           │
  └──────────────────┬───────────────────────────────┘
                     │
  ┌──────────────────▼───────────────────────────────┐
  │ Step 4: 将换模时间插入排产时间线                   │
  │   · 换模时间作为不可用时间段占用工作中心             │
  │   · 甘特图上以特殊颜色显示换模时间段               │
  │   · 模具维修周期纳入产能日历(不可用期间)            │
  └──────────────────────────────────────────────────┘
```

### 1.8 模具寿命驱动的排产拆单

```
示例:

  排产任务: 方管100×50×4.0 — 60T
  使用模具: M-001 (成型模具)
  模具剩余寿命: 35T (max=500T, 已累计=465T)

  排产引擎自动拆单:
  ┌────────────────────────────────────────────────────────────┐
  │                                                            │
  │  原任务 SCH-001: 方管100×50×4.0 — 60T                      │
  │                                                            │
  │  → 拆分为:                                                 │
  │                                                            │
  │  SCH-001A: 方管100×50×4.0 — 35T (用模具 M-001, 至满寿命)   │
  │  ├── 03-20 08:00 ~ 03-21 14:00                            │
  │  ├── 模具 M-001 累计达到 500T → 必须拆模维修               │
  │  │                                                         │
  │  [换模时间]: 03-21 14:00 ~ 03-21 16:30 (机组A换模=150min)  │
  │  ├── 拆下 M-001 → 送维修                                  │
  │  ├── 安装 M-002 (同规格备用模具)                           │
  │  │                                                         │
  │  SCH-001B: 方管100×50×4.0 — 25T (用模具 M-002)             │
  │  └── 03-21 16:30 ~ 03-22 14:00                            │
  │                                                            │
  └────────────────────────────────────────────────────────────┘
```

### 1.9 排产表增加模具字段

```sql
-- aps_schedule 增加模具相关字段
ALTER TABLE aps_schedule ADD (
    mold_id             BIGINT        NULL,         -- 使用的模具
    mold_accumulated_before DECIMAL(18,3) NULL,     -- 排产前模具已累计
    mold_accumulated_after  DECIMAL(18,3) NULL,     -- 排产后模具预计累计
    mold_change_required BIT          NOT NULL DEFAULT 0,  -- 排产期间是否需要换模
    mold_change_schedule_id BIGINT    NULL          -- 换模后的续产排产单ID
);

-- aps_schedule_oper 增加换模时间段
ALTER TABLE aps_schedule_oper ADD (
    mold_id             BIGINT        NULL,         -- 工序使用的模具
    is_mold_change      BIT           NOT NULL DEFAULT 0, -- 此工序是否为换模操作
    mold_change_from    BIGINT        NULL,         -- 换下的模具ID
    mold_change_to      BIGINT        NULL          -- 换上的模具ID
);
```

---

## 2. 替代料设计

### 2.1 钢铁行业替代料场景

```
替代料常见场景:

  场景1: 材质替代
    需求: Q235B 带钢
    替代: Q235C 带钢 (力学性能相近，可替代)
    规则: Q235C ≥ Q235B (向上替代，性能更好的可替代差的)

  场景2: 壁厚替代
    需求: 带钢 厚4.0mm
    替代: 带钢 厚4.2mm (稍厚可替代，反之不行)
    规则: 壁厚只能向上替代(厚替薄，不能薄替厚)

  场景3: 宽度替代
    需求: 带钢 宽290mm
    替代: 带钢 宽295mm (稍宽可以，切边即可)
    规则: 宽度只能向上替代(宽替窄，有切边损耗)

  场景4: 产地替代
    需求: 指定鞍钢
    替代: 唐钢 (客户同意的前提下)
    规则: 需客户确认或合同允许

  场景5: 品类替代 (较少见)
    需求: 热轧带钢
    替代: 冷轧带钢 (部分场景可替代)
    规则: 需工艺确认
```

### 2.2 替代料规则表 (bas_substitute_rule)

```sql
-- 替代料规则定义
-- 比原来的 bas_material_substitute(物料级替代)更灵活
-- 支持按属性维度定义替代规则
CREATE TABLE bas_substitute_rule (
    rule_id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    rule_code           VARCHAR(30)   NOT NULL,
    rule_name           NVARCHAR(100) NOT NULL,
    rule_type           VARCHAR(20)   NOT NULL,     -- GRADE=材质替代 THICKNESS=壁厚替代
                                                    -- WIDTH=宽度替代 ORIGIN=产地替代
                                                    -- MATERIAL=物料级替代 CATEGORY=品类替代
    
    -- ═══ 原始需求条件 ═══
    source_category     VARCHAR(20)   NULL,         -- 适用品类
    source_grade_code   VARCHAR(30)   NULL,         -- 原始材质
    source_material_id  BIGINT        NULL,         -- 原始物料(物料级替代时使用)
    
    -- ═══ 替代目标 ═══
    target_grade_code   VARCHAR(30)   NULL,         -- 替代材质
    target_material_id  BIGINT        NULL,         -- 替代物料
    
    -- ═══ 数值维度替代规则(壁厚/宽度) ═══
    dimension_type      VARCHAR(20)   NULL,         -- THICKNESS/WIDTH/LENGTH
    -- 允许替代的偏差范围
    allow_over_min      DECIMAL(10,3) NULL,         -- 允许偏大最小值(mm)
    allow_over_max      DECIMAL(10,3) NULL,         -- 允许偏大最大值(mm)
    allow_under_min     DECIMAL(10,3) NULL,         -- 允许偏小最小值(mm，通常0=不允许偏小)
    allow_under_max     DECIMAL(10,3) NULL,         -- 允许偏小最大值(mm)
    
    -- ═══ 替代影响 ═══
    scrap_rate_adjust   DECIMAL(8,4)  NOT NULL DEFAULT 0,  -- 替代带来的额外损耗(如切边)
    cost_adjust_pct     DECIMAL(8,4)  NULL,         -- 成本调整百分比
    quality_impact      VARCHAR(10)   NULL,         -- NONE/MINOR/REVIEW (是否需要质量审核)
    
    -- ═══ 审批要求 ═══
    need_customer_confirm BIT         NOT NULL DEFAULT 0,  -- 是否需要客户确认
    need_tech_confirm   BIT           NOT NULL DEFAULT 0,  -- 是否需要技术确认
    auto_substitute     BIT           NOT NULL DEFAULT 0,  -- 是否允许系统自动替代
    
    priority            INT           NOT NULL DEFAULT 50, -- 替代优先级(多条规则时取最优)
    is_active           BIT           NOT NULL DEFAULT 1,
    remark              NVARCHAR(500) NULL,
    CONSTRAINT UK_rule_code UNIQUE (rule_code)
);

CREATE INDEX IX_sub_rule_source ON bas_substitute_rule(source_category, source_grade_code);
CREATE INDEX IX_sub_rule_type ON bas_substitute_rule(rule_type);
```

### 2.3 排产替代料匹配记录 (aps_substitute_log)

```sql
-- 记录排产过程中每次替代料的使用情况
CREATE TABLE aps_substitute_log (
    sub_log_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    schedule_id         BIGINT        NOT NULL,     -- 关联排产单
    rule_id             BIGINT        NULL,         -- 使用的替代规则
    
    -- 原始需求
    original_material_id BIGINT       NULL,
    original_grade_code VARCHAR(30)   NULL,
    original_spec_desc  NVARCHAR(200) NULL,
    
    -- 实际替代
    substitute_material_id BIGINT     NULL,
    substitute_grade_code VARCHAR(30) NULL,
    substitute_stock_id BIGINT        NULL,         -- 实际替代的库存批次
    substitute_spec_desc NVARCHAR(200) NULL,
    
    substitute_type     VARCHAR(20)   NOT NULL,     -- GRADE/THICKNESS/WIDTH/ORIGIN/MATERIAL
    extra_scrap_rate    DECIMAL(8,4)  NULL,         -- 额外损耗
    
    -- 审批状态
    approval_status     VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
                                                    -- PENDING/AUTO_APPROVED/APPROVED/REJECTED
    approved_by         VARCHAR(50)   NULL,
    approved_time       DATETIME      NULL,
    
    created_by          VARCHAR(50)   NULL,
    created_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_sub_log_schedule FOREIGN KEY (schedule_id)
        REFERENCES aps_schedule(schedule_id)
);
```

### 2.4 替代料匹配引擎

```java
/**
 * 替代料匹配引擎
 * 当原始物料/规格不满足时，按优先级查找可替代方案
 */
public class SubstituteMatcher {
    
    public static class SubstituteOption {
        private Long ruleId;
        private String substituteType;     // GRADE/THICKNESS/WIDTH/...
        private StockItem matchedStock;    // 匹配到的库存
        private BigDecimal availableQty;
        private BigDecimal extraScrapRate; // 额外损耗
        private boolean needApproval;      // 是否需要审批
        private int priority;
    }
    
    /**
     * 为一个排产任务查找所有可行的替代方案
     */
    public List<SubstituteOption> findSubstitutes(
            Long materialId, String gradeCode,
            BigDecimal requiredWidth, BigDecimal requiredThickness,
            BigDecimal requiredQty) {
        
        List<SubstituteOption> options = new ArrayList<>();
        
        Material material = materialMapper.selectById(materialId);
        
        // 1. 查找材质替代规则
        List<SubstituteRule> gradeRules = ruleMapper.selectByTypeAndSource(
            "GRADE", material.getCategoryCode(), gradeCode);
        for (SubstituteRule rule : gradeRules) {
            List<StockItem> stocks = stockMapper.selectAvailable(
                materialId, rule.getTargetGradeCode(), null);
            for (StockItem stock : stocks) {
                if (stock.getAvailableQty().compareTo(BigDecimal.ZERO) > 0) {
                    SubstituteOption opt = buildOption(rule, stock);
                    options.add(opt);
                }
            }
        }
        
        // 2. 查找壁厚替代(带钢/板材)
        List<SubstituteRule> thicknessRules = ruleMapper.selectByTypeAndSource(
            "THICKNESS", material.getCategoryCode(), null);
        for (SubstituteRule rule : thicknessRules) {
            BigDecimal minThk = requiredThickness.add(
                rule.getAllowOverMin() != null ? rule.getAllowOverMin() : BigDecimal.ZERO);
            BigDecimal maxThk = requiredThickness.add(
                rule.getAllowOverMax() != null ? rule.getAllowOverMax() : BigDecimal.ZERO);
            
            List<StockItem> stocks = stockMapper.selectByThicknessRange(
                material.getCategoryCode(), gradeCode, minThk, maxThk);
            for (StockItem stock : stocks) {
                SubstituteOption opt = buildOption(rule, stock);
                options.add(opt);
            }
        }
        
        // 3. 查找宽度替代
        List<SubstituteRule> widthRules = ruleMapper.selectByTypeAndSource(
            "WIDTH", material.getCategoryCode(), null);
        for (SubstituteRule rule : widthRules) {
            BigDecimal minW = requiredWidth.add(
                rule.getAllowOverMin() != null ? rule.getAllowOverMin() : BigDecimal.ZERO);
            BigDecimal maxW = requiredWidth.add(
                rule.getAllowOverMax() != null ? rule.getAllowOverMax() : BigDecimal.ZERO);
            
            List<StockItem> stocks = stockMapper.selectByWidthRange(
                material.getCategoryCode(), gradeCode, minW, maxW);
            for (StockItem stock : stocks) {
                SubstituteOption opt = buildOption(rule, stock);
                opt.setExtraScrapRate(rule.getScrapRateAdjust());
                options.add(opt);
            }
        }
        
        // 按优先级排序: auto_substitute优先 → priority → 额外损耗小优先
        options.sort(Comparator
            .comparing((SubstituteOption o) -> o.isNeedApproval() ? 1 : 0)
            .thenComparing(SubstituteOption::getPriority)
            .thenComparing(o -> o.getExtraScrapRate()));
        
        return options;
    }
}
```

---

## 3. 同规格不同壁厚分组排产（减少换模）

### 3.1 核心理念

```
实际业务:
  同一套成型模具(如方管100×50的模具)可以生产不同壁厚的方管:
  · 方管 100×50×3.0
  · 方管 100×50×3.5
  · 方管 100×50×4.0
  · 方管 100×50×4.5

  生产不同壁厚时:
  · 不需要换成型模具(外形相同)
  · 只需要调整焊接参数和定径辊间距
  · 这个调整时间(快速换模) << 完全换模时间

  因此:
  · 100×50×3.0 → 100×50×4.0: 快速换模 15min
  · 100×50×4.0 → 80×40×3.0:  完全换模 120min
  
  排产策略:
  · 将相同外形尺寸(side_a × side_b)不同壁厚的任务分为一组
  · 组内连续排产，只发生快速换模
  · 组间换产时才发生完全换模
  · 组内按壁厚从薄到厚排序(或从厚到薄，根据工艺偏好)
```

### 3.2 排产分组模型

```
排产分组层次:

  工作中心 (焊管1线)
  └── 模具组 1: 方管100×50 模具 M-001
  │   ├── [快速换模 15min] 方管100×50×3.0 Q235B — 20T (SCH-011)
  │   ├── [快速换模 10min] 方管100×50×3.5 Q235B — 15T (SCH-012)
  │   ├── [快速换模 10min] 方管100×50×4.0 Q235B — 25T (SCH-013)
  │   └── [快速换模 15min] 方管100×50×4.0 Q345B — 10T (SCH-014)
  │                                              ↑ 同壁厚不同材质，也在组内
  │
  │   [完全换模 120min → 换模具 M-001 → M-003]
  │
  └── 模具组 2: 方管80×40 模具 M-003
      ├── [快速换模 10min] 方管80×40×2.5 Q235B — 30T (SCH-021)
      ├── [快速换模 10min] 方管80×40×3.0 Q235B — 18T (SCH-022)
      └── [快速换模 10min] 方管80×40×3.5 Q345B — 12T (SCH-023)

  组内排序优化:
  · 壁厚: 从薄到厚(或从厚到薄)
  · 材质: 同材质连排(减少焊接参数切换)
  · 排序键: (side_a, side_b, grade_code, thickness)
```

### 3.3 分组排产算法

```java
/**
 * 模具分组排产优化器
 * 将待排任务按模具兼容性分组，组内按壁厚排序，最小化换模次数
 */
public class MoldGroupScheduleOptimizer {
    
    /**
     * 排产分组
     */
    public static class MoldGroup {
        private Long moldId;
        private String moldCode;
        private String specPattern;        // 外形规格(如 "100×50")
        private List<ScheduleTask> tasks;  // 组内任务列表(已排序)
        private int totalChanges;          // 组内换模次数
        private int totalChangeMinutes;    // 组内总换模时间
    }
    
    /**
     * 将待排任务分组并优化排序
     */
    public List<MoldGroup> groupAndOptimize(List<ScheduleTask> tasks, Long wcId) {
        
        // Step 1: 为每个任务查找兼容模具
        Map<Long, List<Long>> taskToMolds = new HashMap<>();
        for (ScheduleTask task : tasks) {
            List<MoldProduct> compatibleMolds = moldProductMapper
                .selectByMaterialId(task.getMaterialId());
            taskToMolds.put(task.getScheduleId(), 
                compatibleMolds.stream().map(MoldProduct::getMoldId).collect(Collectors.toList()));
        }
        
        // Step 2: 按主模具分组
        // 优先使用 is_primary = true 的模具作为分组键
        Map<Long, List<ScheduleTask>> groups = new LinkedHashMap<>();
        for (ScheduleTask task : tasks) {
            Long primaryMoldId = findPrimaryMold(task.getMaterialId());
            groups.computeIfAbsent(primaryMoldId, k -> new ArrayList<>()).add(task);
        }
        
        // Step 3: 组内排序 — 同壁厚同材质靠近
        List<MoldGroup> moldGroups = new ArrayList<>();
        for (Map.Entry<Long, List<ScheduleTask>> entry : groups.entrySet()) {
            MoldGroup group = new MoldGroup();
            group.setMoldId(entry.getKey());
            
            List<ScheduleTask> groupTasks = entry.getValue();
            
            // 排序键: 材质 → 壁厚(薄→厚)
            groupTasks.sort(Comparator
                .comparing(ScheduleTask::getGradeCode)
                .thenComparing(ScheduleTask::getThickness));
            
            group.setTasks(groupTasks);
            
            // 计算组内换模时间
            int totalQuickChanges = 0;
            int totalQuickMinutes = 0;
            for (int i = 1; i < groupTasks.size(); i++) {
                ScheduleTask prev = groupTasks.get(i - 1);
                ScheduleTask curr = groupTasks.get(i);
                
                if (!prev.getThickness().equals(curr.getThickness())
                    || !prev.getGradeCode().equals(curr.getGradeCode())) {
                    // 壁厚或材质不同 → 需要快速换模
                    int quickMin = getQuickChangeMinutes(
                        entry.getKey(), wcId, prev, curr);
                    totalQuickChanges++;
                    totalQuickMinutes += quickMin;
                }
            }
            group.setTotalChanges(totalQuickChanges);
            group.setTotalChangeMinutes(totalQuickMinutes);
            
            moldGroups.add(group);
        }
        
        // Step 4: 组间排序 — 最小化完全换模时间 (TSP近似)
        moldGroups = optimizeGroupOrder(moldGroups, wcId);
        
        return moldGroups;
    }
    
    /**
     * 组间排序优化 — 贪心法近似TSP
     * 选择换模时间最短的下一个组
     */
    private List<MoldGroup> optimizeGroupOrder(List<MoldGroup> groups, Long wcId) {
        if (groups.size() <= 1) return groups;
        
        List<MoldGroup> ordered = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        
        // 从第一个组开始
        MoldGroup current = groups.get(0);
        ordered.add(current);
        visited.add(current.getMoldId());
        
        while (ordered.size() < groups.size()) {
            MoldGroup nearest = null;
            int minChangeTime = Integer.MAX_VALUE;
            
            for (MoldGroup candidate : groups) {
                if (visited.contains(candidate.getMoldId())) continue;
                
                int changeTime = getFullChangeMinutes(
                    wcId, current.getMoldId(), candidate.getMoldId());
                if (changeTime < minChangeTime) {
                    minChangeTime = changeTime;
                    nearest = candidate;
                }
            }
            
            ordered.add(nearest);
            visited.add(nearest.getMoldId());
            current = nearest;
        }
        
        return ordered;
    }
}
```

### 3.4 甘特图展示分组效果

```
排产甘特图 — 带模具分组标注:

焊管1线  03-20                      03-21                      03-22
         08:00    12:00    16:00     08:00    12:00    16:00     08:00
├────────┼────────┼────────┼────────┼────────┼────────┼────────┤
│        │                 │        │                 │         │
│ ┌─ 模具M-001 方管100×50 ──────────────────────────┐│         │
│ │                                                  ││         │
│ │ ██████████ ░ ████████ ░ ████████████ ░ █████████ ││         │
│ │ 100×50×3.0│Q│100×50×3.5Q│100×50×4.0   │Q│×4.0    ││         │
│ │ Q235B 20T │换│Q235B 15T│换│Q235B 25T   │换│Q345B  ││         │
│ │           │模│         │模│            │模│10T    ││         │
│ │           │15│         │10│            │10│       ││         │
│ │           │min         │min            │min      ││         │
│ └──────────────────────────────────────────────────┘│         │
│                                                     │         │
│ [换模具 M-001→M-003: 120min]                        │         │
│ ░░░░░░░░░░░░░░░░░░░░░                              │         │
│                                                     │         │
│ ┌─ 模具M-003 方管80×40 ────────────────────────────┐│         │
│ │ ██████████████████ ░ ████████████ ░ ████████████ ││         │
│ │ 80×40×2.5 Q235B   │Q│80×40×3.0   │Q│80×40×3.5  ││         │
│ │ 30T               │换│Q235B 18T  │换│Q345B 12T  ││         │
│ └──────────────────────────────────────────────────┘│         │

图例:
  ██ = 生产时间
  ░  = 快速换模(壁厚/材质调整, 10~15min)
  ░░ = 完全换模(更换模具, 120min)
  
  组内快速换模以浅色细条显示
  组间完全换模以深色粗条显示 + 标注换模时间
```

---

## 4. 产出物料流向标注

### 4.1 流向概念

```
一个排产任务的产出物料，其去向有以下几种:

  ┌──────────────┐
  │  排产任务     │
  │  (某工序产出) │
  └──────┬───────┘
         │
    ┌────┴────────────────────────────────┐
    │                                     │
    ▼                                     ▼
  ┌──────────┐                     ┌──────────────┐
  │ 流向成品  │                     │ 流向下一工序  │
  │          │                     │              │
  │ 直接入库  │                     │ 显示工序名称  │
  │ 可发货   │                     │ 如: 镀锌     │
  │          │                     │     焊接     │
  └──────────┘                     │     包装     │
                                   │     装配     │
                                   └──────────────┘

更细的流向:
  ├── FG_STOCK: 流向成品仓库(终产品)
  ├── SEMI_STOCK: 流向半成品仓库(中间库存)
  ├── NEXT_OPER: 流向下一道工序(本排产单内)
  ├── NEXT_SCHEDULE: 流向另一个排产单的工序(跨单)
  ├── OUTSOURCE: 流向委外加工
  └── CUSTOMER: 直发客户(不入库)
```

### 4.2 排产表增加流向字段

```sql
-- aps_schedule 增加产出流向字段
ALTER TABLE aps_schedule ADD (
    -- ═══ 产出流向 ═══
    output_flow_type    VARCHAR(20)   NOT NULL DEFAULT 'FG_STOCK',
                                                    -- FG_STOCK=成品入库 
                                                    -- SEMI_STOCK=半成品入库
                                                    -- NEXT_OPER=下一工序(本单内)
                                                    -- NEXT_SCHEDULE=下一排产单
                                                    -- OUTSOURCE=委外
                                                    -- CUSTOMER=直发客户
    output_flow_desc    NVARCHAR(100) NULL,         -- 流向描述(展示用)
    
    -- 流向下一工序时的详细信息
    next_oper_name      NVARCHAR(100) NULL,         -- 下一工序名称(如:镀锌/焊接/包装)
    next_schedule_id    BIGINT        NULL,         -- 下一排产单ID(跨单时)
    next_wc_id          BIGINT        NULL,         -- 下一工序的工作中心
    
    -- 流向成品时的入库信息
    target_warehouse    VARCHAR(30)   NULL,         -- 目标仓库
    
    -- 直发客户时的信息
    direct_customer     NVARCHAR(100) NULL,         -- 直发客户名称
    direct_address      NVARCHAR(500) NULL          -- 直发地址
);

-- aps_schedule_oper 也增加工序级流向
ALTER TABLE aps_schedule_oper ADD (
    output_flow_type    VARCHAR(20)   NULL,         -- 工序产出流向
    output_flow_desc    NVARCHAR(100) NULL,         -- 流向描述
    next_oper_name      NVARCHAR(100) NULL,         -- 下一工序名称
    next_sched_oper_id  BIGINT        NULL          -- 下一工序排产ID(工序间关联)
);
```

### 4.3 流向自动推断规则

```java
/**
 * 产出流向推断引擎
 * 根据排产任务的上下文自动推断产出物料的流向
 */
public class OutputFlowResolver {
    
    public FlowResult resolveFlow(ScheduleTask task) {
        FlowResult result = new FlowResult();
        
        Material product = materialMapper.selectById(task.getMaterialId());
        
        // 规则1: 如果产品是最终成品(material_type=FG) → 流向成品仓
        if ("FG".equals(product.getMaterialType())) {
            // 检查是否有关联需求单，且需求单要求直发
            DemandLine demand = findLinkedDemand(task);
            if (demand != null && demand.isDirectShip()) {
                result.setFlowType("CUSTOMER");
                result.setFlowDesc("直发 " + demand.getCustomerName());
                result.setDirectCustomer(demand.getCustomerName());
            } else {
                result.setFlowType("FG_STOCK");
                result.setFlowDesc("成品入库");
                result.setTargetWarehouse(getDefaultFgWarehouse(product));
            }
            return result;
        }
        
        // 规则2: 检查是否有后续排产单(跨单流转)
        ScheduleTask nextSchedule = findNextSchedule(task);
        if (nextSchedule != null) {
            result.setFlowType("NEXT_SCHEDULE");
            result.setNextScheduleId(nextSchedule.getScheduleId());
            result.setFlowDesc("→ " + nextSchedule.getOperName()
                + " (" + nextSchedule.getWcName() + ")");
            result.setNextOperName(nextSchedule.getOperName());
            result.setNextWcId(nextSchedule.getWcId());
            return result;
        }
        
        // 规则3: 如果有工艺路线中的后续工序 → 流向下一工序
        RoutingOper nextOper = findNextOper(task);
        if (nextOper != null) {
            result.setFlowType("NEXT_OPER");
            result.setFlowDesc("→ " + nextOper.getOperName());
            result.setNextOperName(nextOper.getOperName());
            result.setNextWcId(nextOper.getWcId());
            return result;
        }
        
        // 规则4: 半成品无后续 → 半成品入库
        if ("SEMI".equals(product.getMaterialType())) {
            result.setFlowType("SEMI_STOCK");
            result.setFlowDesc("半成品入库");
            return result;
        }
        
        // 默认: 成品入库
        result.setFlowType("FG_STOCK");
        result.setFlowDesc("成品入库");
        return result;
    }
}
```

### 4.4 甘特图流向展示

```
排产甘特图 — 带产出流向标注:

焊管1线  03-20 08:00 ──────────────────── 03-21 ───────────────────
│                                                                  │
│ ██████████████████ ░ ████████████████████ ░ ██████████████████   │
│ SCH-011           │ │SCH-012             │ │SCH-013             │
│ 方管100×50×3.0    │ │方管100×50×3.5      │ │方管100×50×4.0      │
│ Q235B 20T         │ │Q235B 15T           │ │Q235B 25T           │
│ 模具M-001         │换│模具M-001          │换│模具M-001           │
│                   │模│                   │模│                    │
│ ┌流向─────────┐   │ │┌流向────────────┐  │ │┌流向─────────────┐ │
│ │→ 镀锌工序    │   │ ││→ 成品入库      │  │ ││→ 直发 XX建材    │ │
│ │  镀锌车间    │   │ ││  成品仓A       │  │ ││  合同HT-089     │ │
│ └─────────────┘   │ │└───────────────┘  │ │└────────────────┘ │
│                   │ │                   │ │                    │

排产任务列表(表格视图) — 带流向列:

┌──────┬───────────────┬──────┬────┬──────┬────────┬──────────────┐
│排产号│产品           │材质  │数量│模具  │状态    │产出流向       │
├──────┼───────────────┼──────┼────┼──────┼────────┼──────────────┤
│SCH-011│方管100×50×3.0│Q235B│20T │M-001 │排产中  │→ 镀锌工序    │
│SCH-012│方管100×50×3.5│Q235B│15T │M-001 │已确认  │→ 成品入库    │
│SCH-013│方管100×50×4.0│Q235B│25T │M-001 │已确认  │→ 直发XX建材  │
│SCH-014│方管100×50×4.0│Q345B│10T │M-001 │已确认  │→ 成品入库    │
│SCH-021│方管80×40×2.5 │Q235B│30T │M-003 │待排产  │→ 包装工序    │
│SCH-022│方管80×40×3.0 │Q235B│18T │M-003 │待排产  │→ 成品入库    │
│SCH-031│开平6.0×1500  │Q235B│25T │—     │已确认  │→ 剪切工序    │
│SCH-032│分剪290×4.0   │Q235B│22T │—     │已确认  │→ 制管(焊管1线)│
└──────┴───────────────┴──────┴────┴──────┴────────┴──────────────┘
```

### 4.5 流向追踪视图（生产链路）

```
产出物料的完整生产链路追踪:

  钢卷 C-201 (1500×4.0 Q235B, 30T)
  │
  ├── [分剪] SCH-101 → 分出 290×4.0 带钢 5条
  │   │   流向: → 制管(焊管1线)
  │   │
  │   ├── [制管] SCH-011 → 方管100×50×3.0 Q235B 20T
  │   │   │   流向: → 镀锌工序
  │   │   │
  │   │   └── [镀锌] SCH-041 → 镀锌方管100×50×3.0 Q235B 20T
  │   │       流向: → 成品入库(成品仓A)
  │   │
  │   └── [制管] SCH-013 → 方管100×50×4.0 Q235B 10T
  │       流向: → 直发客户(XX建材, 合同HT-089)
  │
  └── [边丝/余料] 1.5T → 废料处理
```

---

## 5. 综合排产引擎增强

### 5.1 增强后的排产主流程

```
┌───────────────────────────────────────────────────────┐
│ Step 1: 加载排产输入                                   │
│  · 已确认的计划订单                                    │
│  · 现有排产计划(锁定的)                                │
│  · 工作中心 + 产能日历                                 │
│  · 模具主数据 + 模具当前状态/累计量        ← [模具]    │
│  · 换模时间矩阵(含快速换模)               ← [模具]    │
│  · 替代料规则                             ← [替代料]   │
└──────────────────────┬────────────────────────────────┘
                       │
┌──────────────────────▼────────────────────────────────┐
│ Step 2: 任务拆分与工序展开                             │
│  · 计划订单 → 排产任务                                 │
│  · 检查模具寿命 → 超量自动拆单             ← [模具]    │
│  · 计算每道工序加工时间(含模具效率系数)     ← [模具]    │
└──────────────────────┬────────────────────────────────┘
                       │
┌──────────────────────▼────────────────────────────────┐
│ Step 2.5: 原料匹配与替代检查               ← [替代料]  │
│  · 检查原料库存是否满足                                │
│  · 不满足 → 自动查找替代料方案                         │
│  · auto_substitute=true → 自动使用最优替代              │
│  · 需审批 → 生成替代料审批待办                         │
└──────────────────────┬────────────────────────────────┘
                       │
┌──────────────────────▼────────────────────────────────┐
│ Step 3: 模具分组排产优化                   ← [分组]    │
│  · 按模具兼容性对任务分组                              │
│  · 组内按 (材质 → 壁厚) 排序                          │
│  · 组间用贪心法优化换模顺序                            │
│  · 计算每次换模时间(快速/完全)                         │
└──────────────────────┬────────────────────────────────┘
                       │
┌──────────────────────▼────────────────────────────────┐
│ Step 4: 有限产能排产(带模具约束)                       │
│  · 按分组后的顺序依次分配时间槽                        │
│  · 组内任务间插入快速换模时间                          │
│  · 组间插入完全换模时间                                │
│  · 模具到量时插入换模+维修等待时间          ← [模具]    │
│  · 检查备用模具可用性                                  │
└──────────────────────┬────────────────────────────────┘
                       │
┌──────────────────────▼────────────────────────────────┐
│ Step 5: 产出流向推断                       ← [流向]    │
│  · 对每个排产任务推断产出流向                          │
│  · FG_STOCK / NEXT_OPER / NEXT_SCHEDULE / CUSTOMER     │
│  · 标注下一工序名称                                    │
│  · 建立排产单间的流转关系                              │
└──────────────────────┬────────────────────────────────┘
                       │
┌──────────────────────▼────────────────────────────────┐
│ Step 6: 冲突检测(增强)                                 │
│  · 原有: 资源冲突、交期冲突、产能超载                   │
│  · 新增: 模具不足冲突(需要的模具在维修中)   ← [模具]    │
│  · 新增: 替代料审批未完成阻塞排产           ← [替代料]  │
│  · 新增: 流向下一工序的产能是否匹配         ← [流向]    │
└──────────────────────┬────────────────────────────────┘
                       │
┌──────────────────────▼────────────────────────────────┐
│ Step 7: 结果输出 + 模具累计更新                        │
│  · 更新排产表(含模具/替代料/流向字段)                   │
│  · 更新模具预计累计量                                  │
│  · 生成换模日程                                        │
│  · 生成替代料使用报告                                  │
│  · 生成流转关系图                                      │
└───────────────────────────────────────────────────────┘
```

### 5.2 增强后的排产甘特图

```
┌──────────────────────────────────────────────────────────────────┐
│  排产调度中心                                                     │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  视图: [产线视图●] [模具视图] [流向视图]                           │
│                                                                  │
│  工具栏:                                                         │
│  [🔄 自动排产] [📥 插单] [🔒 锁定] [🔧 换模管理] [🔀 替代料]     │
│  [✂ 拆分] [🔗 合并] [📊 负荷/模具分析]                           │
│                                                                  │
│  显示选项: ☑ 显示模具信息  ☑ 显示流向  ☑ 显示换模时段  ☑ 显示替代料│
│                                                                  │
├─────────┬────────────────────────────────────────────────────────┤
│ 焊管1线 │  03-20                                  03-21          │
│         │  08:00    12:00    16:00    20:00    08:00    12:00     │
│         │                                                        │
│  📊85%  │ ┌─ M-001 方管100×50 ─────────────────┐                │
│  🔧M-001│ │██████░██████░██████████░██████████ │                │
│         │ │100×50 │100×50│100×50×4.0│100×50×4.0│                │
│         │ │×3.0 20T│×3.5 │Q235B 25T │Q345B 10T│                │
│         │ │→镀锌  │15min│→成品库  │→成品库  │                │
│         │ │🔵MTO   │10min│🔵MTO    │🟢MTS   │                │
│         │ └────────────────────────────────────┘                │
│         │                                                        │
│         │ ░░░░░░░ [换模M-001→M-003 120min] ░░░░░░░              │
│         │ ⚠ M-001累计达490T/500T → 14:00换模送修                 │
│         │                                                        │
│         │ ┌─ M-003 方管80×40 ────────────────────────┐          │
│         │ │████████████░████████░███████████         │          │
│         │ │80×40×2.5  │80×40  │80×40×3.5           │          │
│         │ │Q235B 30T  │×3.0 18T│Q345B 12T           │          │
│         │ │→包装      │10min│→成品库 │→成品库      │          │
│         │ │🔵MTO      │    │🟢MTS   │🔵MTO        │          │
│         │ └──────────────────────────────────────────┘          │
│         │                                                        │
├─────────┤                                                        │
│ 分剪线  │ ████████████████ ░ ██████████████                      │
│         │ SCH-101 C-201   │换│SCH-102 C-202                     │
│  📊72%  │ 290×4.0 一分五   │刃│300×4.0 一分五                    │
│         │ Q235B            │ │Q345B                              │
│         │ →焊管1线(制管)   │ │→焊管2线(制管)                     │
│         │                  │ │                                   │
│         │ 替代料: 原Q235B  │ │                                   │
│         │ → 实际Q235C ⚠待确│ │                                   │
│         │                                                        │
├─────────┴────────────────────────────────────────────────────────┤
│  📍 选中: SCH-011 | 方管100×50×3.0 Q235B | 20T                   │
│  模具: M-001(累计490/500T, 剩余10T) | 流向: → 镀锌工序(镀锌车间)  │
│  原料: 带钢290×4.0 Q235B(C-001) | 替代料: 无                     │
│  客户: XX建材 | 合同号: HT-089 | 交期: 03-25                     │
│                                                                  │
│  [调整时间] [更换模具] [更换原料] [更换替代料] [修改流向] [查看链路]│
└──────────────────────────────────────────────────────────────────┘
```

### 5.3 模具管理面板

```
┌──────────────────────────────────────────────────────────────────┐
│  模具管理中心                                                     │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  模具状态总览:                                                    │
│  ┌──────┬──────────┬──────┬──────────┬──────────┬──────┬──────┐ │
│  │编号  │名称      │状态  │当前机组  │累计/上限  │寿命% │操作  │ │
│  ├──────┼──────────┼──────┼──────────┼──────────┼──────┼──────┤ │
│  │M-001 │方管100×50│使用中│焊管1线   │490/500T  │98%🔴│[换模]│ │
│  │M-002 │方管100×50│空闲  │—        │120/500T  │24%🟢│[上模]│ │
│  │M-003 │方管80×40 │空闲  │—        │300/500T  │60%🟡│[上模]│ │
│  │M-004 │圆管Φ89  │维修中│—        │—        │维修中│      │ │
│  │      │          │      │预计03-25 │修复后归零│      │      │ │
│  │M-005 │圆管Φ114 │使用中│焊管2线   │200/500T  │40%🟢│      │ │
│  └──────┴──────────┴──────┴──────────┴──────────┴──────┴──────┘ │
│                                                                  │
│  ⚠ 预警: M-001 即将到达寿命上限(98%)，建议排产后安排换模          │
│  ⚠ 预警: M-004 维修中，预计03-25归还，影响圆管Φ89排产             │
│                                                                  │
│  [批量上模] [批量送修] [新增模具] [维修日历]                      │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## 6. 完整示例：综合排产场景

```
场景: 焊管1线排产, 5个任务, 涉及模具寿命/换模/替代料/流向

输入:
  ├── SCH-011: 方管100×50×3.0 Q235B — 20T, MTO, 交期03-25, →镀锌
  ├── SCH-012: 方管100×50×3.5 Q235B — 15T, MTO, 交期03-28, →成品库
  ├── SCH-013: 方管100×50×4.0 Q235B — 25T, MTO, 交期03-25, →直发客户
  ├── SCH-014: 方管100×50×4.0 Q345B — 10T, MTS, →成品库
  └── SCH-021: 方管80×40×2.5 Q235B — 30T, MTO, 交期03-28, →包装

排产引擎处理:

  Step 1: 模具查找
    SCH-011~014 → 模具M-001 (方管100×50, 兼容壁厚2.5~6.0)
    SCH-021     → 模具M-003 (方管80×40)

  Step 2: 模具寿命检查
    M-001: 当前累计 = 465T, 上限 = 500T, 剩余 = 35T
    SCH-011~014 合计 = 20+15+25+10 = 70T > 35T ← 需要拆单!
    
    策略: 先用M-001做35T, 然后换M-002(同规格备用, 累计=120T)
    M-001做完35T → 送维修, 预计7天后归还
    
    拆单结果:
    ├── SCH-011: 20T → M-001 (累计465→485)
    ├── SCH-012: 15T → M-001 (累计485→500) ← 到上限!
    ├── [换模 M-001→M-002: 焊管1线=150min]
    ├── SCH-013: 25T → M-002 (累计120→145)
    └── SCH-014: 10T → M-002 (累计145→155)

  Step 3: 模具分组排序
    组1(M-001): SCH-011(×3.0) → SCH-012(×3.5) [壁厚从薄到厚]
    [完全换模 150min: M-001→M-002]
    组2(M-002): SCH-013(×4.0 Q235B) → SCH-014(×4.0 Q345B) [同壁厚,材质排序]
    [完全换模 120min: M-002→M-003]
    组3(M-003): SCH-021(80×40×2.5)

  Step 4: 替代料检查
    SCH-013 需要带钢290×4.0 Q235B, 库存只有15T, 不足10T
    → 查找替代: 290×4.2 Q235B 库存20T → 壁厚替代(厚替薄, auto=true)
    → 自动使用, 额外损耗率+1% (切边)
    
  Step 5: 流向标注
    SCH-011: output_flow_type=NEXT_OPER, next_oper_name="镀锌"
    SCH-012: output_flow_type=FG_STOCK, target_warehouse="成品仓A"
    SCH-013: output_flow_type=CUSTOMER, direct_customer="XX建材"
    SCH-014: output_flow_type=FG_STOCK
    SCH-021: output_flow_type=NEXT_OPER, next_oper_name="包装"

  最终甘特图:
    08:00─────────12:00─────────16:00─────────20:00─────────08:00─
    ├─[M-001]─────────────────────────────────────────────────────┤
    │ SCH-011(×3.0,20T) ░15min│ SCH-012(×3.5,15T)               │
    │ →镀锌                   │ →成品库                          │
    ├─────────────────────────┤                                   │
    │                         │ [换模M-001→M-002: 150min]         │
    ├─[M-002]─────────────────┤─────────────────────────────────┤
    │                         │ SCH-013(×4.0 Q235B,25T) ░10min│  │
    │                         │ →直发XX建材  替代料:厚4.2│      │  │
    │                         │                    SCH-014(×4.0│  │
    │                         │                    Q345B,10T)  │  │
    │                         │                    →成品库     │  │
    ├─────────────────────────┤────────────────────────────────┤  │
    │                         │        [换模M-002→M-003: 120min]  │
    ├─[M-003]─────────────────┤───────────────────────────────┤  │
    │                         │  SCH-021(80×40×2.5, 30T)      │  │
    │                         │  →包装                        │  │
```
