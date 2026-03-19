# AiAPS — 冷板与镀锌板生产工艺排产设计

> 版本：1.0 | 最后更新：2026-03-17
>
> 覆盖冷板和镀锌板的完整工艺链路：酸洗→冷轧→罩退(炉台+层位)→平整→
> 镀锌(连续线)→后处理。重点解决罩退工序的炉台/层位管理。

---

## 0. 冷板与镀锌板工艺链路

```
原料                                    冷板成品                镀锌板成品
─────                                  ──────                 ──────

热轧卷 (酸洗原料)
  │
  ├── [酸洗] 酸洗线 → 去除氧化铁皮
  │     产出: 酸洗卷
  │
  ├── [冷轧] 冷轧机组 → 压下减薄到目标厚度
  │     产出: 冷硬卷(硬态, 不能直接使用)
  │
  ├── [罩退] 罩式退火炉 → 去应力, 恢复塑性           ← 本次设计重点
  │     ★ 上料必须明确: 哪个炉、第几层
  │     ★ 退火周期长: 3~5天/炉次
  │     ★ 一炉多卷: 每炉4~6层, 每层1~2卷
  │     产出: 退火卷(软态)
  │
  ├── [平整] 平整机组 → 改善板形, 调整表面粗糙度
  │     产出: 冷板成品卷 ────────────→ 冷板成品(出厂)
  │
  └── [镀锌] 连续热镀锌线 → 锌层覆盖             (从冷硬卷或退火卷进)
        │  产出类型: 有花/无花/镜面
        │
        ├── [钝化/涂油] → 后处理
        │
        └── 镀锌板成品 ──────────────→ 镀锌板成品(出厂)

两条工艺路线:
  冷板: 酸洗 → 冷轧 → 罩退 → 平整 → 冷板成品
  镀锌: 酸洗 → 冷轧 → (罩退) → 镀锌线 → 后处理 → 镀锌板成品
                        可选: 有些镀锌线自带退火段, 不需要单独罩退
```

---

## 1. 罩退工序设计（炉台+层位管理）

### 1.1 罩退的业务特点

```
罩退(罩式退火)的独特性:

  1. 批处理工序(非连续流)
     · 一炉一次装入多卷(4~6层)
     · 整炉一起加热→保温→冷却 → 整炉一起出炉
     · 退火周期: 装炉2h + 加热12h + 保温24h + 冷却48h + 出炉2h ≈ 3~4天

  2. 空间约束: 炉台+层位
     · 一个车间有多个炉台(如: 8台罩式炉)
     · 每台炉有固定层数(如: 5层)
     · 每层放1~2卷(取决于卷径和卷重)
     · 上料时必须指定: 哪台炉 + 第几层

  3. 退火制度约束
     · 不同材质/厚度需要不同的退火温度和时间
     · 同一炉内最好是相同/相近退火制度的卷
     · 不同制度的卷混装会影响质量

  4. 产能计算方式
     · 不按"吨/小时"算, 而按"炉次×每炉装入量÷退火周期"
     · 如: 每炉装80T, 周期4天 → 日产能 = 80/4 = 20T/天/炉
     · 8台炉 → 总日产能 = 160T/天
```

### 1.2 炉台主数据 (bas_furnace)

```sql
CREATE TABLE bas_furnace (
    furnace_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    furnace_code        VARCHAR(30)   NOT NULL,     -- 炉台编号: F-01, F-02 ...
    furnace_name        NVARCHAR(60)  NOT NULL,     -- 炉台名称
    wc_id               BIGINT        NOT NULL,     -- 所属工作中心(罩退车间)
    
    -- ═══ 物理参数 ═══
    total_layers        INT           NOT NULL,     -- 总层数: 4/5/6
    max_weight_per_layer DECIMAL(18,3) NOT NULL,    -- 每层最大装入重量(T)
    max_total_weight    DECIMAL(18,3) NOT NULL,     -- 整炉最大装入重量(T)
    max_coil_diameter   DECIMAL(10,3) NULL,         -- 最大卷径(mm)
    inner_diameter      DECIMAL(10,3) NULL,         -- 内罩内径(mm)
    inner_height        DECIMAL(10,3) NULL,         -- 内罩高度(mm)
    
    -- ═══ 标准退火参数 ═══
    std_cycle_hours     INT           NOT NULL DEFAULT 96,  -- 标准退火周期(小时)
    std_heat_hours      INT           NULL,         -- 加热时间(小时)
    std_hold_hours      INT           NULL,         -- 保温时间(小时)
    std_cool_hours      INT           NULL,         -- 冷却时间(小时)
    load_hours          INT           NOT NULL DEFAULT 2,  -- 装炉时间(小时)
    unload_hours        INT           NOT NULL DEFAULT 2,  -- 出炉时间(小时)
    
    -- ═══ 状态 ═══
    furnace_status      VARCHAR(10)   NOT NULL DEFAULT 'IDLE',
                                                    -- IDLE=空闲 LOADING=装炉中
                                                    -- ANNEALING=退火中 COOLING=冷却中
                                                    -- UNLOADING=出炉中 MAINTAIN=维护
    current_schedule_id BIGINT        NULL,         -- 当前使用的排产单
    current_start_time  DATETIME      NULL,         -- 当前炉次开始时间
    expected_end_time   DATETIME      NULL,         -- 预计出炉时间
    
    is_active           BIT           NOT NULL DEFAULT 1,
    CONSTRAINT UK_furnace_code UNIQUE (furnace_code),
    CONSTRAINT FK_furnace_wc FOREIGN KEY (wc_id) 
        REFERENCES bas_work_center(wc_id)
);
```

### 1.3 退火制度 (bas_anneal_recipe)

```sql
-- 退火制度: 不同材质/厚度对应不同的温度和时间曲线
CREATE TABLE bas_anneal_recipe (
    recipe_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    recipe_code         VARCHAR(30)   NOT NULL,
    recipe_name         NVARCHAR(100) NOT NULL,
    
    -- ═══ 适用范围 ═══
    applicable_grade    VARCHAR(30)   NULL,         -- 适用材质(NULL=通用)
    thickness_min       DECIMAL(10,3) NULL,         -- 适用厚度下限
    thickness_max       DECIMAL(10,3) NULL,         -- 适用厚度上限
    
    -- ═══ 退火参数 ═══
    target_temp         INT           NOT NULL,     -- 目标温度(℃): 680/710/...
    heat_rate           INT           NULL,         -- 升温速率(℃/h)
    hold_temp           INT           NOT NULL,     -- 保温温度(℃)
    hold_hours          INT           NOT NULL,     -- 保温时间(小时)
    cool_method         VARCHAR(20)   NULL,         -- 冷却方式: FURNACE=炉冷 AIR=空冷 WATER=水冷
    total_cycle_hours   INT           NOT NULL,     -- 总周期(小时)
    
    -- ═══ 混装兼容组 ═══
    compat_group        VARCHAR(20)   NULL,         -- 兼容组编码(同组可混装)
    
    is_active           BIT           NOT NULL DEFAULT 1,
    CONSTRAINT UK_recipe_code UNIQUE (recipe_code)
);
```

### 1.4 炉次排产与层位分配 (aps_furnace_charge)

```sql
-- 炉次计划: 一次装炉的整体计划
CREATE TABLE aps_furnace_charge (
    charge_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    charge_no           VARCHAR(30)   NOT NULL,     -- 炉次编号
    furnace_id          BIGINT        NOT NULL,     -- 炉台
    schedule_id         BIGINT        NULL,         -- 关联排产单
    recipe_id           BIGINT        NOT NULL,     -- 退火制度
    
    -- ═══ 时间计划 ═══
    plan_load_start     DATETIME      NOT NULL,     -- 计划装炉开始
    plan_load_end       DATETIME      NULL,         -- 计划装炉完成
    plan_anneal_start   DATETIME      NULL,         -- 计划退火开始
    plan_anneal_end     DATETIME      NULL,         -- 计划退火完成(保温结束)
    plan_cool_end       DATETIME      NULL,         -- 计划冷却完成
    plan_unload_start   DATETIME      NULL,         -- 计划出炉开始
    plan_unload_end     DATETIME      NULL,         -- 计划出炉完成
    
    -- 实际时间
    actual_load_start   DATETIME      NULL,
    actual_anneal_start DATETIME      NULL,
    actual_anneal_end   DATETIME      NULL,
    actual_cool_end     DATETIME      NULL,
    actual_unload_end   DATETIME      NULL,
    
    -- ═══ 装炉汇总 ═══
    total_coils         INT           NOT NULL DEFAULT 0,  -- 总装入卷数
    total_weight        DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 总装入重量(T)
    layers_used         INT           NOT NULL DEFAULT 0,  -- 使用层数
    
    -- ═══ 退火参数(实际) ═══
    actual_temp         INT           NULL,         -- 实际退火温度
    actual_hold_hours   INT           NULL,         -- 实际保温时间
    
    -- ═══ 状态 ═══
    charge_status       VARCHAR(10)   NOT NULL DEFAULT 'PLANNED',
                                                    -- PLANNED/LOADING/ANNEALING/COOLING
                                                    -- /UNLOADING/COMPLETED/CANCELLED
    
    created_by          VARCHAR(50)   NULL,
    created_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    CONSTRAINT UK_charge_no UNIQUE (charge_no),
    CONSTRAINT FK_charge_furnace FOREIGN KEY (furnace_id)
        REFERENCES bas_furnace(furnace_id)
);

CREATE INDEX IX_charge_furnace ON aps_furnace_charge(furnace_id);
CREATE INDEX IX_charge_status ON aps_furnace_charge(charge_status);

-- 层位明细: 每层放什么卷
CREATE TABLE aps_furnace_charge_layer (
    layer_id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    charge_id           BIGINT        NOT NULL,     -- 所属炉次
    layer_no            INT           NOT NULL,     -- 层号: 1(底层)~N(顶层)
    
    -- ═══ 装入的卷 ═══
    stock_id            BIGINT        NOT NULL,     -- 库存批次
    CardNo              VARCHAR(60)   NULL,         -- 卡号
    ResNo               VARCHAR(30)   NULL,         -- 资源号(卷号)
    PrdtID              BIGINT        NOT NULL,     -- 物料
    PATName             VARCHAR(30)   NULL,         -- 材质
    PAName              VARCHAR(30)   NULL,         -- 产地
    coil_weight         DECIMAL(18,3) NOT NULL,     -- 卷重(T)
    coil_outer_dia      DECIMAL(10,3) NULL,         -- 卷外径(mm)
    thickness           DECIMAL(10,3) NULL,         -- 厚度(mm)
    width               DECIMAL(10,3) NULL,         -- 宽度(mm)
    
    -- ═══ 合同 ═══
    contract_no         VARCHAR(60)   NULL,
    
    -- ═══ 关联上游排产 ═══
    source_schedule_id  BIGINT        NULL,         -- 来源排产(冷轧产出)
    
    -- ═══ 关联下游排产 ═══
    next_schedule_id    BIGINT        NULL,         -- 后续排产(平整/镀锌)
    
    -- ═══ 质量 ═══
    anneal_result       VARCHAR(10)   NULL,         -- 退火结果: GOOD/NG/PENDING
    qc_remark           NVARCHAR(200) NULL,
    
    -- ═══ 备注 ═══
    CardRemark          NVARCHAR(500) NULL,
    CardRemark2         NVARCHAR(500) NULL,
    
    CONSTRAINT FK_layer_charge FOREIGN KEY (charge_id)
        REFERENCES aps_furnace_charge(charge_id),
    CONSTRAINT UK_charge_layer UNIQUE (charge_id, layer_no)
);

CREATE INDEX IX_layer_charge ON aps_furnace_charge_layer(charge_id);
CREATE INDEX IX_layer_res ON aps_furnace_charge_layer(ResNo);
CREATE INDEX IX_layer_contract ON aps_furnace_charge_layer(contract_no);
```

### 1.5 罩退排产UI

```
┌──────────────────────────────────────────────────────────────────────────┐
│  罩退排产 — 炉台总览                                                     │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─── 炉台状态 ──────────────────────────────────────────────────────┐ │
│  │                                                                    │ │
│  │  F-01 [🔴退火中]    F-02 [🔴退火中]    F-03 [🟡冷却中]           │ │
│  │  装入: 5卷/78T      装入: 4卷/65T      装入: 5卷/80T             │ │
│  │  制度: R-01(680℃)  制度: R-02(710℃)  制度: R-01(680℃)          │ │
│  │  预计出炉: 03-25    预计出炉: 03-26    预计出炉: 03-24 14:00      │ │
│  │                                                                    │ │
│  │  F-04 [🟢空闲]      F-05 [🟢空闲]      F-06 [🔵装炉中]           │ │
│  │  上次出炉: 03-22    上次出炉: 03-21    装入进度: 3/5层             │ │
│  │  可用                可用               预计装完: 03-22 16:00      │ │
│  │                                                                    │ │
│  │  F-07 [🔴退火中]    F-08 [⚙维护中]                                │ │
│  │  装入: 6卷/90T      预计恢复: 03-25                                │ │
│  │  预计出炉: 03-27                                                   │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  ┌─── 装炉操作 (F-06) ──────────────────────────────────────────────┐  │
│  │                                                                    │  │
│  │  炉次: CHG-2026-0055 | 退火制度: R-01 (680℃ 保温24h)            │  │
│  │  兼容材质: SPCC / DC01 / Q195 (同兼容组G-SOFT)                    │  │
│  │                                                                    │  │
│  │  层位分配:                                                         │  │
│  │  ┌────┬──────────┬──────────┬──────┬──────┬──────┬──────┬──────┐ │  │
│  │  │层号│资源号    │规格      │材质  │产地  │卷重  │合同  │状态  │ │  │
│  │  ├────┼──────────┼──────────┼──────┼──────┼──────┼──────┼──────┤ │  │
│  │  │ 1  │CRC-0088 │0.5×1250  │SPCC │鞍钢  │18.5T │HT-120│✅已装│ │  │
│  │  │ 2  │CRC-0092 │0.8×1000  │SPCC │唐钢  │15.0T │HT-125│✅已装│ │  │
│  │  │ 3  │CRC-0095 │0.5×1250  │DC01 │鞍钢  │17.2T │HT-120│✅已装│ │  │
│  │  │ 4  │(待分配) │         │      │      │      │      │⬜空  │ │  │
│  │  │ 5  │(待分配) │         │      │      │      │      │⬜空  │ │  │
│  │  └────┴──────────┴──────────┴──────┴──────┴──────┴──────┴──────┘ │  │
│  │                                                                    │  │
│  │  已装: 3卷 / 50.7T     剩余容量: 2层 / 约29.3T                     │  │
│  │                                                                    │  │
│  │  可装入的待退火卷:                                                  │  │
│  │  ┌──────────┬──────────┬──────┬──────┬──────┬──────┬──────┐      │  │
│  │  │资源号    │规格      │材质  │产地  │卷重  │合同  │操作  │      │  │
│  │  ├──────────┼──────────┼──────┼──────┼──────┼──────┼──────┤      │  │
│  │  │CRC-0098 │0.5×1250  │SPCC │首钢  │16.0T │HT-128│[装入]│      │  │
│  │  │CRC-0100 │1.0×1000  │DC01 │鞍钢  │12.5T │HT-130│[装入]│      │  │
│  │  │CRC-0103 │0.8×1250  │SPCC │唐钢  │14.8T │HT-125│[装入]│      │  │
│  │  └──────────┴──────────┴──────┴──────┴──────┴──────┴──────┘      │  │
│  │                                                                    │  │
│  │  [装入第4层] [装入第5层] [完成装炉→开始退火]                        │  │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### 1.6 罩退甘特图

```
罩退车间甘特图 — 以炉台为行(非产线):

      03-20    03-21    03-22    03-23    03-24    03-25    03-26
F-01 ███████████████████████████████████████████████████████████████
     CHG-0050  装炉→  退火中(680℃)        →  冷却中  →  出炉
     5卷/78T SPCC  合同: HT-118/HT-120

F-02      ██████████████████████████████████████████████████████████
          CHG-0051  装→ 退火中(710℃)           → 冷却  → 出炉
          4卷/65T DC01  合同: HT-122

F-03 ████████████████████████████████████░░░░░░░ ██████████████████
     CHG-0049 退火中→冷却→出炉              CHG-0054 装→退火
     5卷/80T                                 新一炉

F-04 ░░░░░░░░░░░░░░░ ████████████████████████████████████████████
     (空闲)           CHG-0052  装→ 退火中(680℃)  → 冷却
                      6卷/90T SPCC  合同: HT-125/HT-128

F-05 ░░░░░░░░░░░░░░░░░░░░░░░░░░░ ████████████████████████████████
     (空闲)                       CHG-0053  装→退火
                                  规划中

颜色: ██装炉(蓝)  ██退火中(红)  ██冷却中(橙)  ██出炉(绿)  ░空闲(灰)
```

---

## 2. 镀锌工序设计

### 2.1 镀锌线的特点

```
连续热镀锌线(CGL)特点:
  · 连续流工序(与罩退的批处理相反)
  · 原料: 冷硬卷 或 退火卷 → 开卷→焊接→退火段→锌锅→冷却→光整→卷取
  · 镀锌种类:
    ├── 有花(Regular Spangle): 锌花可见, 普通结构用
    ├── 无花(Minimized Spangle): 锌花极小, 家电用
    ├── 零花/镜面(Zero Spangle): 无锌花, 高端用
    └── 锌铝镁(ZAM): 特殊合金镀层
  · 不同镀锌种类影响: 锌锅温度/线速度/气刀参数/冷却方式
  · 连续换卷: 一卷用完焊接下一卷, 不停机

排产关键:
  · 同类型镀锌尽量连排(减少参数切换)
  · 厚度从薄到厚排(或从厚到薄, 减少辊缝调整)
  · 宽度从窄到宽排(宽卷后边部质量好)
```

### 2.2 镀锌线参数 (bas_galv_line_config)

```sql
CREATE TABLE bas_galv_line_config (
    config_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    wc_id               BIGINT        NOT NULL,     -- 镀锌线(工作中心)
    
    -- ═══ 产能参数 ═══
    max_speed_mpm       DECIMAL(10,2) NOT NULL,     -- 最大线速(m/min)
    max_width           DECIMAL(10,3) NOT NULL,     -- 最大带宽(mm)
    min_width           DECIMAL(10,3) NOT NULL,     -- 最小带宽
    max_thickness       DECIMAL(10,3) NOT NULL,     -- 最大厚度
    min_thickness       DECIMAL(10,3) NOT NULL,     -- 最小厚度
    
    -- ═══ 锌锅参数 ═══
    pot_capacity_ton    DECIMAL(18,3) NULL,         -- 锌锅容量(T锌液)
    zinc_type           VARCHAR(30)   NULL,         -- 支持的锌种: GI/GA/ZAM/GL
    
    -- ═══ 换产参数 ═══
    spangle_change_min  INT           NOT NULL DEFAULT 60,   -- 花型切换时间(min)
    thickness_change_min INT          NOT NULL DEFAULT 15,   -- 厚度切换时间(min)
    width_change_min    INT           NOT NULL DEFAULT 10,   -- 宽度切换时间(min)
    
    CONSTRAINT FK_galv_wc FOREIGN KEY (wc_id) 
        REFERENCES bas_work_center(wc_id)
);
```

### 2.3 镀锌排产特有属性

```sql
-- aps_schedule 扩展: 镀锌工序专有字段
ALTER TABLE aps_schedule ADD (
    -- ═══ 镀锌参数(镀锌工序时使用) ═══
    zinc_coat_type      VARCHAR(20)   NULL,         -- 镀锌花型: REGULAR/MINI/ZERO
    zinc_weight_g       DECIMAL(10,2) NULL,         -- 镀锌量(g/㎡): 80/120/180/275
    zinc_coat_desc      NVARCHAR(50)  NULL,         -- 镀层描述: "双面等厚80g"
    line_speed_mpm      DECIMAL(10,2) NULL,         -- 计划线速(m/min)
    
    -- ═══ 罩退参数(罩退工序时使用) ═══
    furnace_id          BIGINT        NULL,         -- 使用的炉台
    charge_id           BIGINT        NULL,         -- 炉次号
    furnace_layer       INT           NULL,         -- 层位号
    anneal_recipe_id    BIGINT        NULL          -- 退火制度
);
```

---

## 3. 平整工序设计

```sql
-- 平整线参数
CREATE TABLE bas_temper_mill_config (
    config_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    wc_id               BIGINT        NOT NULL,
    
    max_speed_mpm       DECIMAL(10,2) NOT NULL,     -- 最大轧制速度
    max_width           DECIMAL(10,3) NOT NULL,
    min_width           DECIMAL(10,3) NOT NULL,
    max_thickness       DECIMAL(10,3) NOT NULL,
    min_thickness       DECIMAL(10,3) NOT NULL,
    
    -- 平整参数影响产品性能
    elongation_range    NVARCHAR(50)  NULL,         -- 延伸率范围: "0.5~2.0%"
    roughness_range     NVARCHAR(50)  NULL,         -- 粗糙度范围: "0.8~1.6μm"
    
    roll_change_min     INT           NOT NULL DEFAULT 120, -- 换辊时间(min)
    
    CONSTRAINT FK_temper_wc FOREIGN KEY (wc_id) 
        REFERENCES bas_work_center(wc_id)
);
```

---

## 4. 冷板/镀锌板全工艺链路排产模型

### 4.1 排产串联关系

```
冷板排产链(5道工序串联):

  ┌────────┐    ┌────────┐    ┌────────────┐    ┌────────┐    ┌────────┐
  │ 酸洗    │───→│ 冷轧    │───→│ 罩退        │───→│ 平整    │───→│ 成品    │
  │ SCH-A  │    │ SCH-B  │    │ SCH-C       │    │ SCH-D  │    │ 入库   │
  │ 连续线  │    │ 冷轧机组│    │ 炉台F-04    │    │ 平整机组│    │        │
  │ 2h     │    │ 4h     │    │ 层3         │    │ 3h     │    │        │
  │        │    │        │    │ 退火96h      │    │        │    │        │
  │        │    │        │    │ (瓶颈工序!)  │    │        │    │        │
  └────────┘    └────────┘    └────────────┘    └────────┘    └────────┘

  排产关键:
  · 罩退是瓶颈(96h vs 其他工序2~4h)
  · 排产方向: 以罩退为锚点 → 向前推酸洗冷轧(倒排) → 向后推平整(正排)
  · 罩退决定了整条链路的节拍

镀锌板排产链:

  ┌────────┐    ┌────────┐    ┌────────────┐    ┌────────┐    ┌────────┐
  │ 酸洗    │───→│ 冷轧    │───→│ 罩退(可选)  │───→│ 镀锌线  │───→│ 成品    │
  │ SCH-A  │    │ SCH-B  │    │ SCH-C       │    │ SCH-E  │    │ 入库   │
  │        │    │        │    │ 部分镀锌线   │    │ 连续线  │    │        │
  │        │    │        │    │ 自带退火段   │    │ 花型:   │    │        │
  │        │    │        │    │ → 可跳过    │    │ 无花    │    │        │
  └────────┘    └────────┘    └────────────┘    └────────┘    └────────┘
```

### 4.2 罩退排产逻辑

```
罩退排产的特殊逻辑:

  Step 1: 收集待退火的冷硬卷(来自冷轧产出)
    按(退火制度兼容组)分组:
    ├── 组A (680℃制度): CRC-0088, CRC-0092, CRC-0095, CRC-0098 ...
    ├── 组B (710℃制度): CRC-0100, CRC-0103 ...
    └── 组C (特殊制度): CRC-0110 ...

  Step 2: 按组分配炉台
    组A需要4卷 → 分配F-04(5层)
    组B需要2卷 → 和组A混装(如果兼容) or 分配F-05

  Step 3: 层位分配
    优先规则:
    ├── 重卷放底层(承重)
    ├── 轻卷放顶层
    ├── 同合同的卷尽量放同一炉(方便追溯和出炉后流转)
    └── 考虑卷径: 大卷占位大, 小卷可能每层放2卷

  Step 4: 排炉次时间
    F-04 当前空闲 → 计划:
      装炉: 03-22 14:00 ~ 03-22 16:00
      退火: 03-22 16:00 ~ 03-24 16:00 (48h)
      冷却: 03-24 16:00 ~ 03-26 16:00 (48h)
      出炉: 03-26 16:00 ~ 03-26 18:00
    
  Step 5: 出炉后关联后续排产(平整/镀锌)
    CRC-0088 出炉 → 排入平整线 03-27 08:00
    CRC-0092 出炉 → 排入镀锌线 03-27 10:00
```

---

## 5. 追溯与上料增强

### 5.1 罩退上料追溯

```
trc_trace_link 新增罩退环节:

  ┌───────────────────────────────────────────────────────────────┐
  │ trace_id: 10                                                  │
  │ source: STOCK, CardNo=CD-CRC-0088, ResNo=CRC-0088            │
  │         PrdtID=冷硬卷0.5×1250, PATName=SPCC, weight=18.5T    │
  │ process: ANNEAL (罩退)                                        │
  │         schedule=SCH-ANNEAL-055                               │
  │         furnace=F-04, layer=1, charge=CHG-0055                │
  │         recipe=R-01(680℃/24h)                                │
  │ target: STOCK, CardNo=CD-ANN-0088, ResNo=ANN-CRC-0088        │
  │         PrdtID=退火卷0.5×1250, weight=18.5T                  │
  │ contract: HT-2026-0120                                        │
  └───────────────────────────────────────────────────────────────┘

追溯查询时可以查到:
  "这卷退火卷是在F-04炉第1层退火的, 
   退火制度R-01(680℃保温24h), 同炉还有哪几卷"
```

### 5.2 上料操作增强(罩退专用)

```
┌──────────────────────────────────────────────────────────────────────────┐
│  罩退上料                                                   [F-06炉台] │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  炉次: CHG-2026-0055 | 退火制度: R-01(680℃/24h) | 兼容: SPCC/DC01     │
│                                                                          │
│  装入第 4 层:                                                            │
│  扫码: [ CRC-0098                    ] [🔍扫码]                         │
│                                                                          │
│  ┌─── 物料信息 ──────────────────────────────────────────────────────┐ │
│  │  资源号: CRC-0098 | 卡号: CD-CRC-0098                             │ │
│  │  物料: 冷硬卷 0.5×1250 | 材质: SPCC | 产地: 首钢 | 重量: 16.0T    │ │
│  │  合同: HT-2026-0128                                               │ │
│  │  卷径: 1200mm                                                      │ │
│  │                                                                    │ │
│  │  ✅ 退火制度兼容: SPCC 属于 G-SOFT 兼容组, 与本炉次匹配           │ │
│  │  ✅ 层位可用: 第4层剩余容量 20T, 卷重16T 可装入                    │ │
│  │  ✅ 卷径检查: 1200mm < 内罩内径1500mm, 通过                        │ │
│  │  ✅ 合同检查: HT-0128, 本炉已有HT-0120/HT-0125(不同合同同炉,      │ │
│  │              窜料策略=罩退允许不同合同同炉)                         │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  [确认装入第4层]  [换一卷]                                               │
│                                                                          │
│  当前炉内状态:                                                           │
│  ┌─────────────────────────────────────┐                                │
│  │  ╔═══════════════╗ ← 内罩           │                                │
│  │  ║ 第5层: (空)    ║                  │                                │
│  │  ║───────────────║                  │                                │
│  │  ║ 第4层: ← 装入中║ CRC-0098 16.0T  │                                │
│  │  ║───────────────║                  │                                │
│  │  ║ 第3层: CRC-0095║ 17.2T DC01 鞍钢  │                                │
│  │  ║───────────────║                  │                                │
│  │  ║ 第2层: CRC-0092║ 15.0T SPCC 唐钢  │                                │
│  │  ║───────────────║                  │                                │
│  │  ║ 第1层: CRC-0088║ 18.5T SPCC 鞍钢  │                                │
│  │  ╚═══════════════╝ ← 底座           │                                │
│  └─────────────────────────────────────┘                                │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 6. API 接口补充

```
# ═══ 炉台管理 ═══
GET    /api/v1/furnace                           # 炉台列表(含状态)
GET    /api/v1/furnace/{id}                      # 炉台详情
GET    /api/v1/furnace/{id}/status                # 炉台实时状态
PUT    /api/v1/furnace/{id}/status                # 更新炉台状态

# ═══ 炉次排产 ═══
POST   /api/v1/furnace-charge                     # 创建炉次计划
GET    /api/v1/furnace-charge/{id}                # 炉次详情(含层位)
POST   /api/v1/furnace-charge/{id}/load-layer     # 装入某层(扫码上料)
POST   /api/v1/furnace-charge/{id}/start-anneal   # 开始退火
POST   /api/v1/furnace-charge/{id}/complete        # 出炉完成
GET    /api/v1/furnace-charge/gantt                # 罩退甘特图数据

# ═══ 退火制度 ═══
GET    /api/v1/anneal-recipe                       # 退火制度列表
GET    /api/v1/anneal-recipe/compatible/{gradeCode} # 查兼容制度

# ═══ 镀锌线 ═══
GET    /api/v1/galv-line/{wcId}/config              # 镀锌线配置
POST   /api/v1/galv-line/{wcId}/schedule            # 镀锌线排产
```
