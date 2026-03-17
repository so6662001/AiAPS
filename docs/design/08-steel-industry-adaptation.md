# AiAPS — 钢铁行业深度适配设计（核心优化）

> 版本：2.0 | 最后更新：2026-03-17
>
> 本文档是对 V1.0 版设计的核心优化，解决钢铁行业与传统离散制造 MRP 的本质差异。

---

## 0. 问题诊断：钢铁行业 vs 传统 MRP 的根本差异

```
传统离散制造 MRP:
  成品(精确规格) ──精确BOM──→ 半成品(精确规格) ──精确BOM──→ 原材料(精确规格)
  每一层都是确定的物料编码，用量精确

钢铁行业实际情况:
  成品(精确规格) ──品类BOM──→ 原料品类(规格需推算) ──公式计算──→ 原料规格区间
  │                            │
  │ 方管 100×50×4.0            │ 原料=带钢, 宽度≈296~300mm, 厚度=4.0mm
  │ 方管 80×40×3.0             │ 原料=带钢, 宽度≈236~240mm, 厚度=3.0mm
  │                            │
  └── 品类层面是确定的           └── 规格是通过公式推算出来的(区间值)
      (方管→带钢)                   不是精确BOM里写死的
```

**本次优化解决的五个核心问题：**

| # | 问题 | 现有设计缺陷 | 优化方案 |
|---|------|------------|---------|
| 1 | 物料模型 | 材质/产地写死在物料主数据里 | 材质/产地从物料剥离，成为库存批次属性 |
| 2 | BOM 体系 | 只有精确BOM，无法处理品类级关系 | 双轨BOM：品类BOM(公式推算) + 离散BOM(精确) |
| 3 | 排产模式 | 单阶段排产 | 多阶段排产：原料层排产→制造层排产 |
| 4 | 排产驱动 | 只有需求驱动 | 双驱动：需求驱动 + 原料驱动(行情买料后反推产品) |
| 5 | 分剪模式 | 一对一排产 | 一分X模式：一个原料→多个产出 |

---

## 1. 物料主数据模型重构

### 1.1 核心理念：物料 = 品类 + 规格，材质和产地跟着库存批次走

```
重构前（V1.0）:
  物料编码 = GC-Q235B-Φ89×4.0×6000-HG
  材质 Q235B 嵌在编码里 → 同规格不同材质 = 不同物料 → 物料数爆炸

重构后（V2.0）:
  物料编码 = GC-Φ89×4.0×6000-HG    ← 只到品类+规格+表面处理
  材质 Q235B / Q345B               ← 独立维度，属于库存批次属性
  产地 鞍钢 / 唐钢                  ← 独立维度，属于库存批次属性

好处：
  · 一个物料编码覆盖所有材质，物料主数据量大幅减少
  · 材质/产地在库存、需求、排产中作为属性参与匹配
  · 符合钢铁行业实际：客户下单时指定"方管100×50×4.0 Q235B"
    其中方管100×50×4.0是物料，Q235B是材质属性
```

### 1.2 重构后的物料主数据表 (bas_material)

```sql
CREATE TABLE bas_material (
    material_id         BIGINT IDENTITY(1,1) PRIMARY KEY,
    material_code       VARCHAR(60)   NOT NULL,     -- 物料编码(品类+规格，不含材质/产地)
    material_name       NVARCHAR(200) NOT NULL,     -- 物料名称
    category_code       VARCHAR(20)   NOT NULL,     -- 品类编码: PIPE/RPIPE/PROFILE/PLATE/STRIP/COIL
                                                    --          /BEAM_FRAME/BEND_PART/OTHER
    category_name       NVARCHAR(50)  NULL,         -- 品类名称
    material_type       VARCHAR(20)   NOT NULL,     -- 属性: RAW/SEMI/FG/PACK
    unit_code           VARCHAR(10)   NOT NULL,     -- 基本计量单位: T/KG/M/PCS
    aux_unit_code       VARCHAR(10)   NULL,         -- 辅助计量单位
    unit_convert_rate   DECIMAL(18,6) NULL,         -- 辅助→基本换算率
    
    -- ═══ 规格参数（不同品类使用不同字段组合）═══
    spec_desc           NVARCHAR(200) NULL,         -- 规格描述文本(展示用)
    thickness           DECIMAL(10,3) NULL,         -- 厚度/壁厚 mm
    width               DECIMAL(10,3) NULL,         -- 宽度 mm (带钢/板材)
    length              DECIMAL(10,3) NULL,         -- 长度 mm (定尺)
    outer_diameter      DECIMAL(10,3) NULL,         -- 外径 mm (圆管)
    height              DECIMAL(10,3) NULL,         -- 高度 mm (方管高/型材腹板高)
    flange_width        DECIMAL(10,3) NULL,         -- 翼缘宽 mm (型材)
    web_thickness       DECIMAL(10,3) NULL,         -- 腹板厚 mm (型材)
    flange_thickness    DECIMAL(10,3) NULL,         -- 翼缘厚 mm (型材)
    inner_diameter      DECIMAL(10,3) NULL,         -- 内径 mm (无缝管)
    side_a              DECIMAL(10,3) NULL,         -- 边长A mm (方管/矩管)
    side_b              DECIMAL(10,3) NULL,         -- 边长B mm (矩管)
    corner_radius       DECIMAL(10,3) NULL,         -- 圆角半径 mm (管材)
    surface_treatment   VARCHAR(20)   NULL,         -- 表面处理: HG/DX/PH/RM/WU(无)
    
    -- ═══ 理论重量（用于重量换算）═══
    theory_weight_per_m DECIMAL(18,6) NULL,         -- 理论米重 kg/m
    theory_weight_per_pc DECIMAL(18,6) NULL,        -- 理论支重 kg/支
    
    -- ═══ 计划属性 ═══
    lot_policy          VARCHAR(10)   NOT NULL DEFAULT 'LFL',
    fixed_lot_qty       DECIMAL(18,3) NULL,
    min_order_qty       DECIMAL(18,3) NULL,
    lot_multiple        DECIMAL(18,3) NULL,
    lead_time_days      INT           NOT NULL DEFAULT 0,
    safety_stock_qty    DECIMAL(18,3) NULL,
    safety_lead_days    INT           NULL,
    procurement_type    VARCHAR(10)   NOT NULL DEFAULT 'M',
                                                    -- M=自制 P=采购 O=委外
    is_active           BIT           NOT NULL DEFAULT 1,
    created_by          VARCHAR(50)   NULL,
    created_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    updated_by          VARCHAR(50)   NULL,
    updated_time        DATETIME      NULL,
    CONSTRAINT UK_material_code UNIQUE (material_code)
);

CREATE INDEX IX_material_category ON bas_material(category_code);
CREATE INDEX IX_material_type ON bas_material(material_type);
```

### 1.3 材质字典 (bas_steel_grade)

```sql
CREATE TABLE bas_steel_grade (
    grade_id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    grade_code          VARCHAR(30)   NOT NULL,     -- 材质编码: Q235B, Q345B, 20#, 45#, 304...
    grade_name          NVARCHAR(60)  NOT NULL,     -- 材质名称
    grade_group         VARCHAR(20)   NULL,         -- 材质分组: CARBON/LOW_ALLOY/ALLOY/STAINLESS
    standard_code       VARCHAR(30)   NULL,         -- 执行标准: GB/T700, GB/T1591
    density             DECIMAL(10,4) NULL,         -- 密度 g/cm³
    is_active           BIT           NOT NULL DEFAULT 1,
    CONSTRAINT UK_grade_code UNIQUE (grade_code)
);
```

### 1.4 产地字典 (bas_origin)

```sql
CREATE TABLE bas_origin (
    origin_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    origin_code         VARCHAR(30)   NOT NULL,     -- 产地编码
    origin_name         NVARCHAR(60)  NOT NULL,     -- 产地名称: 鞍钢/宝钢/唐钢/日照...
    origin_type         VARCHAR(20)   NULL,         -- DOMESTIC/IMPORT
    is_active           BIT           NOT NULL DEFAULT 1,
    CONSTRAINT UK_origin_code UNIQUE (origin_code)
);
```

### 1.5 库存表重构 — 材质/产地在批次级 (inv_stock)

```sql
CREATE TABLE inv_stock (
    stock_id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    material_id         BIGINT        NOT NULL,     -- 物料(品类+规格)
    grade_code          VARCHAR(30)   NULL,         -- 材质(库存属性)
    origin_code         VARCHAR(30)   NULL,         -- 产地(库存属性)
    warehouse_code      VARCHAR(30)   NOT NULL,
    location_code       VARCHAR(30)   NULL,
    batch_no            VARCHAR(60)   NULL,         -- 批次号/炉号/卷号
    heat_no             VARCHAR(30)   NULL,         -- 炉号(钢铁特有)
    coil_no             VARCHAR(30)   NULL,         -- 卷号(钢卷/带钢特有)
    cert_no             VARCHAR(60)   NULL,         -- 质保书编号
    
    on_hand_qty         DECIMAL(18,3) NOT NULL DEFAULT 0,
    on_hand_weight      DECIMAL(18,3) NULL,         -- 实际重量(与理论重量可能有差异)
    reserved_qty        DECIMAL(18,3) NOT NULL DEFAULT 0,
    in_transit_qty      DECIMAL(18,3) NOT NULL DEFAULT 0,
    in_process_qty      DECIMAL(18,3) NOT NULL DEFAULT 0,
    quality_hold_qty    DECIMAL(18,3) NOT NULL DEFAULT 0,
    
    -- ═══ 实际规格(可能与物料标准规格有偏差) ═══
    actual_thickness    DECIMAL(10,3) NULL,         -- 实际厚度(钢卷实测)
    actual_width        DECIMAL(10,3) NULL,         -- 实际宽度
    actual_length       DECIMAL(10,3) NULL,         -- 实际长度
    actual_weight       DECIMAL(18,3) NULL,         -- 实际重量(过磅)
    coil_outer_dia      DECIMAL(10,3) NULL,         -- 钢卷外径
    coil_inner_dia      DECIMAL(10,3) NULL,         -- 钢卷内径
    
    unit_price          DECIMAL(18,2) NULL,         -- 单价(行情相关)
    purchase_date       DATETIME      NULL,         -- 采购日期
    last_updated        DATETIME      NOT NULL DEFAULT GETDATE(),
    
    CONSTRAINT FK_stock_material FOREIGN KEY (material_id)
        REFERENCES bas_material(material_id)
);

CREATE INDEX IX_stock_material ON inv_stock(material_id);
CREATE INDEX IX_stock_grade ON inv_stock(grade_code);
CREATE INDEX IX_stock_category ON inv_stock(material_id, grade_code);
CREATE INDEX IX_stock_coil ON inv_stock(coil_no);
```

### 1.6 需求单明细重构 — 需求也带材质

```sql
CREATE TABLE dem_demand_line (
    demand_line_id      BIGINT IDENTITY(1,1) PRIMARY KEY,
    demand_id           BIGINT        NOT NULL,
    line_no             INT           NOT NULL,
    material_id         BIGINT        NOT NULL,     -- 需求物料(品类+规格)
    grade_code          VARCHAR(30)   NULL,         -- 需求材质(客户指定)
    origin_code         VARCHAR(30)   NULL,         -- 需求产地(客户可能指定，也可能不限)
    required_qty        DECIMAL(18,3) NOT NULL,     -- 需求数量
    required_weight     DECIMAL(18,3) NULL,         -- 需求重量
    required_date       DATETIME      NOT NULL,
    grade_flexible      BIT           NOT NULL DEFAULT 0,  -- 材质是否可替代
    origin_flexible     BIT           NOT NULL DEFAULT 1,  -- 产地是否不限
    allocated_qty       DECIMAL(18,3) NOT NULL DEFAULT 0,
    produced_qty        DECIMAL(18,3) NOT NULL DEFAULT 0,
    line_status         VARCHAR(10)   NOT NULL DEFAULT 'OPEN',
    spec_desc           NVARCHAR(200) NULL,
    remark              NVARCHAR(200) NULL,
    CONSTRAINT FK_demand_line_head FOREIGN KEY (demand_id)
        REFERENCES dem_demand_head(demand_id),
    CONSTRAINT FK_demand_line_material FOREIGN KEY (material_id)
        REFERENCES bas_material(material_id)
);
```

---

## 2. 双轨 BOM 体系设计

### 2.1 核心理念

```
┌──────────────────────────────────────────────────────────────────┐
│                      AiAPS 双轨 BOM 体系                         │
│                                                                  │
│  ┌────────────────────────┐    ┌────────────────────────────┐   │
│  │    品类 BOM (Category) │    │   离散 BOM (Discrete)       │   │
│  │                        │    │                            │   │
│  │  · 父子关系在品类层     │    │  · 传统精确 BOM             │   │
│  │  · 规格通过公式推算     │    │  · 每个子项是确定的物料     │   │
│  │  · 用量通过参数计算     │    │  · 用量是固定值             │   │
│  │                        │    │                            │   │
│  │  适用：                 │    │  适用：                     │   │
│  │  · 管材 (方管/圆管)    │    │  · 货车车架                 │   │
│  │  · 型材               │    │  · 货车车梁                 │   │
│  │  · 开平/分剪          │    │  · 组装类产品               │   │
│  │  · 剪切/折弯          │    │  · 有明确子件清单的产品     │   │
│  └────────────────────────┘    └────────────────────────────┘   │
│                                                                  │
│  系统自动识别：                                                    │
│  · 物料有离散 BOM → 使用离散 BOM 精确展开                          │
│  · 物料无离散 BOM，有品类 BOM → 使用品类 BOM + 公式推算             │
│  · 两者都有 → 优先使用离散 BOM                                    │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 品类 BOM 表 (bas_category_bom)

```sql
-- 品类级 BOM：定义品类间的父子关系和规格推算公式
CREATE TABLE bas_category_bom (
    cat_bom_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    cat_bom_code        VARCHAR(60)   NOT NULL,
    parent_category     VARCHAR(20)   NOT NULL,     -- 父品类: RECT_PIPE (方管)
    child_category      VARCHAR(20)   NOT NULL,     -- 子品类: STRIP (带钢)
    calc_formula_code   VARCHAR(30)   NOT NULL,     -- 规格推算公式编码
    scrap_rate          DECIMAL(8,4)  NOT NULL DEFAULT 0.03,  -- 损耗率
    fixed_scrap_qty     DECIMAL(18,3) NOT NULL DEFAULT 0,
    yield_rate          DECIMAL(8,4)  NOT NULL DEFAULT 1.0,   -- 成材率
    is_active           BIT           NOT NULL DEFAULT 1,
    remark              NVARCHAR(500) NULL,
    CONSTRAINT UK_cat_bom UNIQUE (cat_bom_code),
    CONSTRAINT UK_cat_bom_rel UNIQUE (parent_category, child_category, calc_formula_code)
);
```

### 2.3 规格推算公式库 (bas_spec_formula)

```sql
-- 规格推算公式定义
-- 每个公式定义了"产出物料规格 → 原料规格"的计算逻辑
CREATE TABLE bas_spec_formula (
    formula_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    formula_code        VARCHAR(30)   NOT NULL,     -- 公式编码
    formula_name        NVARCHAR(100) NOT NULL,     -- 公式名称
    parent_category     VARCHAR(20)   NOT NULL,     -- 产出品类
    child_category      VARCHAR(20)   NOT NULL,     -- 原料品类
    formula_type        VARCHAR(20)   NOT NULL,     -- PARAMETRIC=参数公式 TABLE=查表 CUSTOM=自定义
    
    -- ═══ 参数公式定义 ═══
    -- 使用变量: ${side_a} ${side_b} ${thickness} ${outer_diameter} ${height}
    --          ${width} ${length} ${corner_radius} ${flange_width} 等
    -- 对应 bas_material 的规格字段
    
    -- 原料宽度计算公式(结果单位:mm)
    width_formula       NVARCHAR(500) NULL,
    -- 原料宽度允许偏差(mm)，用于匹配库存
    width_tolerance_min DECIMAL(10,3) NULL,
    width_tolerance_max DECIMAL(10,3) NULL,
    
    -- 原料厚度计算公式(结果单位:mm)
    thickness_formula   NVARCHAR(500) NULL,
    -- 原料厚度允许偏差(mm)
    thickness_tol_min   DECIMAL(10,3) NULL,
    thickness_tol_max   DECIMAL(10,3) NULL,
    
    -- 原料长度计算公式(可选)
    length_formula      NVARCHAR(500) NULL,
    
    -- 用量(重量)计算公式
    -- 结果: 生产1吨产出物料需要多少吨原料(考虑损耗)
    weight_formula      NVARCHAR(500) NULL,
    
    -- 联产品/余料计算(可选)
    byproduct_formula   NVARCHAR(500) NULL,
    
    is_active           BIT           NOT NULL DEFAULT 1,
    remark              NVARCHAR(500) NULL,
    CONSTRAINT UK_formula_code UNIQUE (formula_code)
);
```

### 2.4 各品类规格推算公式详解

#### 2.4.1 方管/矩管 → 带钢

```
品类关系: 方管(RECT_PIPE) → 带钢(STRIP)

已知参数 (来自产出物料 bas_material):
  side_a        = 方管长边 (mm)，如 100
  side_b        = 方管短边 (mm)，如 50
  thickness     = 壁厚 (mm)，如 4.0
  corner_radius = 圆角半径 (mm)，如 2×thickness = 8.0
  length        = 定尺长度 (mm)，如 6000

推算原料规格:
  ┌────────────────────────────────────────────────────────────────┐
  │  带钢宽度 = 展开周长                                            │
  │                                                                │
  │  周长 = 2×(side_a + side_b) - 8×corner_radius + 2×π×corner_radius│
  │       = 2×(100 + 50) - 8×8 + 2×3.14159×8                       │
  │       = 300 - 64 + 50.27                                       │
  │       = 286.27 mm                                               │
  │                                                                │
  │  考虑焊缝和成型余量:                                             │
  │  带钢宽度 ≈ 周长 + 成型余量(通常 2~5mm)                          │
  │          ≈ 286.27 + 3 ≈ 289 mm                                  │
  │                                                                │
  │  实际匹配时按区间: 286 ~ 292 mm                                  │
  │                                                                │
  │  带钢厚度 = 壁厚 = 4.0 mm  (允许 ±0.15mm)                      │
  │                                                                │
  │  带钢重量 = 产出重量 / 成材率                                    │
  │  成材率 ≈ 0.95 ~ 0.97 (含切头切尾、焊缝损耗)                    │
  └────────────────────────────────────────────────────────────────┘

公式配置:
  width_formula       = '2*(${side_a}+${side_b})-8*${corner_radius}+2*3.14159*${corner_radius}+3'
  width_tolerance_min = -3      -- 允许偏小 3mm
  width_tolerance_max = 5       -- 允许偏大 5mm
  thickness_formula   = '${thickness}'
  thickness_tol_min   = -0.15
  thickness_tol_max   = 0.15
  weight_formula      = '1/${yield_rate}'    -- yield_rate=0.96
```

#### 2.4.2 圆管 → 带钢

```
品类关系: 圆管(ROUND_PIPE) → 带钢(STRIP)

已知参数:
  outer_diameter = 外径 (mm)，如 Φ89
  thickness      = 壁厚 (mm)，如 4.0

推算:
  ┌────────────────────────────────────────────────────┐
  │  带钢宽度 = π × (outer_diameter - thickness)       │
  │           = 3.14159 × (89 - 4.0)                  │
  │           = 3.14159 × 85                           │
  │           = 267.04 mm                              │
  │                                                    │
  │  匹配区间: 265 ~ 270 mm                            │
  │  带钢厚度 = 壁厚 = 4.0 mm                          │
  └────────────────────────────────────────────────────┘

公式配置:
  width_formula       = '3.14159*(${outer_diameter}-${thickness})'
  width_tolerance_min = -2
  width_tolerance_max = 3
  thickness_formula   = '${thickness}'
```

#### 2.4.3 开平/分剪: 钢卷 → 板材/窄带 (一分X模式)

```
品类关系: 
  分剪(SLIT): 钢卷(COIL) → 多条窄带钢(STRIP)  — 纵切
  开平(LEVEL): 钢卷(COIL) → 多张板材(PLATE)   — 横切

一分X模式 (分剪):
  ┌────────────────────────────────────────────────────┐
  │  一个钢卷(宽1500mm, 厚4.0mm, 重20T)               │
  │                                                    │
  │  可分剪为:                                          │
  │  ├── 方案A: 1500 = 300×5 → 5条300mm带钢            │
  │  ├── 方案B: 1500 = 400×3 + 300×1 → 3+1条           │
  │  ├── 方案C: 1500 = 500×2 + 250×2 → 2+2条           │
  │  └── 方案D: 1500 = 289×5 + 55(余料) → 5条+余料     │
  │                                                    │
  │  最优分剪方案 = 使余料最小化                         │
  │  余料 = 钢卷宽度 - Σ(各条带宽) - 边丝损耗            │
  │  边丝损耗 ≈ 每边 5~10mm                             │
  └────────────────────────────────────────────────────┘

公式配置 (分剪是反向: 已知子料规格，匹配母卷):
  -- 对于分剪，公式方向相反: 子料宽度×N ≤ 母卷宽度
  -- 需要特殊的套裁优化算法，不是简单公式
  formula_type = 'CUSTOM'  -- 使用自定义套裁算法

开平:
  ┌────────────────────────────────────────────────────┐
  │  一个钢卷(宽1500mm, 厚6.0mm, 重25T)               │
  │  开平后: 板材 6.0×1500×L                           │
  │                                                    │
  │  定尺板: 6.0×1500×6000                             │
  │  可出板数 = 钢卷长度 / (定尺长度 + 切割损耗)        │
  │  钢卷长度 ≈ 重量/(厚度×宽度×密度)                   │
  │          ≈ 25000/(0.006×1.5×7.85×1000)             │
  │          ≈ 354m → 可出 354/6.05 ≈ 58 张             │
  └────────────────────────────────────────────────────┘
```

#### 2.4.4 型材: 型钢 → 钢坯

```
品类关系: H型钢(H_STEEL) → 钢坯(BILLET) 或 热轧板卷(HOT_COIL)

已知参数 (H型钢):
  height          = 腹板高度 H (mm)
  flange_width    = 翼缘宽度 B (mm)
  web_thickness   = 腹板厚度 t1 (mm)
  flange_thickness= 翼缘厚度 t2 (mm)

推算 (如果是焊接H型钢，由板材焊接):
  ┌────────────────────────────────────────────────────┐
  │  翼缘板: 2块, 规格 t2 × B × L                     │
  │  腹板:   1块, 规格 t1 × (H-2×t2) × L              │
  │                                                    │
  │  示例: H200×100×5.5×8                              │
  │  翼缘板: 8.0 × 100 × L → 需 8.0mm 厚板 (宽≥100)   │
  │  腹板:   5.5 × 184 × L → 需 5.5mm 厚板 (宽≥184)   │
  │                                                    │
  │  原料品类 = PLATE (板材)                            │
  │  分别推算翼缘和腹板的板材规格                       │
  └────────────────────────────────────────────────────┘
```

#### 2.4.5 折弯件 → 板材

```
品类关系: 折弯件(BEND_PART) → 板材(PLATE)

推算:
  ┌────────────────────────────────────────────────────┐
  │  折弯展开长度 = Σ 各段直线长度 + Σ 弯曲补偿        │
  │                                                    │
  │  弯曲补偿 = (π/180) × 弯曲角度 × (弯曲半径 + K系数×厚度)│
  │  K系数: 内弯 ≈ 0.33, 外弯 ≈ 0.50                   │
  │                                                    │
  │  板材宽度 ≥ 折弯展开长度                            │
  │  板材厚度 = 折弯件厚度                              │
  │  板材长度 ≥ 折弯件长度                              │
  └────────────────────────────────────────────────────┘
```

#### 2.4.6 车架/车梁 → 离散 BOM

```
车架和车梁使用传统离散 BOM (bas_bom_head + bas_bom_detail)
每个子件是确定的物料编码:

  车架总成 (FG)
  ├── 纵梁 (SEMI) × 2 ← 可以有离散BOM或品类BOM
  │   └── 板材 16×300×12000 (RAW)
  ├── 横梁 (SEMI) × 6
  │   └── 槽钢 10# (RAW)
  ├── 连接板 (SEMI) × 12
  │   └── 板材 10×200×300 (RAW) ← 剪切/折弯
  ├── 螺栓组 (PACK) × N
  └── 铆钉 (PACK) × N

注意: 纵梁/横梁如果涉及折弯/剪切工序,
      其原料板材仍然可以通过品类BOM+公式推算
      → 离散BOM和品类BOM可以混合使用
```

### 2.5 公式计算引擎 (Java 设计)

```java
/**
 * 规格推算引擎 — 核心类
 * 根据产出物料的规格参数，计算所需原料的规格区间
 */
public class SpecCalculationEngine {
    
    /**
     * 计算结果: 原料规格区间
     */
    public static class RawMaterialSpec {
        private String childCategory;       // 原料品类
        private BigDecimal widthMin;         // 宽度下限
        private BigDecimal widthMax;         // 宽度上限
        private BigDecimal thicknessMin;     // 厚度下限
        private BigDecimal thicknessMax;     // 厚度上限
        private BigDecimal lengthMin;        // 长度下限(可选)
        private BigDecimal lengthMax;        // 长度上限(可选)
        private BigDecimal weightPerUnit;    // 每吨(或每件)产出需原料重量
        private BigDecimal scrapRate;        // 损耗率
    }
    
    /**
     * 根据产出物料计算原料规格
     * @param product     产出物料 (bas_material)
     * @param quantity    产出数量
     * @param formula     规格推算公式 (bas_spec_formula)
     * @param categoryBom 品类BOM (bas_category_bom)
     * @return 原料规格区间及用量
     */
    public RawMaterialSpec calculate(Material product, 
                                     BigDecimal quantity,
                                     SpecFormula formula,
                                     CategoryBom categoryBom) {
        
        RawMaterialSpec spec = new RawMaterialSpec();
        spec.setChildCategory(categoryBom.getChildCategory());
        
        // 构建参数上下文
        Map<String, BigDecimal> params = buildParamContext(product);
        
        // 计算原料宽度
        if (formula.getWidthFormula() != null) {
            BigDecimal calcWidth = evalFormula(formula.getWidthFormula(), params);
            spec.setWidthMin(calcWidth.add(formula.getWidthToleranceMin()));
            spec.setWidthMax(calcWidth.add(formula.getWidthToleranceMax()));
        }
        
        // 计算原料厚度
        if (formula.getThicknessFormula() != null) {
            BigDecimal calcThickness = evalFormula(formula.getThicknessFormula(), params);
            spec.setThicknessMin(calcThickness.add(formula.getThicknessTolMin()));
            spec.setThicknessMax(calcThickness.add(formula.getThicknessTolMax()));
        }
        
        // 计算用量
        BigDecimal yieldRate = categoryBom.getYieldRate();
        BigDecimal scrapRate = categoryBom.getScrapRate();
        spec.setScrapRate(scrapRate);
        
        // 原料需求量 = 产出量 / 成材率 × (1 + 损耗率) + 固定损耗
        spec.setWeightPerUnit(
            quantity.divide(yieldRate, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.ONE.add(scrapRate))
                    .add(categoryBom.getFixedScrapQty())
        );
        
        return spec;
    }
    
    /**
     * 将产出物料的规格字段映射为公式变量
     */
    private Map<String, BigDecimal> buildParamContext(Material product) {
        Map<String, BigDecimal> params = new HashMap<>();
        params.put("side_a", product.getSideA());
        params.put("side_b", product.getSideB());
        params.put("thickness", product.getThickness());
        params.put("outer_diameter", product.getOuterDiameter());
        params.put("height", product.getHeight());
        params.put("width", product.getWidth());
        params.put("length", product.getLength());
        params.put("corner_radius", product.getCornerRadius());
        params.put("flange_width", product.getFlangeWidth());
        params.put("web_thickness", product.getWebThickness());
        params.put("flange_thickness", product.getFlangeThickness());
        // corner_radius 默认 = 2 × thickness (如未指定)
        if (params.get("corner_radius") == null && params.get("thickness") != null) {
            params.put("corner_radius", params.get("thickness").multiply(new BigDecimal("2")));
        }
        return params;
    }
    
    /**
     * 简单公式求值器
     * 支持 +, -, *, /, 括号, 常量, 变量${xxx}
     * 实现方式: javax.script.ScriptEngine("JavaScript") 
     *   或使用 exp4j / MVEL 等轻量表达式引擎
     */
    private BigDecimal evalFormula(String formula, Map<String, BigDecimal> params) {
        String expr = formula;
        for (Map.Entry<String, BigDecimal> entry : params.entrySet()) {
            if (entry.getValue() != null) {
                expr = expr.replace("${" + entry.getKey() + "}", 
                                    entry.getValue().toPlainString());
            }
        }
        // 使用表达式引擎计算
        return expressionEvaluator.eval(expr);
    }
}
```

---

## 3. 多阶段排产模型

### 3.1 为什么需要多阶段排产

```
传统排产（V1.0 设计）:
  需求 → MRP → 排产(一步到位) → 执行

钢铁行业实际:
  需求 → MRP → 原料层排产 → 制造层排产 → 执行
                    │              │
                    │              └── 制管/开平/剪切/折弯 排产
                    │                  (在产线上的具体排序)
                    └── 先确定需要哪些带钢/钢卷
                        再统一安排制管/开平

为什么要分两步:
  1. 管材企业通常先做"带钢排产"，汇总所有管材需求对应的带钢规格
     然后统一采购/出库，再安排制管产线的排产
  2. 这样可以合并同规格带钢需求，减少采购批次
  3. 制管排产时，已经知道带钢到位情况，排产更准确
```

### 3.2 排产阶段模型

```
┌────────────────────────────────────────────────────────────────────────┐
│                        多阶段排产流程                                   │
│                                                                        │
│  ┌─────────────┐                                                       │
│  │ 阶段0: MRP   │  需求 → 品类BOM展开 → 原料需求(品类+规格区间+材质)     │
│  └──────┬──────┘                                                       │
│         │                                                              │
│         │  输出: 原料级计划订单                                         │
│         │  (如: 需要带钢 290×4.0 Q235B 约30T)                          │
│         │                                                              │
│  ┌──────▼──────┐                                                       │
│  │ 阶段1:       │                                                       │
│  │ 原料层排产    │  将原料需求匹配到具体库存/采购                         │
│  │ (备料排产)    │                                                       │
│  │              │  · 匹配已有库存(按规格区间+材质检索)                    │
│  │              │  · 不足部分生成采购建议                                │
│  │              │  · 分剪需求: 制定套裁方案(一分X)                       │
│  │              │  · 输出: 已匹配的原料清单 + 套裁方案                   │
│  └──────┬──────┘                                                       │
│         │                                                              │
│         │  原料已确认到位(或预计到位)                                    │
│         │                                                              │
│  ┌──────▼──────┐                                                       │
│  │ 阶段2:       │                                                       │
│  │ 制造层排产    │  将生产任务排到具体产线的具体时间                       │
│  │ (产线排产)    │                                                       │
│  │              │  · 制管产线: 按带钢规格分组 → 排产线/排时间             │
│  │              │  · 开平线:  按钢卷 → 排开平顺序                        │
│  │              │  · 剪切/折弯: 按板材 → 排加工顺序                      │
│  │              │  · 输出: 甘特图可视化的排产计划                        │
│  └──────┬──────┘                                                       │
│         │                                                              │
│  ┌──────▼──────┐                                                       │
│  │ 阶段3:       │                                                       │
│  │ 执行与报工    │  生产执行 → 报工 → 反馈 → 可能触发重排产               │
│  └─────────────┘                                                       │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

### 3.3 排产计划表结构增强

```sql
-- 排产主计划增加阶段和层级标识
ALTER TABLE aps_schedule ADD (
    schedule_phase      VARCHAR(10)   NOT NULL DEFAULT 'MFG',
                                                -- PREP=备料(阶段1) MFG=制造(阶段2)
    schedule_level      VARCHAR(10)   NOT NULL DEFAULT 'PRODUCT',
                                                -- RAW=原料层 PRODUCT=产品层
    parent_schedule_id  BIGINT        NULL,     -- 父排产单(原料排产→制造排产关联)
    raw_material_id     BIGINT        NULL,     -- 指定的原料物料ID(阶段2时绑定具体原料)
    raw_stock_id        BIGINT        NULL,     -- 指定的原料库存批次ID
    raw_grade_code      VARCHAR(30)   NULL,     -- 原料材质
    raw_coil_no         VARCHAR(30)   NULL,     -- 指定钢卷号(开平/分剪时)
    
    -- 产出可能有多个(分剪一分X)
    is_multi_output     BIT           NOT NULL DEFAULT 0,
    
    -- 套裁方案ID(分剪排产)
    nesting_plan_id     BIGINT        NULL
);
```

### 3.4 管材排产具体流程

```
步骤1: 汇总带钢需求

  需求1: 方管100×50×4.0 Q235B — 20T → 带钢 289×4.0 Q235B — 约21T
  需求2: 方管100×50×4.0 Q345B — 15T → 带钢 289×4.0 Q345B — 约16T
  需求3: 方管80×40×3.0  Q235B — 30T → 带钢 237×3.0 Q235B — 约31T
  需求4: 圆管Φ89×4.0   Q235B — 10T → 带钢 267×4.0 Q235B — 约10.5T

  汇总后:
  ┌──────────────────────────────────────────────────┐
  │  带钢 289×4.0  Q235B — 21T  (需求1)             │
  │  带钢 289×4.0  Q345B — 16T  (需求2)             │
  │  带钢 237×3.0  Q235B — 31T  (需求3)             │
  │  带钢 267×4.0  Q235B — 10.5T (需求4)            │
  └──────────────────────────────────────────────────┘

步骤2: 匹配库存

  检索库存: category=STRIP, 宽度在区间内, 厚度在区间内, 材质匹配
  
  库存实际情况:
  ├── 带钢 290×4.0 Q235B — 库存 15T (卷号 C2026-001)
  │   → 可匹配需求1 (宽度289±3, 厚度4.0±0.15 → 290×4.0 符合)
  │   → 分配 15T 给需求1, 剩余 21-15=6T 需采购/分剪
  │
  ├── 带钢 240×3.0 Q235B — 库存 20T (卷号 C2026-005)
  │   → 可匹配需求3 (宽度237±3~5 → 240 符合)
  │   → 分配 20T 给需求3, 剩余 31-20=11T
  │
  └── 宽带 1500×4.0 Q235B — 库存 30T
      → 可分剪为 289mm 和 267mm 带钢
      → 套裁方案: 1500 = 289×5 + 55(余料) 
      →   或者: 1500 = 289×2 + 267×3 + 121(余料)

步骤3: 制管产线排产

  焊管1线 (适合 Φ60~Φ150 规格):
  03-20 SHIFT1: 带钢290×4.0 → 方管100×50×4.0 — 15T (卷号C2026-001)
  03-20 SHIFT2: 带钢290×4.0 → 方管100×50×4.0 — 6T  (新采购)
  03-20 SHIFT3: 带钢267×4.0 → 圆管Φ89×4.0   — 10.5T
  
  焊管2线 (适合小口径):
  03-20 SHIFT1: 带钢240×3.0 → 方管80×40×3.0  — 20T (卷号C2026-005)
  03-20 SHIFT2: 带钢237×3.0 → 方管80×40×3.0  — 11T
```

---

## 4. 一分X 模式：分剪/开平排产设计

### 4.1 核心概念

```
分剪 (Slitting): 将宽钢卷/带钢纵向切割成多条窄带
  母卷 1500mm → 300mm×5 条 (一分五)
  
开平 (Leveling): 将钢卷横向切割成定尺板材
  钢卷 → N 张板材

特点:
  · 一个输入(母卷) → 多个输出(子带/子板)
  · 不同输出可能用于不同订单
  · 余料需要处理(废料/可再用余料)
  · 排产速度要求极快(现场即时决策)
```

### 4.2 套裁方案表 (aps_nesting_plan)

```sql
-- 套裁方案: 定义一个母卷如何分切
CREATE TABLE aps_nesting_plan (
    nesting_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    nesting_no          VARCHAR(30)   NOT NULL,
    nesting_type        VARCHAR(10)   NOT NULL,     -- SLIT=分剪 LEVEL=开平
    
    -- 母料信息
    source_material_id  BIGINT        NOT NULL,     -- 母卷物料
    source_stock_id     BIGINT        NULL,         -- 母卷库存批次
    source_grade_code   VARCHAR(30)   NULL,         -- 母卷材质
    source_coil_no      VARCHAR(30)   NULL,         -- 母卷卷号
    source_width        DECIMAL(10,3) NOT NULL,     -- 母卷宽度
    source_thickness    DECIMAL(10,3) NOT NULL,     -- 母卷厚度
    source_weight       DECIMAL(18,3) NOT NULL,     -- 母卷重量
    source_length_m     DECIMAL(18,3) NULL,         -- 母卷长度(米)，开平时使用
    
    -- 计划信息
    edge_trim           DECIMAL(10,3) NOT NULL DEFAULT 10,  -- 边丝宽度(每边, mm)
    kerf_width          DECIMAL(10,3) NOT NULL DEFAULT 2,   -- 锯缝宽度(mm)
    slit_count          INT           NOT NULL DEFAULT 1,   -- 分条数
    utilization_pct     DECIMAL(8,4)  NULL,         -- 利用率(%)
    waste_weight        DECIMAL(18,3) NULL,         -- 废料重量
    remainder_weight    DECIMAL(18,3) NULL,         -- 可用余料重量
    
    nesting_status      VARCHAR(10)   NOT NULL DEFAULT 'DRAFT',
                                                    -- DRAFT/CONFIRMED/IN_PROGRESS/COMPLETED
    created_by          VARCHAR(50)   NULL,
    created_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    CONSTRAINT UK_nesting_no UNIQUE (nesting_no)
);

-- 套裁明细: 每一条子带/每种子板的信息
CREATE TABLE aps_nesting_detail (
    nesting_detail_id   BIGINT IDENTITY(1,1) PRIMARY KEY,
    nesting_id          BIGINT        NOT NULL,
    line_no             INT           NOT NULL,     -- 行号
    
    -- 产出物料
    output_material_id  BIGINT        NULL,         -- 产出物料(如已知对应物料)
    output_width        DECIMAL(10,3) NOT NULL,     -- 子料宽度
    output_thickness    DECIMAL(10,3) NOT NULL,     -- 子料厚度(=母卷厚度)
    output_count        INT           NOT NULL DEFAULT 1,  -- 条数(分剪) 或 张数(开平)
    output_length       DECIMAL(10,3) NULL,         -- 定尺长度(开平用)
    output_weight       DECIMAL(18,3) NULL,         -- 单条/单张重量
    total_weight        DECIMAL(18,3) NULL,         -- 小计重量
    
    -- 关联需求
    demand_line_id      BIGINT        NULL,         -- 对应的需求行
    schedule_id         BIGINT        NULL,         -- 对应的排产单(制造层)
    
    -- 类型标识
    output_type         VARCHAR(10)   NOT NULL DEFAULT 'PRODUCT',
                                                    -- PRODUCT=产品 REMAINDER=余料 WASTE=废料
    remark              NVARCHAR(200) NULL,
    
    CONSTRAINT FK_nesting_detail_head FOREIGN KEY (nesting_id)
        REFERENCES aps_nesting_plan(nesting_id)
);
```

### 4.3 套裁优化算法

```java
/**
 * 套裁优化器 — 分剪场景
 * 目标: 给定一组带钢需求和可用母卷,找到利用率最高的分切方案
 */
public class SlittingOptimizer {
    
    /**
     * 分剪需求
     */
    public static class SlitDemand {
        private Long demandLineId;
        private BigDecimal width;          // 需求宽度
        private BigDecimal thickness;      // 需求厚度
        private String gradeCode;          // 材质
        private BigDecimal requiredWeight; // 需求重量
        private BigDecimal remainingWeight; // 未分配重量
    }
    
    /**
     * 可用母卷
     */
    public static class SourceCoil {
        private Long stockId;
        private String coilNo;
        private BigDecimal width;          // 母卷宽度
        private BigDecimal thickness;
        private String gradeCode;
        private BigDecimal weight;
    }
    
    /**
     * 核心算法: 为一个母卷制定最优分切方案
     * 
     * 算法: 一维装箱问题 (1D Bin Packing)
     * 母卷宽度 = 容器大小
     * 各需求宽度 = 物品大小
     * 目标: 利用率最大化 (余料最小化)
     */
    public NestingPlan optimizeSlit(SourceCoil coil, List<SlitDemand> demands,
                                    BigDecimal edgeTrim, BigDecimal kerfWidth) {
        
        // 可用宽度 = 母卷宽度 - 两侧边丝
        BigDecimal usableWidth = coil.getWidth()
            .subtract(edgeTrim.multiply(new BigDecimal("2")));
        
        // 筛选: 厚度匹配 + 材质匹配 的需求
        List<SlitDemand> matchedDemands = demands.stream()
            .filter(d -> isThicknessMatch(d.getThickness(), coil.getThickness()))
            .filter(d -> d.getGradeCode().equals(coil.getGradeCode()))
            .filter(d -> d.getRemainingWeight().compareTo(BigDecimal.ZERO) > 0)
            .sorted(Comparator.comparing(SlitDemand::getWidth).reversed())
            .collect(Collectors.toList());
        
        // 贪心 + 回溯搜索: 找到利用率最高的宽度组合
        // (需求宽度组合使得 Σ(宽度×条数) + (条数-1)×锯缝 ≤ 可用宽度)
        NestingPlan bestPlan = greedyWithBacktrack(
            usableWidth, kerfWidth, matchedDemands, coil);
        
        return bestPlan;
    }
    
    /**
     * 批量套裁: 多个母卷 + 多个需求 的全局优化
     */
    public List<NestingPlan> batchOptimize(List<SourceCoil> coils, 
                                           List<SlitDemand> demands) {
        List<NestingPlan> plans = new ArrayList<>();
        
        // 按 (厚度, 材质) 分组
        Map<String, List<SlitDemand>> demandGroups = groupByThicknessAndGrade(demands);
        Map<String, List<SourceCoil>> coilGroups = groupByThicknessAndGrade(coils);
        
        for (String key : demandGroups.keySet()) {
            List<SlitDemand> groupDemands = demandGroups.get(key);
            List<SourceCoil> groupCoils = coilGroups.getOrDefault(key, new ArrayList<>());
            
            // 对每个分组，逐个母卷制定方案，直到需求全部满足
            for (SourceCoil coil : groupCoils) {
                if (allSatisfied(groupDemands)) break;
                NestingPlan plan = optimizeSlit(coil, groupDemands, ...);
                if (plan.getUtilizationPct() > MIN_UTILIZATION) {
                    plans.add(plan);
                    updateRemainingDemands(groupDemands, plan);
                }
            }
        }
        
        return plans;
    }
}
```

### 4.4 开平快速排产

```
开平特点: 速度极快，操作简单
  · 一个钢卷上线 → 开卷 → 矫平 → 定尺剪切 → N张板材下线
  · 核心操作: 选母卷 + 设定尺长度 → 自动计算可出张数
  · 排产的本质: 决定哪个钢卷在什么时间上线

前端快速排产交互:
┌─────────────────────────────────────────────────────────────────┐
│  开平快速排产                                                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  可用钢卷 (按厚度+材质筛选):                                     │
│  ┌──────┬────────┬──────┬──────┬──────┬─────────┬──────────┐  │
│  │☐    │卷号    │规格   │材质  │重量   │产地     │状态      │  │
│  ├──────┼────────┼──────┼──────┼──────┼─────────┼──────────┤  │
│  │☑    │C-001  │6.0×1500│Q235B│25.3T │唐钢    │可用      │  │
│  │☑    │C-002  │6.0×1500│Q235B│22.1T │鞍钢    │可用      │  │
│  │☐    │C-003  │8.0×1500│Q235B│28.5T │日照    │已预留    │  │
│  └──────┴────────┴──────┴──────┴──────┴─────────┴──────────┘  │
│                                                                 │
│  待排需求 (已匹配物料):                                          │
│  ┌─────────────┬──────┬──────┬──────┬────────┬───────────┐    │
│  │需求号       │规格   │材质  │数量  │交期    │客户       │    │
│  ├─────────────┼──────┼──────┼──────┼────────┼───────────┤    │
│  │DEM-001-L1  │6.0×1500×6000│Q235B│15T│03-25│XX建材    │    │
│  │DEM-003-L2  │6.0×1500×4000│Q235B│8T │03-28│YY钢构    │    │
│  └─────────────┴──────┴──────┴──────┴────────┴───────────┘    │
│                                                                 │
│  ════════ 一键排产结果 ════════                                  │
│                                                                 │
│  方案: C-001(25.3T) → 开平 6.0×1500×6000                       │
│  钢卷长度 ≈ 339m                                                │
│  可出板数 = 339/6.005 = 56 张                                   │
│  产出重量 = 56 × 0.006 × 1.5 × 6.0 × 7.85 = 23.7T             │
│  余料/短尺 = 25.3 - 23.7 = 1.6T                                │
│                                                                 │
│  [确认排产]  [调整定尺]  [查看更多方案]                           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. 原料驱动型排产（反向 MRP）

### 5.1 业务场景

```
传统MRP (需求驱动):
  需求 → 算出需要什么原料 → 采购 → 生产

原料驱动 (逆向):
  行情好 → 买了一批原料 → 这批原料能做什么成品?
                          哪些成品有需求/好卖?
                          → 安排生产

钢铁行业原因:
  · 钢材行情波动大, 低价时囤货
  · 某些特殊材质/规格供货周期长, 有货先抢
  · 企业自有资金充裕时大批量采购降低成本
  · 原料到货后需要快速安排生产消化库存
```

### 5.2 反向匹配算法

```java
/**
 * 原料驱动排产 — 反向匹配引擎
 * 给定一批原料，找出所有可生产的成品及对应需求
 */
public class ReverseMatchEngine {
    
    /**
     * 反向匹配结果
     */
    public static class MatchResult {
        private Long rawStockId;           // 原料库存ID
        private String rawCoilNo;          // 原料卷号
        private BigDecimal rawWeight;      // 原料重量
        
        // 可生产的成品列表 (按优先级排序)
        private List<ProductOption> productOptions;
    }
    
    public static class ProductOption {
        private Long productMaterialId;    // 成品物料ID
        private String productSpec;        // 成品规格
        private BigDecimal producibleQty;  // 可产出数量
        private BigDecimal yieldRate;      // 成材率
        
        // 匹配到的需求
        private List<MatchedDemand> matchedDemands;
        private BigDecimal totalDemandQty; // 总需求量
        private int demandPriority;        // 需求优先级(综合)
        
        // 无需求但市场好卖(MTS/库存备货)
        private BigDecimal marketDemandScore; // 市场需求评分
    }
    
    /**
     * 核心算法: 为一批原料找到最优生产方案
     */
    public List<MatchResult> reverseMatch(List<StockItem> rawMaterials) {
        List<MatchResult> results = new ArrayList<>();
        
        for (StockItem raw : rawMaterials) {
            MatchResult result = new MatchResult();
            result.setRawStockId(raw.getStockId());
            result.setRawWeight(raw.getOnHandQty());
            
            // Step 1: 根据原料品类，找到所有品类BOM中以此品类为子项的父品类
            List<CategoryBom> parentBoms = categoryBomMapper
                .selectByChildCategory(raw.getCategoryCode());
            
            // Step 2: 对每个父品类，反向推算可匹配的成品规格
            List<ProductOption> options = new ArrayList<>();
            
            for (CategoryBom bom : parentBoms) {
                SpecFormula formula = formulaMapper.selectByCode(bom.getCalcFormulaCode());
                
                // 反向匹配: 原料规格 → 可生产的成品规格范围
                // 例: 带钢 290×4.0 → 哪些方管/圆管的计算结果落在 290±偏差 范围内
                List<Material> matchedProducts = reverseCalc(
                    raw.getCategoryCode(), 
                    raw.getActualWidth(), raw.getActualThickness(),
                    formula, bom.getParentCategory()
                );
                
                for (Material product : matchedProducts) {
                    ProductOption option = new ProductOption();
                    option.setProductMaterialId(product.getMaterialId());
                    option.setProductSpec(product.getSpecDesc());
                    
                    // 计算可产出数量
                    BigDecimal producible = raw.getOnHandQty()
                        .multiply(bom.getYieldRate())
                        .multiply(BigDecimal.ONE.subtract(bom.getScrapRate()));
                    option.setProducibleQty(producible);
                    option.setYieldRate(bom.getYieldRate());
                    
                    // 查找该成品对应的需求(同材质)
                    List<DemandLine> demands = demandMapper
                        .selectOpenDemands(product.getMaterialId(), raw.getGradeCode());
                    option.setMatchedDemands(toMatchedDemands(demands));
                    option.setTotalDemandQty(sumQty(demands));
                    
                    // 如果有需求, 计算综合优先级
                    if (!demands.isEmpty()) {
                        option.setDemandPriority(calcPriority(demands));
                    }
                    
                    options.add(option);
                }
            }
            
            // Step 3: 按优先级排序
            // 优先级: 有MTO需求 > 有MTS需求 > 市场热销品 > 安全库存不足品
            options.sort(Comparator.comparing(ProductOption::getDemandPriority).reversed());
            
            result.setProductOptions(options);
            results.add(result);
        }
        
        return results;
    }
    
    /**
     * 反向规格推算
     * 已知: 原料宽度W_raw, 原料厚度T_raw
     * 求: 哪些成品物料的正向推算结果落在 W_raw ± 偏差 范围内
     */
    private List<Material> reverseCalc(String rawCategory,
                                        BigDecimal rawWidth, 
                                        BigDecimal rawThickness,
                                        SpecFormula formula,
                                        String productCategory) {
        // 查询该品类下所有成品物料
        List<Material> allProducts = materialMapper
            .selectByCategory(productCategory);
        
        List<Material> matched = new ArrayList<>();
        for (Material product : allProducts) {
            // 正向计算该成品需要的原料规格
            RawMaterialSpec spec = specEngine.calculate(product, BigDecimal.ONE, formula, null);
            
            // 检查原料规格是否落在计算结果的区间内
            if (rawWidth.compareTo(spec.getWidthMin()) >= 0 
                && rawWidth.compareTo(spec.getWidthMax()) <= 0
                && rawThickness.compareTo(spec.getThicknessMin()) >= 0
                && rawThickness.compareTo(spec.getThicknessMax()) <= 0) {
                matched.add(product);
            }
        }
        return matched;
    }
}
```

### 5.3 原料驱动排产 UI 设计

```
┌─────────────────────────────────────────────────────────────────────┐
│  原料驱动排产                                                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  选择原料:                                                          │
│  筛选: 品类[带钢▼] 材质[Q235B▼] 厚度[4.0▼] 状态[未排产▼]          │
│                                                                     │
│  ┌──────┬──────┬──────────┬──────┬──────┬──────┬────────┬──────┐  │
│  │☑    │卷号  │规格      │材质  │产地  │重量  │采购日期 │单价  │  │
│  ├──────┼──────┼──────────┼──────┼──────┼──────┼────────┼──────┤  │
│  │☑    │C-101│290×4.0   │Q235B│唐钢  │22T  │03-15   │4200  │  │
│  │☑    │C-102│290×4.0   │Q235B│鞍钢  │18T  │03-15   │4180  │  │
│  │☑    │C-103│240×3.0   │Q235B│唐钢  │25T  │03-15   │4150  │  │
│  └──────┴──────┴──────────┴──────┴──────┴──────┴────────┴──────┘  │
│                                                                     │
│  [🔍 智能匹配可生产成品]                                             │
│                                                                     │
│  ════════ 匹配结果 ════════                                         │
│                                                                     │
│  📦 C-101 带钢 290×4.0 Q235B (22T) 可生产:                         │
│  ┌──┬───────────────────┬──────┬─────────────────────────┬──────┐  │
│  │# │成品               │可产量│匹配需求                  │操作  │  │
│  ├──┼───────────────────┼──────┼─────────────────────────┼──────┤  │
│  │1 │方管100×50×4.0     │21.1T │DEM-001: 20T MTO 交期3-25│[排产]│  │
│  │  │                   │      │🟢 需求充足 优先级:92     │      │  │
│  ├──┼───────────────────┼──────┼─────────────────────────┼──────┤  │
│  │2 │方管100×80×4.0     │21.1T │DEM-008: 5T  MTS         │[排产]│  │
│  │  │                   │      │🟡 部分需求 优先级:65     │      │  │
│  ├──┼───────────────────┼──────┼─────────────────────────┼──────┤  │
│  │3 │圆管Φ96×4.0       │21.1T │无直接需求                │[排产]│  │
│  │  │                   │      │🔵 安全库存不足 5T        │      │  │
│  └──┴───────────────────┴──────┴─────────────────────────┴──────┘  │
│                                                                     │
│  📦 C-103 带钢 240×3.0 Q235B (25T) 可生产:                         │
│  ┌──┬───────────────────┬──────┬─────────────────────────┬──────┐  │
│  │1 │方管80×40×3.0      │24.0T │DEM-003: 30T MTO 交期3-28│[排产]│  │
│  │  │                   │      │🟢 需求充足 优先级:88     │      │  │
│  └──┴───────────────────┴──────┴─────────────────────────┴──────┘  │
│                                                                     │
│  [一键排产选中项]  [自定义排产]  [导出方案]                          │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 6. MRP 引擎适配品类 BOM 的展算逻辑

### 6.1 增强后的 MRP 展算流程

```
原 V1.0 Step 3g: BOM 展开→下层毛需求
  只有一种方式: 查离散BOM, 找子物料, 计算用量

优化后 Step 3g: BOM 智能展开
  ┌──────────────────────────────────────────────┐
  │ 判断该物料有哪种BOM:                          │
  │                                              │
  │ ├─ 有离散BOM → 使用离散BOM精确展开            │
  │ │   子料物料ID + 用量 = 确定值                │
  │ │                                            │
  │ ├─ 无离散BOM, 有品类BOM → 使用品类BOM展开     │
  │ │   1. 根据产出物料规格 + 公式 → 算出原料规格区间│
  │ │   2. 原料需求 = 品类 + 规格区间 + 材质       │
  │ │   3. 原料用量 = 公式计算                     │
  │ │   4. 生成原料层计划订单(带规格区间)           │
  │ │                                            │
  │ └─ 两者都没有 → 报异常(BOM缺失)               │
  └──────────────────────────────────────────────┘
```

### 6.2 品类 BOM 展开时的计划订单

```
传统离散BOM展开生成的计划订单:
  计划订单: 物料=带钢290×4.0(精确物料ID), 数量=21T, 类型=PUR

品类BOM展开生成的计划订单:
  计划订单: 
    原料品类 = STRIP(带钢)
    规格区间: 宽度 286~292mm, 厚度 3.85~4.15mm
    材质 = Q235B (来自需求行的材质属性)
    数量 = 21T
    类型 = PUR/STOCK_MATCH  ← 新增类型: 可从库存匹配
    
  → 原料层排产(阶段1)会将此"规格区间"需求与实际库存进行匹配
  → 精确匹配到具体的物料/批次
```

### 6.3 计划订单表增强

```sql
-- mrp_plan_order 新增字段(品类BOM展开时使用)
ALTER TABLE mrp_plan_order ADD (
    -- 品类BOM展开时，原料是品类+规格区间，而非精确物料ID
    is_category_bom     BIT           NOT NULL DEFAULT 0,
    raw_category_code   VARCHAR(20)   NULL,         -- 原料品类
    raw_width_min       DECIMAL(10,3) NULL,         -- 原料宽度下限
    raw_width_max       DECIMAL(10,3) NULL,         -- 原料宽度上限
    raw_thickness_min   DECIMAL(10,3) NULL,         -- 原料厚度下限
    raw_thickness_max   DECIMAL(10,3) NULL,         -- 原料厚度上限
    raw_grade_code      VARCHAR(30)   NULL,         -- 原料要求材质
    raw_grade_flexible  BIT           NOT NULL DEFAULT 0,  -- 材质是否可替代
    
    -- 原料匹配结果(阶段1排产后填入)
    matched_material_id BIGINT        NULL,         -- 匹配到的实际原料物料
    matched_stock_id    BIGINT        NULL,         -- 匹配到的库存批次
    matched_coil_no     VARCHAR(30)   NULL,         -- 匹配到的卷号
    match_status        VARCHAR(10)   NULL          -- UNMATCHED/MATCHED/PARTIAL
);
```

---

## 7. 完整业务流程示例

### 7.1 示例：订货合同驱动 (MTO)

```
第一步: 录入需求
  客户下单: 方管100×50×4.0×6000 Q235B — 50T, 交期 04-05

第二步: MRP 展算
  ├── 查库存: 方管100×50×4.0 Q235B 库存 8T → 可用
  ├── 净需求: 50 - 8 = 42T
  ├── 查BOM: 方管无离散BOM → 查品类BOM: RECT_PIPE → STRIP
  ├── 公式计算: 方管100×50×4.0 → 带钢宽度=289mm±偏差, 厚度=4.0mm
  ├── 原料需求: 带钢 宽286~294mm, 厚3.85~4.15mm, Q235B — 约43.7T
  ├── 查带钢库存: 找到 带钢290×4.0 Q235B 15T (C-001)
  ├── 净需求: 43.7 - 15 = 28.7T
  └── 生成:
      ├── 制造计划订单: 方管100×50×4.0 — 42T (自制)
      ├── 原料匹配: 带钢 15T 从库存 C-001
      └── 采购建议: 带钢 290×4.0 Q235B — 28.7T (或匹配宽带分剪)

第三步: 原料层排产(阶段1)
  ├── C-001 带钢290×4.0 Q235B 15T → 分配给此需求
  ├── 检查: 是否有宽带可分剪?
  │   └── 1500×4.0 Q235B 钢卷库存 30T
  │       套裁: 1500 = 290×5 + 50(余料+边丝) → 一分五
  │       一卷可产 290mm 带钢约 5条 = 约30T ÷ 5 × ... 
  │       分配 28.7T 给此需求
  └── 输出: 原料备齐

第四步: 制造层排产(阶段2)
  ├── 焊管1线: 03-21 ~ 03-23
  │   ├── 03-21 SHIFT1~3: 带钢290×4.0(C-001,15T) → 方管100×50×4.0
  │   └── 03-22 SHIFT1~2: 带钢290×4.0(分剪,28T) → 方管100×50×4.0 续
  └── 预计完工: 03-23, 满足交期 04-05 ✅

第五步: 执行、报工、入库
```

### 7.2 示例：原料驱动 (行情买料后排产)

```
第一步: 采购到货
  到货: 带钢 290×4.0 Q235B 100T (行情低买入)

第二步: 原料驱动匹配
  系统反向计算:
  ├── 290×4.0 可做方管: 100×50×4.0, 100×80×4.0, 120×60×4.0 ...
  ├── 290×4.0 可做圆管: Φ96×4.0 ...
  │
  ├── 匹配需求: 方管100×50×4.0 有 MTO 需求 50T → 优先级最高
  ├── 匹配需求: 方管100×80×4.0 有 MTS 需求 20T → 次优先
  └── 无需求但安全库存不足: 圆管Φ96×4.0 缺 5T → 低优先

第三步: 用户确认排产方案
  ├── 50T → 方管100×50×4.0 (满足 MTO 需求)
  ├── 20T → 方管100×80×4.0 (满足 MTS 需求)
  ├── 5T → 圆管Φ96×4.0 (补安全库存)
  └── 25T → 方管100×50×4.0 (备货, 市场好卖)

第四步: 直接进入制造层排产(跳过MRP，因为原料已确定)
```

### 7.3 示例：分剪排产 (一分X)

```
第一步: 汇总带钢需求
  ├── 制管需要: 290×4.0 Q235B — 43T
  ├── 制管需要: 240×3.0 Q235B — 31T
  ├── 客户直接要: 300×4.0 Q345B — 10T (直接卖带钢)
  └── 折弯需要: 200×6.0 Q235B — 5T

第二步: 检索可分剪的宽带/钢卷
  ├── 1500×4.0 Q235B — 30T (C-201)
  ├── 1500×4.0 Q345B — 25T (C-202)
  ├── 1250×3.0 Q235B — 20T (C-203)
  └── 1500×6.0 Q235B — 28T (C-204)

第三步: 套裁方案
  C-201 (1500×4.0 Q235B):
    方案: 290×5 = 1450, 余+边丝 50mm
    产出: 290×4.0 带钢 约 30T (5条同规格)
    → 分配给制管290×4.0需求
    利用率: 96.7% ✅

  C-202 (1500×4.0 Q345B):
    方案: 300×5 = 1500, 边丝 0 (刚好!)
    产出: 300×4.0 带钢 约 25T (5条)
    → 分配10T给客户直接要的300×4.0 Q345B
    → 剩余15T入库备用
    利用率: 100% ✅

  C-203 (1250×3.0 Q235B):
    方案: 240×5 = 1200, 余 50mm
    产出: 240×3.0 带钢 约 20T (5条)
    → 分配给制管240×3.0需求
    利用率: 96.0% ✅

  C-204 (1500×6.0 Q235B):
    方案: 200×7 = 1400, 余+边丝 100mm
    产出: 200×6.0 带钢 约 28T (7条)
    → 分配5T给折弯需求, 余23T入库
    利用率: 93.3%

第四步: 排产到分剪产线
  分剪线 03-20:
    08:00 C-201 → 290×5 (约3小时)
    11:00 C-202 → 300×5 (约2.5小时)
    14:00 C-203 → 240×5 (约2小时)
    16:00 C-204 → 200×7 (约3小时)
```

---

## 8. 品类编码体系参考

```
品类编码 (category_code) 标准化:

钢材原料类:
  COIL        热轧钢卷 (母卷)
  COLD_COIL   冷轧钢卷
  STRIP       带钢 (窄带/纵切后)
  PLATE       板材 (开平后的定尺板)
  BILLET      钢坯/方坯
  ROUND_BAR   圆钢

管材类:
  ROUND_PIPE  圆管 (焊管/无缝管)
  RECT_PIPE   方管/矩管
  OVAL_PIPE   椭圆管
  PROFILE_PIPE 异型管

型材类:
  H_STEEL     H型钢
  I_STEEL     工字钢
  CHANNEL     槽钢
  ANGLE       角钢
  C_STEEL     C型钢
  Z_STEEL     Z型钢

加工件类:
  BEND_PART   折弯件
  CUT_PART    剪切件
  WELD_PART   焊接件

总成类:
  BEAM_FRAME  车架总成
  BEAM        车梁
  ASSEMBLY    装配件

其他:
  PACK        包装材料
  AUX         辅料
  OTHER       其他
```
