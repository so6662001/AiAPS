# AiAPS — 材质与产地替代料深度设计

> 版本：1.0 | 最后更新：2026-03-17
>
> 本文档是对 09-mold-substitute-flow.md §2 替代料设计的深度增强，
> 专门解决钢铁行业中材质（钢号）和产地（钢厂）替代的复杂业务场景。

---

## 0. 问题诊断：为什么材质/产地替代需要专门设计

```
钢铁行业的现实:

  客户下单: "方管100×50×4.0  Q235B  要鞍钢的"
                              ↑材质    ↑产地

  但实际生产时:
  ├── 鞍钢的Q235B带钢用完了, 唐钢的Q235B有货 → 能不能用?
  ├── Q235B带钢都没了, Q235C有货(性能更好) → 能不能用?
  ├── 鞍钢的Q235B带钢有货但厚度是4.2mm → 厚度+产地组合替代?
  ├── 客户A接受唐钢替代鞍钢, 客户B坚持只要鞍钢 → 客户差异化?
  └── 用Q345B替代Q235B → 材质跨系列, 需要技术确认

  V1版(09文档)的问题:
  · 只有简单的 source_grade → target_grade 一对一规则
  · 没有材质等级层次(不知道Q235C > Q235B)
  · 没有产地质量分级(不知道鞍钢≈宝钢 > 某小厂)
  · 没有客户级差异化(所有客户一套规则)
  · 材质替代和产地替代是分开的, 不能组合判断
  · 产出品的材质/产地标记没有联动更新
```

---

## 1. 材质等级体系

### 1.1 材质等级与替代方向

```
碳素结构钢 Q235 系列（GB/T700）:
  Q235A < Q235B < Q235C < Q235D
  
  · A级: 冲击试验温度不作要求
  · B级: 20°C 冲击 ≥ 27J
  · C级: 0°C 冲击 ≥ 27J
  · D级: -20°C 冲击 ≥ 27J
  
  替代方向: 高级可替代低级 (D→C→B→A)
  即: Q235D 可替代 Q235C/B/A
      Q235C 可替代 Q235B/A
      Q235B 不可替代 Q235C

低合金高强度钢 Q345 系列（GB/T1591）:
  Q345A < Q345B < Q345C < Q345D < Q345E
  
  同系列内可向上替代, Q345 不可替代 Q235(强度不同, 非简单升级)

跨系列替代 — 需技术确认:
  Q235B ↔ Q345B : 不可自动替代(强度差异大)
  Q235B ↔ SS400 : 可能可以(国际标准对应, 需确认)
  20# ↔ Q235B  : 不可自动替代(用途不同)
```

### 1.2 材质等级表 (bas_grade_hierarchy)

```sql
-- 材质等级层次表: 定义同系列内的等级关系
CREATE TABLE bas_grade_hierarchy (
    hierarchy_id        BIGINT IDENTITY(1,1) PRIMARY KEY,
    grade_family        VARCHAR(20)   NOT NULL,     -- 材质系列: Q235/Q345/Q390/Q420/20#/45#/304
    grade_code          VARCHAR(30)   NOT NULL,     -- 材质编码: Q235A/Q235B/Q235C/Q235D
    grade_level         INT           NOT NULL,     -- 等级序号(越大越高级): A=1, B=2, C=3, D=4
    standard_code       VARCHAR(30)   NULL,         -- 执行标准: GB/T700, GB/T1591
    
    -- ═══ 力学性能参考值(用于跨系列对比) ═══
    yield_strength_min  DECIMAL(10,2) NULL,         -- 屈服强度下限 MPa
    tensile_strength_min DECIMAL(10,2) NULL,        -- 抗拉强度下限 MPa
    elongation_min      DECIMAL(8,2)  NULL,         -- 延伸率下限 %
    impact_temp         INT           NULL,         -- 冲击试验温度 ℃
    impact_value_min    DECIMAL(8,2)  NULL,         -- 冲击功下限 J
    
    is_active           BIT           NOT NULL DEFAULT 1,
    CONSTRAINT UK_grade_hier UNIQUE (grade_family, grade_code)
);

-- 预置数据示例:
-- INSERT INTO bas_grade_hierarchy VALUES
--   ('Q235', 'Q235A', 1, 'GB/T700', 235, 370, 26, NULL, NULL, 1),
--   ('Q235', 'Q235B', 2, 'GB/T700', 235, 370, 26, 20, 27, 1),
--   ('Q235', 'Q235C', 3, 'GB/T700', 235, 370, 26, 0, 27, 1),
--   ('Q235', 'Q235D', 4, 'GB/T700', 235, 370, 26, -20, 27, 1),
--   ('Q345', 'Q345A', 1, 'GB/T1591', 345, 470, 21, NULL, NULL, 1),
--   ('Q345', 'Q345B', 2, 'GB/T1591', 345, 470, 21, 20, 34, 1),
--   ...
```

### 1.3 跨系列材质替代规则 (bas_grade_cross_substitute)

```sql
-- 跨系列替代: 不同材质系列之间的替代关系(需技术确认)
CREATE TABLE bas_grade_cross_substitute (
    cross_sub_id        BIGINT IDENTITY(1,1) PRIMARY KEY,
    source_grade_code   VARCHAR(30)   NOT NULL,     -- 原始材质
    target_grade_code   VARCHAR(30)   NOT NULL,     -- 替代材质
    substitute_direction VARCHAR(10)  NOT NULL,     -- UP=向上(性能更好)
                                                    -- EQUIV=等效 DOWN=向下(需特批)
    
    applicable_category VARCHAR(20)   NULL,         -- 适用品类(NULL=全品类)
    
    -- ═══ 审批要求 ═══
    auto_allowed        BIT           NOT NULL DEFAULT 0,
    need_tech_confirm   BIT           NOT NULL DEFAULT 1,  -- 跨系列默认需技术确认
    need_customer_confirm BIT         NOT NULL DEFAULT 1,  -- 跨系列默认需客户确认
    
    -- ═══ 影响 ═══
    cost_impact_pct     DECIMAL(8,4)  NULL,         -- 成本影响百分比(正=更贵)
    quality_note        NVARCHAR(500) NULL,         -- 质量说明
    
    is_active           BIT           NOT NULL DEFAULT 1,
    remark              NVARCHAR(500) NULL,
    
    CONSTRAINT UK_grade_cross UNIQUE (source_grade_code, target_grade_code, applicable_category)
);
```

---

## 2. 产地质量分级体系

### 2.1 产地在钢铁行业的意义

```
为什么产地重要:

  1. 质量信誉不同
     · 大型钢厂(鞍钢/宝钢/首钢): 质量稳定, 性能偏上限
     · 中型钢厂(唐钢/日照/沙钢): 质量良好, 性价比高
     · 小型钢厂: 质量波动大, 价格便宜

  2. 化学成分差异
     · 同为Q235B, 不同钢厂的C/Mn/Si含量范围不同
     · 影响焊接性、成型性

  3. 客户指定
     · 出口订单通常指定大钢厂
     · 工程项目可能有供方名录要求
     · 部分客户只认特定钢厂(历史合作/验厂通过)

  4. 价格差异
     · 同规格同材质, 不同钢厂价格可能差 50~200 元/吨
     · 替代时需考虑成本影响
```

### 2.2 产地质量分级表 (bas_origin_tier)

```sql
-- 产地按质量分级, 同级可互换, 高级可替低级
CREATE TABLE bas_origin_tier (
    tier_id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    origin_code         VARCHAR(30)   NOT NULL,     -- 产地编码
    origin_name         NVARCHAR(60)  NOT NULL,     -- 产地名称
    quality_tier        INT           NOT NULL,     -- 质量等级: 1=顶级 2=优良 3=良好 4=一般
    tier_name           NVARCHAR(30)  NULL,         -- 等级名称: 顶级/优良/良好/一般
    
    -- ═══ 各品类的质量评分(不同品类同一钢厂质量可能不同) ═══
    pipe_quality_score  INT           NULL,         -- 管材用料质量评分 1~100
    plate_quality_score INT           NULL,         -- 板材质量评分
    profile_quality_score INT         NULL,         -- 型材质量评分
    
    -- ═══ 价格参考 ═══
    price_tier          INT           NULL,         -- 价格等级: 1=最贵 2=较贵 3=适中 4=便宜
    avg_premium_per_ton DECIMAL(10,2) NULL,         -- 相对市场均价的溢价/折价 (元/吨)
    
    is_active           BIT           NOT NULL DEFAULT 1,
    CONSTRAINT FK_origin_tier FOREIGN KEY (origin_code) 
        REFERENCES bas_origin(origin_code)
);

-- 预置数据示例:
-- 等级1(顶级): 宝钢, 太钢(不锈钢)
-- 等级2(优良): 鞍钢, 首钢, 马钢, 武钢
-- 等级3(良好): 唐钢, 日照, 沙钢, 邯钢, 南钢, 柳钢
-- 等级4(一般): 各地中小钢厂
```

### 2.3 产地互换组 (bas_origin_exchange_group)

```sql
-- 定义哪些产地可以互相替换(不需要审批)
-- 比质量分级更精细: 同等级不一定都能互换
CREATE TABLE bas_origin_exchange_group (
    group_id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    group_code          VARCHAR(20)   NOT NULL,     -- 互换组编码
    group_name          NVARCHAR(60)  NOT NULL,     -- 互换组名称
    applicable_category VARCHAR(20)   NULL,         -- 适用品类(NULL=全品类)
    applicable_grade_family VARCHAR(20) NULL,       -- 适用材质系列(NULL=全系列)
    exchange_type       VARCHAR(10)   NOT NULL DEFAULT 'FREE',
                                                    -- FREE=自由互换 CONFIRM=需确认
    remark              NVARCHAR(200) NULL,
    CONSTRAINT UK_origin_group UNIQUE (group_code)
);

-- 互换组成员
CREATE TABLE bas_origin_exchange_member (
    member_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    group_id            BIGINT        NOT NULL,
    origin_code         VARCHAR(30)   NOT NULL,
    CONSTRAINT FK_exchange_group FOREIGN KEY (group_id) 
        REFERENCES bas_origin_exchange_group(group_id),
    CONSTRAINT UK_exchange_member UNIQUE (group_id, origin_code)
);

-- 示例:
-- 互换组 G01 "优质管材用料": 鞍钢, 首钢, 马钢 → 这三家的带钢互相替换免审批
-- 互换组 G02 "华北板材组": 唐钢, 邯钢, 日照 → 板材互换免审批
-- 互换组 G03 "碳钢通用": 鞍钢,首钢,唐钢,日照,沙钢 → 大范围互换但需确认
```

---

## 3. 客户级材质/产地接受度

### 3.1 为什么需要客户级差异化

```
同样是 Q235B 带钢用完了想用 Q235C 替代:

  客户A (XX建材): "没问题, 随便换, 我不挑材质/产地"
    → auto_substitute = true, 免审批

  客户B (YY钢构): "材质可以向上替代, 但产地只接受鞍钢/宝钢/首钢"
    → 材质免审, 产地需在白名单内

  客户C (ZZ出口): "材质和产地都不能换, 必须严格按合同"
    → 一律不允许替代, 如需替代必须客户确认

  客户D (AA车辆): "碳钢可以换产地, 但合金钢必须指定厂家"
    → 按材质系列差异化
```

### 3.2 客户材质/产地偏好表 (bas_customer_material_pref)

```sql
CREATE TABLE bas_customer_material_pref (
    pref_id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    customer_code       VARCHAR(30)   NOT NULL,     -- 客户编码
    
    -- ═══ 材质替代偏好 ═══
    grade_substitute_policy VARCHAR(20) NOT NULL DEFAULT 'SAME_FAMILY_UP',
                                                    -- STRICT=严格不替代
                                                    -- SAME_FAMILY_UP=同系列向上可替代
                                                    -- SAME_FAMILY_ANY=同系列任意替代
                                                    -- CROSS_FAMILY=可跨系列(需确认)
                                                    -- ANY=任意材质(客户不限)
    grade_auto_approve  BIT           NOT NULL DEFAULT 0,
                                                    -- 材质替代是否自动批准
    
    -- ═══ 产地替代偏好 ═══
    origin_substitute_policy VARCHAR(20) NOT NULL DEFAULT 'TIER_UP',
                                                    -- STRICT=严格不替代(只用指定产地)
                                                    -- WHITELIST=白名单内可替代
                                                    -- SAME_TIER=同等级可替代
                                                    -- TIER_UP=同等级或更高等级可替代
                                                    -- ANY=任意产地(客户不限)
    origin_auto_approve BIT           NOT NULL DEFAULT 0,
    
    -- ═══ 特殊限制(按材质系列差异化) ═══
    -- 如: 碳钢可以换产地, 合金钢不行
    special_rules_desc  NVARCHAR(500) NULL,
    
    is_active           BIT           NOT NULL DEFAULT 1,
    remark              NVARCHAR(500) NULL,
    CONSTRAINT UK_customer_pref UNIQUE (customer_code)
);

-- 客户产地白名单 (origin_substitute_policy=WHITELIST 时使用)
CREATE TABLE bas_customer_origin_whitelist (
    whitelist_id        BIGINT IDENTITY(1,1) PRIMARY KEY,
    customer_code       VARCHAR(30)   NOT NULL,
    origin_code         VARCHAR(30)   NOT NULL,     -- 客户接受的产地
    applicable_grade_family VARCHAR(20) NULL,       -- 适用材质系列(NULL=全系列)
    CONSTRAINT UK_cust_origin UNIQUE (customer_code, origin_code, 
                                      ISNULL(applicable_grade_family, ''))
);

-- 客户材质黑名单 (不接受的特定材质)
CREATE TABLE bas_customer_grade_blacklist (
    blacklist_id        BIGINT IDENTITY(1,1) PRIMARY KEY,
    customer_code       VARCHAR(30)   NOT NULL,
    grade_code          VARCHAR(30)   NOT NULL,     -- 客户不接受的材质
    reason              NVARCHAR(200) NULL,
    CONSTRAINT UK_cust_grade_bl UNIQUE (customer_code, grade_code)
);
```

---

## 4. 增强后的替代料匹配引擎

### 4.1 完整匹配流程

```
┌──────────────────────────────────────────────────────────────────────┐
│  材质+产地 替代料匹配流程 (增强版)                                     │
│                                                                      │
│  输入: 需求物料(规格) + 需求材质 + 需求产地 + 需求数量 + 客户编码      │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │ Step 1: 精确匹配                                               │  │
│  │   查库存: 规格匹配 AND 材质=需求材质 AND 产地=需求产地          │  │
│  │   → 找到 → 完美, 直接分配                                      │  │
│  │   → 不足 → 进入替代匹配                                        │  │
│  └──────────────────────────┬─────────────────────────────────────┘  │
│                              │ 不足                                   │
│  ┌──────────────────────────▼─────────────────────────────────────┐  │
│  │ Step 2: 加载客户偏好                                            │  │
│  │   查 bas_customer_material_pref 获取:                           │  │
│  │   · 材质替代策略 (STRICT/SAME_FAMILY_UP/...)                    │  │
│  │   · 产地替代策略 (STRICT/WHITELIST/SAME_TIER/...)               │  │
│  │   · 是否自动批准                                                │  │
│  │                                                                 │  │
│  │   如果 材质=STRICT 且 产地=STRICT → 不可替代, 报告缺料          │  │
│  └──────────────────────────┬─────────────────────────────────────┘  │
│                              │                                        │
│  ┌──────────────────────────▼─────────────────────────────────────┐  │
│  │ Step 3: 按优先级搜索替代方案                                     │  │
│  │                                                                 │  │
│  │  优先级1: 同材质 + 同等级产地                                    │  │
│  │    例: Q235B 鞍钢 → Q235B 首钢(同为优良级)                      │  │
│  │    判断: 客户产地策略允许? 两产地在同互换组?                     │  │
│  │                                                                 │  │
│  │  优先级2: 同材质 + 更高等级产地                                  │  │
│  │    例: Q235B 唐钢 → Q235B 鞍钢(唐钢=良好, 鞍钢=优良)           │  │
│  │    判断: 客户产地策略允许 TIER_UP?                               │  │
│  │                                                                 │  │
│  │  优先级3: 同系列高级材质 + 同产地                                │  │
│  │    例: Q235B 鞍钢 → Q235C 鞍钢(材质升级, 产地不变)              │  │
│  │    判断: 客户材质策略允许 SAME_FAMILY_UP?                        │  │
│  │                                                                 │  │
│  │  优先级4: 同系列高级材质 + 同等级产地                            │  │
│  │    例: Q235B 鞍钢 → Q235C 首钢(材质升级+产地换同级)             │  │
│  │    判断: 材质策略+产地策略均允许?                                │  │
│  │                                                                 │  │
│  │  优先级5: 同系列高级材质 + 更高等级产地                          │  │
│  │    例: Q235B 唐钢 → Q235C 鞍钢(材质+产地双升级)                 │  │
│  │                                                                 │  │
│  │  优先级6: 同材质 + 较低等级产地 (需客户确认)                     │  │
│  │    例: Q235B 鞍钢 → Q235B 日照(产地降级)                        │  │
│  │    判断: 客户是否接受? 白名单内?                                │  │
│  │                                                                 │  │
│  │  优先级7: 跨系列替代 (需技术+客户确认)                           │  │
│  │    例: Q235B → SS400 (国际对应标准)                              │  │
│  │    判断: 查 bas_grade_cross_substitute                          │  │
│  └──────────────────────────┬─────────────────────────────────────┘  │
│                              │                                        │
│  ┌──────────────────────────▼─────────────────────────────────────┐  │
│  │ Step 4: 结果排序与推荐                                           │  │
│  │                                                                 │  │
│  │  每个方案计算综合评分:                                           │  │
│  │    评分 = W1×可用量满足度 + W2×材质匹配度 + W3×产地匹配度        │  │
│  │         + W4×价格优势 + W5×审批便捷度                           │  │
│  │                                                                 │  │
│  │  材质匹配度: 精确=100, 同系列升1级=90, 升2级=80, 跨系列=50     │  │
│  │  产地匹配度: 精确=100, 同互换组=95, 同等级=85, 高等级=80        │  │
│  │  审批便捷度: 自动=100, 需确认=50, 需多方确认=20                 │  │
│  │                                                                 │  │
│  │  按评分降序排列, 推荐Top3                                       │  │
│  └──────────────────────────┬─────────────────────────────────────┘  │
│                              │                                        │
│  ┌──────────────────────────▼─────────────────────────────────────┐  │
│  │ Step 5: 产出物料的材质/产地标记更新                               │  │
│  │                                                                 │  │
│  │  原料替代后, 产出品的材质/产地继承规则:                          │  │
│  │  · 材质向上替代(Q235B→Q235C): 产出品标记为原始材质Q235B          │  │
│  │    (向上兼容, 客户合同写的Q235B, 给更好的没问题)                 │  │
│  │  · 产地替代: 产出品标记实际使用的产地                            │  │
│  │    (质保书上体现真实产地, 不能虚假标注)                          │  │
│  │  · 跨系列: 产出品标记实际材质, 需客户书面确认                    │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### 4.2 增强后的 Java 匹配引擎

```java
/**
 * 材质+产地 组合替代匹配引擎 (增强版)
 */
public class GradeOriginSubstituteMatcher {

    /**
     * 替代方案
     */
    public static class SubstituteOption {
        private StockItem stock;
        
        // 材质维度
        private String originalGrade;
        private String substituteGrade;
        private String gradeFamily;       // 材质系列
        private int gradeLevelDiff;       // 等级差(正数=向上, 0=同级, 负数=向下)
        private boolean isCrossFamily;    // 是否跨系列
        
        // 产地维度
        private String originalOrigin;
        private String substituteOrigin;
        private int originTierDiff;       // 质量等级差(正数=更好)
        private boolean isInExchangeGroup; // 是否在同互换组
        
        // 综合评估
        private BigDecimal availableQty;
        private BigDecimal matchScore;     // 综合匹配评分 0~100
        private BigDecimal priceDiff;      // 价格差异
        private boolean autoApproved;      // 是否可自动批准
        private boolean needTechConfirm;
        private boolean needCustomerConfirm;
        private String approvalReason;     // 需审批的原因描述
    }

    public List<SubstituteOption> findSubstitutes(
            Long materialId, String gradeCode, String originCode,
            BigDecimal requiredQty, String customerCode) {
        
        List<SubstituteOption> allOptions = new ArrayList<>();
        
        // 加载客户偏好
        CustomerMaterialPref pref = prefMapper.selectByCustomer(customerCode);
        if (pref == null) {
            pref = getDefaultPref(); // 默认: 同系列向上 + 同等级产地
        }
        
        // 加载材质等级信息
        GradeHierarchy sourceGrade = hierarchyMapper.selectByCode(gradeCode);
        OriginTier sourceOrigin = originTierMapper.selectByCode(originCode);
        
        // 查询所有可能的库存(同物料规格, 不同材质/产地)
        List<StockItem> candidates = stockMapper.selectByMaterialWithAnyGradeOrigin(
            materialId);
        
        for (StockItem stock : candidates) {
            if (stock.getAvailableQty().compareTo(BigDecimal.ZERO) <= 0) continue;
            
            SubstituteOption option = new SubstituteOption();
            option.setStock(stock);
            option.setOriginalGrade(gradeCode);
            option.setSubstituteGrade(stock.getGradeCode());
            option.setOriginalOrigin(originCode);
            option.setSubstituteOrigin(stock.getOriginCode());
            
            // === 材质维度判断 ===
            GradeHierarchy targetGrade = hierarchyMapper.selectByCode(stock.getGradeCode());
            
            boolean gradeOk = false;
            if (gradeCode.equals(stock.getGradeCode())) {
                // 同材质
                gradeOk = true;
                option.setGradeLevelDiff(0);
            } else if (sourceGrade != null && targetGrade != null 
                       && sourceGrade.getGradeFamily().equals(targetGrade.getGradeFamily())) {
                // 同系列
                int diff = targetGrade.getGradeLevel() - sourceGrade.getGradeLevel();
                option.setGradeLevelDiff(diff);
                option.setGradeFamily(sourceGrade.getGradeFamily());
                
                if (diff > 0) {
                    // 向上替代
                    gradeOk = isGradePolicyAllowed(pref.getGradeSubstitutePolicy(), "UP");
                } else if (diff == 0) {
                    gradeOk = true;
                } else {
                    // 向下替代 — 通常不允许
                    gradeOk = "ANY".equals(pref.getGradeSubstitutePolicy());
                }
            } else {
                // 跨系列
                option.setIsCrossFamily(true);
                GradeCrossSubstitute cross = crossMapper.select(gradeCode, stock.getGradeCode());
                gradeOk = (cross != null && cross.getIsActive());
                if (gradeOk) {
                    option.setNeedTechConfirm(cross.getNeedTechConfirm());
                    option.setNeedCustomerConfirm(cross.getNeedCustomerConfirm());
                }
            }
            
            if (!gradeOk) continue;
            
            // === 产地维度判断 ===
            boolean originOk = false;
            if (originCode == null || originCode.equals(stock.getOriginCode())) {
                originOk = true;
                option.setOriginTierDiff(0);
            } else {
                OriginTier targetOrigin = originTierMapper.selectByCode(stock.getOriginCode());
                
                switch (pref.getOriginSubstitutePolicy()) {
                    case "STRICT":
                        originOk = false;
                        break;
                    case "WHITELIST":
                        originOk = whitelistMapper.exists(customerCode, stock.getOriginCode());
                        break;
                    case "SAME_TIER":
                        originOk = (sourceOrigin != null && targetOrigin != null 
                                   && sourceOrigin.getQualityTier() == targetOrigin.getQualityTier());
                        break;
                    case "TIER_UP":
                        originOk = (sourceOrigin != null && targetOrigin != null 
                                   && targetOrigin.getQualityTier() <= sourceOrigin.getQualityTier());
                        break;
                    case "ANY":
                        originOk = true;
                        break;
                }
                
                // 检查互换组
                if (!originOk && originCode != null) {
                    option.setIsInExchangeGroup(
                        exchangeGroupMapper.isInSameGroup(originCode, stock.getOriginCode()));
                    if (option.isInExchangeGroup()) {
                        originOk = true;
                    }
                }
                
                if (targetOrigin != null && sourceOrigin != null) {
                    option.setOriginTierDiff(
                        sourceOrigin.getQualityTier() - targetOrigin.getQualityTier());
                }
            }
            
            if (!originOk) continue;
            
            // === 客户黑名单检查 ===
            if (blacklistMapper.exists(customerCode, stock.getGradeCode())) continue;
            
            // === 计算综合评分 ===
            option.setMatchScore(calcMatchScore(option, pref));
            
            // === 判断审批方式 ===
            option.setAutoApproved(determineAutoApproval(option, pref));
            
            option.setAvailableQty(stock.getAvailableQty());
            allOptions.add(option);
        }
        
        // 按评分排序
        allOptions.sort(Comparator.comparing(SubstituteOption::getMatchScore).reversed());
        
        return allOptions;
    }
    
    private BigDecimal calcMatchScore(SubstituteOption opt, CustomerMaterialPref pref) {
        double score = 0;
        
        // 材质匹配度 (40%)
        if (opt.getGradeLevelDiff() == 0 && !opt.isCrossFamily()) {
            score += 40; // 同材质
        } else if (!opt.isCrossFamily() && opt.getGradeLevelDiff() == 1) {
            score += 36; // 同系列升1级
        } else if (!opt.isCrossFamily() && opt.getGradeLevelDiff() == 2) {
            score += 32; // 同系列升2级
        } else if (opt.isCrossFamily()) {
            score += 20; // 跨系列
        }
        
        // 产地匹配度 (30%)
        if (opt.getOriginTierDiff() == 0) {
            if (opt.getOriginalOrigin().equals(opt.getSubstituteOrigin())) {
                score += 30; // 同产地
            } else if (opt.isInExchangeGroup()) {
                score += 28; // 同互换组
            } else {
                score += 25; // 同等级
            }
        } else if (opt.getOriginTierDiff() > 0) {
            score += 24; // 更高等级产地
        } else {
            score += 15; // 更低等级产地
        }
        
        // 审批便捷度 (20%)
        if (opt.isAutoApproved()) score += 20;
        else if (!opt.isNeedCustomerConfirm()) score += 12;
        else score += 5;
        
        // 数量满足度 (10%)
        // ...
        
        return new BigDecimal(score);
    }
}
```

---

## 5. 产出物料的材质/产地继承

### 5.1 继承规则

```
原料替代后, 产出成品的材质/产地标记规则:

┌──────────────────────────────────────────────────────────────────────┐
│                                                                      │
│  规则1: 材质向上替代 → 产出标记原始材质                                │
│                                                                      │
│  原料: Q235B 带钢(需求) → 实际用 Q235C 带钢(替代)                    │
│  产出: 方管 标记 Q235B (因为Q235C≥Q235B, 产品满足Q235B标准)           │
│  质保书: 原材质 Q235C, 产品执行标准 Q235B                            │
│                                                                      │
│  规则2: 产地替代 → 产出标记实际产地                                    │
│                                                                      │
│  原料: 鞍钢 带钢(需求) → 实际用 首钢 带钢(替代)                      │
│  产出: 方管 标记 产地=首钢 (真实溯源, 不能虚假标注)                    │
│  报工/入库时自动更新产出批次的产地字段                                  │
│                                                                      │
│  规则3: 跨系列替代 → 产出标记实际材质 + 特殊标注                      │
│                                                                      │
│  原料: Q235B(需求) → 实际用 SS400(替代, 国际等效)                     │
│  产出: 方管 标记 SS400, 备注"客户确认替代Q235B"                       │
│                                                                      │
│  规则4: 一个排产单多次换料不同材质/产地 → 按段分批次标记               │
│                                                                      │
│  排产 SCH-013: 35T                                                   │
│  段1: 20T 用 Q235B 鞍钢 → 产出批次P-001 材质Q235B 产地鞍钢           │
│  段2: 15T 用 Q235B 首钢 → 产出批次P-002 材质Q235B 产地首钢           │
│  (同材质不同产地 → 必须分批次, 质保书不同)                              │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### 5.2 排产/报工表增强

```sql
-- aps_schedule 增加替代后的实际材质/产地标记
ALTER TABLE aps_schedule ADD (
    -- 需求要求的材质/产地(来自订单)
    demand_grade_code   VARCHAR(30)   NULL,
    demand_origin_code  VARCHAR(30)   NULL,
    
    -- 实际使用的材质/产地(可能与需求不同=替代)
    actual_grade_code   VARCHAR(30)   NULL,
    actual_origin_code  VARCHAR(30)   NULL,
    
    -- 产出标记的材质/产地
    output_grade_code   VARCHAR(30)   NULL,         -- 按继承规则自动计算
    output_origin_code  VARCHAR(30)   NULL,
    
    -- 是否发生了材质/产地替代
    grade_substituted   BIT           NOT NULL DEFAULT 0,
    origin_substituted  BIT           NOT NULL DEFAULT 0
);
```

---

## 6. UI 交互设计

### 6.1 替代料选择面板(增强版)

```
┌──────────────────────────────────────────────────────────────────────┐
│  替代料选择 — SCH-013 方管100×50×4.0                                  │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  需求: 带钢290×4.0 Q235B 鞍钢 — 缺 10T                              │
│  客户: XX建材 | 偏好: 材质同系列向上✅ 产地同等级或更高✅              │
│                                                                      │
│  系统推荐方案 (按匹配度排序):                                         │
│                                                                      │
│  ┌──┬──────────┬──────┬──────┬──────┬──────┬──────┬───────┬──────┐ │
│  │# │库存批次  │材质  │产地  │可用量│匹配度│审批  │价差   │选择  │ │
│  ├──┼──────────┼──────┼──────┼──────┼──────┼──────┼───────┼──────┤ │
│  │1 │C-012    │Q235B│首钢  │15T   │95分  │自动  │+30元/T│ ●    │ │
│  │  │290×4.0  │同材质│同等级│      │      │      │       │      │ │
│  │  │         │      │互换组│      │      │      │       │      │ │
│  ├──┼──────────┼──────┼──────┼──────┼──────┼──────┼───────┼──────┤ │
│  │2 │C-015    │Q235C│鞍钢  │12T   │92分  │自动  │+50元/T│ ○    │ │
│  │  │290×4.0  │升1级│同产地│      │      │      │       │      │ │
│  ├──┼──────────┼──────┼──────┼──────┼──────┼──────┼───────┼──────┤ │
│  │3 │C-018    │Q235C│唐钢  │20T   │85分  │自动  │-20元/T│ ○    │ │
│  │  │290×4.0  │升1级│良好级│      │      │      │       │      │ │
│  ├──┼──────────┼──────┼──────┼──────┼──────┼──────┼───────┼──────┤ │
│  │4 │C-020    │Q235B│日照  │25T   │78分  │需确认│-80元/T│ ○    │ │
│  │  │290×4.0  │同材质│良好级│      │客户  │      │       │      │ │
│  ├──┼──────────┼──────┼──────┼──────┼──────┼──────┼───────┼──────┤ │
│  │5 │C-025    │Q235D│宝钢  │8T    │82分  │自动  │+120/T │ ○    │ │
│  │  │290×4.0  │升2级│顶级  │      │      │      │       │      │ │
│  └──┴──────────┴──────┴──────┴──────┴──────┴──────┴───────┴──────┘ │
│                                                                      │
│  ┌─── 产出影响预览 ──────────────────────────────────────────────┐  │
│  │  选择方案1 (Q235B 首钢) 后:                                    │  │
│  │  · 产出方管标记: 材质 Q235B (不变)  产地 首钢 (鞍钢→首钢)      │  │
│  │  · 质保书产地标注变更, 需通知客户                               │  │
│  │  · 成本影响: +300元 (10T × 30元/T)                             │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  [确认选择]  [取消]                                                  │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### 6.2 材质/产地替代追溯报表增强

```
在 R13 替代料使用分析报表基础上增加:

┌──────────────────────────────────────────────────────────────────────┐
│  材质/产地替代详细分析                                    [本月▼]     │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─── 替代类型分布 ──────────────────────────┐                      │
│  │  同材质换产地(同等级)  ████████████  35次  │                      │
│  │  同材质换产地(升等级)  ██████        15次  │                      │
│  │  材质升级同产地        ████████      20次  │                      │
│  │  材质升级+换产地       ████          10次  │                      │
│  │  跨系列替代            █              3次  │                      │
│  └───────────────────────────────────────────┘                      │
│                                                                      │
│  ┌─── 各客户替代情况 ──────────────────────────┐                    │
│  │  XX建材: 替代12次, 全部自动批准, 无投诉      │                    │
│  │  YY钢构: 替代 5次, 3次需客户确认, 均已批准   │                    │
│  │  ZZ出口: 替代 0次 (客户策略: 严格不替代)      │                    │
│  │  AA车辆: 替代 8次, 1次被客户驳回(产地不接受)  │                    │
│  └──────────────────────────────────────────────┘                    │
│                                                                      │
│  ┌─── 成本影响汇总 ──────────────────────────────────────────────┐  │
│  │  因替代料使用的总成本差异:                                      │  │
│  │  材质升级成本增加:   +￥45,200 (高级材质单价更高)                │  │
│  │  产地切换成本节省:   -￥28,500 (部分换了更便宜的产地)            │  │
│  │  净成本影响:         +￥16,700 (占原料总成本 0.3%)               │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 7. API 接口补充

```
# ═══ 材质等级 ═══
GET    /api/v1/grade/hierarchy                  # 材质等级体系列表
GET    /api/v1/grade/hierarchy/{family}          # 某系列的等级列表
GET    /api/v1/grade/cross-substitute            # 跨系列替代规则

# ═══ 产地分级 ═══
GET    /api/v1/origin/tier                       # 产地质量分级列表
GET    /api/v1/origin/exchange-group              # 产地互换组列表
GET    /api/v1/origin/exchange-group/{id}/members # 互换组成员

# ═══ 客户偏好 ═══
GET    /api/v1/customer/{code}/material-pref      # 客户材质/产地偏好
PUT    /api/v1/customer/{code}/material-pref      # 设置客户偏好
GET    /api/v1/customer/{code}/origin-whitelist    # 客户产地白名单
PUT    /api/v1/customer/{code}/origin-whitelist    # 维护白名单

# ═══ 增强替代匹配 ═══
POST   /api/v1/substitute/match                   # 材质+产地组合替代匹配
       Body: { materialId, gradeCode, originCode, qty, customerCode }
       Response: [ { stock, matchScore, gradeInfo, originInfo, approval, priceDiff } ]
```
