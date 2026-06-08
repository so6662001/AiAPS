# AiAPS — 数据模型设计（SQL Server 2008 兼容）

> **版本：3.0（最终版）** | 最后更新：2026-03-17
>
> 所有 DDL 严格兼容 SQL Server 2008，不使用 SEQUENCE、STRING_AGG、JSON 等高版本特性。
>
> **V3.0 重大修正：** 本版本将 08/09/13 等文档中通过 ALTER TABLE 散落添加的字段
> 全部合并到原始表定义中，确保所有核心业务表都包含 **材质(grade_code)、产地(origin_code)、
> 重量(weight)** 字段。钢铁行业以重量为核心计量单位，材质+产地是物料的基本属性维度，
> 必须贯穿 需求→库存→MRP计划→排产 全链路。
>
> 增量表（品类BOM、套裁方案、模具、替代料、二次加工等）仍在各专项文档中定义。

---

## 1. 实体关系总览 (ER 概要)

```
┌──────────────┐      ┌──────────────┐      ┌──────────────────┐
│  bas_material │◄────│  bas_bom_head │─────►│  bas_bom_detail  │
│  (物料主数据)  │      │  (BOM 表头)   │      │  (BOM 明细行)    │
└──────┬───────┘      └──────────────┘      └──────────────────┘
       │
       │  1:N
       ▼
┌──────────────────┐    ┌──────────────────┐   ┌────────────────┐
│  bas_routing_head │───►│ bas_routing_oper │──►│ bas_work_center │
│  (工艺路线表头)    │    │ (工序明细)       │   │ (工作中心)      │
└──────────────────┘    └──────────────────┘   └────────────────┘
                                                       │
                                               ┌───────┴────────┐
                                               │bas_wc_capacity  │
                                               │(工作中心产能日历)│
                                               └────────────────┘

┌──────────────────┐    ┌──────────────────┐
│  dem_demand_head  │───►│ dem_demand_line  │
│  (需求单表头)     │    │ (需求单明细行)    │
└──────────────────┘    └──────────────────┘

┌──────────────────┐    ┌──────────────────┐
│  inv_stock        │    │ inv_stock_batch  │
│  (即时库存)       │    │ (批次库存)       │
└──────────────────┘    └──────────────────┘

┌──────────────────┐    ┌──────────────────┐
│  mrp_plan_order   │    │ mrp_run_log      │
│  (MRP 计划订单)   │    │ (MRP 运行日志)   │
└──────────────────┘    └──────────────────┘

┌──────────────────┐    ┌──────────────────┐
│  aps_schedule     │    │aps_schedule_oper │
│  (排产主计划)     │    │(排产工序计划)     │
└──────────────────┘    └──────────────────┘
```

---

## 2. 基础数据表

### 2.1 物料主数据 (bas_material)

```sql
CREATE TABLE bas_material (
    material_id         BIGINT IDENTITY(1,1) PRIMARY KEY,
    material_code       VARCHAR(60)   NOT NULL,     -- 物料编码
    material_name       NVARCHAR(200) NOT NULL,     -- 物料名称
    material_spec       NVARCHAR(200) NULL,         -- 规格型号
    material_type       VARCHAR(20)   NOT NULL,     -- 类型: RAW/SEMI/FG/PACK
                                                    -- RAW=原材料 SEMI=半成品 FG=成品 PACK=包装材料
    material_group      VARCHAR(20)   NULL,         -- 物料分组: PIPE/PROFILE/PLATE/BEAM/OTHER
    unit_code           VARCHAR(10)   NOT NULL,     -- 基本计量单位: T/KG/M/PCS
    aux_unit_code       VARCHAR(10)   NULL,         -- 辅助计量单位
    unit_convert_rate   DECIMAL(18,6) NULL,         -- 辅助→基本换算率
    steel_grade         VARCHAR(30)   NULL,         -- 材质/钢号: Q235B, Q345B, 20#...
    thickness           DECIMAL(10,3) NULL,         -- 厚度 mm
    width               DECIMAL(10,3) NULL,         -- 宽度 mm
    length              DECIMAL(10,3) NULL,         -- 长度 mm
    outer_diameter      DECIMAL(10,3) NULL,         -- 外径 mm (管材用)
    wall_thickness      DECIMAL(10,3) NULL,         -- 壁厚 mm (管材用)
    surface_treatment   VARCHAR(20)   NULL,         -- 表面处理: HG/DX/PH/RM
    lot_policy          VARCHAR(10)   NOT NULL DEFAULT 'LFL',
                                                    -- 批量策略: LFL=按需 FOQ=固定批量 POQ=固定期间 EOQ=经济批量
    fixed_lot_qty       DECIMAL(18,3) NULL,         -- 固定批量数量 (FOQ 时使用)
    min_order_qty       DECIMAL(18,3) NULL,         -- 最小起订量
    lot_multiple        DECIMAL(18,3) NULL,         -- 批量增量 (必须是此值的整数倍)
    lead_time_days      INT           NOT NULL DEFAULT 0,  -- 提前期(天)
    safety_stock_qty    DECIMAL(18,3) NULL,         -- 安全库存
    safety_lead_days    INT           NULL,         -- 安全提前期(天)
    procurement_type    VARCHAR(10)   NOT NULL DEFAULT 'M',
                                                    -- 获取方式: M=自制 P=采购 O=委外
    default_supplier_id BIGINT        NULL,         -- 默认供应商ID
    is_phantom          BIT           NOT NULL DEFAULT 0,   -- 是否虚拟件(不实际入库)
    is_active           BIT           NOT NULL DEFAULT 1,
    created_by          VARCHAR(50)   NULL,
    created_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    updated_by          VARCHAR(50)   NULL,
    updated_time        DATETIME      NULL,
    CONSTRAINT UK_material_code UNIQUE (material_code)
);

-- 索引
CREATE INDEX IX_material_type ON bas_material(material_type);
CREATE INDEX IX_material_group ON bas_material(material_group);
CREATE INDEX IX_steel_grade ON bas_material(steel_grade);
```

### 2.2 替代物料 (bas_material_substitute)

```sql
CREATE TABLE bas_material_substitute (
    substitute_id       BIGINT IDENTITY(1,1) PRIMARY KEY,
    material_id         BIGINT        NOT NULL,     -- 主物料
    sub_material_id     BIGINT        NOT NULL,     -- 替代物料
    priority            INT           NOT NULL DEFAULT 1,  -- 替代优先级(1最高)
    convert_rate        DECIMAL(18,6) NOT NULL DEFAULT 1,  -- 换算率
    effective_from      DATETIME      NULL,
    effective_to        DATETIME      NULL,
    is_active           BIT           NOT NULL DEFAULT 1,
    CONSTRAINT FK_sub_material FOREIGN KEY (material_id) 
        REFERENCES bas_material(material_id),
    CONSTRAINT FK_sub_material_alt FOREIGN KEY (sub_material_id) 
        REFERENCES bas_material(material_id)
);
```

### 2.3 BOM 表头 (bas_bom_head)

```sql
CREATE TABLE bas_bom_head (
    bom_id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    bom_code            VARCHAR(60)   NOT NULL,     -- BOM 编码
    material_id         BIGINT        NOT NULL,     -- 父项物料
    bom_version         VARCHAR(20)   NOT NULL DEFAULT 'V1.0',
    bom_type            VARCHAR(10)   NOT NULL DEFAULT 'STD',
                                                    -- STD=标准 ENG=工程 CFG=配置
    base_qty            DECIMAL(18,3) NOT NULL DEFAULT 1,  -- 基准数量
    effective_from      DATETIME      NULL,
    effective_to        DATETIME      NULL,
    is_default          BIT           NOT NULL DEFAULT 1,   -- 是否默认 BOM
    is_active           BIT           NOT NULL DEFAULT 1,
    remark              NVARCHAR(500) NULL,
    created_by          VARCHAR(50)   NULL,
    created_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    updated_by          VARCHAR(50)   NULL,
    updated_time        DATETIME      NULL,
    CONSTRAINT FK_bom_material FOREIGN KEY (material_id) 
        REFERENCES bas_material(material_id),
    CONSTRAINT UK_bom_code UNIQUE (bom_code)
);
```

### 2.4 BOM 明细 (bas_bom_detail)

```sql
CREATE TABLE bas_bom_detail (
    bom_detail_id       BIGINT IDENTITY(1,1) PRIMARY KEY,
    bom_id              BIGINT        NOT NULL,
    line_no             INT           NOT NULL,      -- 行号
    child_material_id   BIGINT        NOT NULL,      -- 子项物料
    qty_per             DECIMAL(18,6) NOT NULL,      -- 单位用量 (对应父项 base_qty)
    scrap_rate          DECIMAL(8,4)  NOT NULL DEFAULT 0,  -- 损耗率 (0.05 = 5%)
    fixed_scrap_qty     DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 固定损耗量
    operation_no        INT           NULL,           -- 关联工序号
    supply_type         VARCHAR(10)   NOT NULL DEFAULT 'S',
                                                     -- S=库存发料 F=倒冲 D=直送工位
    effective_from      DATETIME      NULL,
    effective_to        DATETIME      NULL,
    substitute_group    VARCHAR(20)   NULL,           -- 替代组
    is_active           BIT           NOT NULL DEFAULT 1,
    CONSTRAINT FK_bom_detail_head FOREIGN KEY (bom_id) 
        REFERENCES bas_bom_head(bom_id),
    CONSTRAINT FK_bom_detail_child FOREIGN KEY (child_material_id) 
        REFERENCES bas_material(material_id)
);

CREATE INDEX IX_bom_detail_bom ON bas_bom_detail(bom_id);
```

### 2.5 工作中心 (bas_work_center)

```sql
CREATE TABLE bas_work_center (
    wc_id               BIGINT IDENTITY(1,1) PRIMARY KEY,
    wc_code             VARCHAR(30)   NOT NULL,
    wc_name             NVARCHAR(100) NOT NULL,
    wc_type             VARCHAR(20)   NOT NULL,      -- LINE=产线 MACHINE=机台 STATION=工位
    workshop_code       VARCHAR(30)   NULL,          -- 所属车间
    factory_code        VARCHAR(30)   NULL,          -- 所属工厂
    capacity_unit       VARCHAR(10)   NOT NULL,      -- 产能单位: T/H, PCS/H, M/H
    std_capacity        DECIMAL(18,3) NOT NULL,      -- 标准产能(每小时)
    efficiency_rate     DECIMAL(8,4)  NOT NULL DEFAULT 1.0,  -- 效率系数
    utilization_rate    DECIMAL(8,4)  NOT NULL DEFAULT 0.85, -- 利用率
    shift_mode          VARCHAR(10)   NOT NULL DEFAULT '3S',
                                                     -- 3S=三班倒 2S=两班倒 1S=常白班
    hours_per_shift     DECIMAL(5,2)  NOT NULL DEFAULT 8,
    max_parallel_jobs   INT           NOT NULL DEFAULT 1,    -- 最大并行任务数
    setup_time_minutes  INT           NOT NULL DEFAULT 0,    -- 默认换产时间(分钟)
    queue_time_hours    DECIMAL(8,2)  NOT NULL DEFAULT 0,    -- 默认排队等待时间(小时)
    is_bottleneck       BIT           NOT NULL DEFAULT 0,    -- 是否瓶颈资源
    is_active           BIT           NOT NULL DEFAULT 1,
    created_by          VARCHAR(50)   NULL,
    created_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    CONSTRAINT UK_wc_code UNIQUE (wc_code)
);
```

### 2.6 工作中心产能日历 (bas_wc_capacity)

```sql
CREATE TABLE bas_wc_capacity (
    capacity_id         BIGINT IDENTITY(1,1) PRIMARY KEY,
    wc_id               BIGINT        NOT NULL,
    cal_date            DATE          NOT NULL,      -- 日期 (SQL Server 2008 支持 DATE 类型)
    shift_code          VARCHAR(10)   NOT NULL,      -- 班次: SHIFT1/SHIFT2/SHIFT3
    available_hours     DECIMAL(8,2)  NOT NULL,      -- 可用工时(小时)
    available_capacity  DECIMAL(18,3) NOT NULL,      -- 可用产能(标准单位)
    planned_downtime    DECIMAL(8,2)  NOT NULL DEFAULT 0, -- 计划停机时间(小时)
    capacity_status     VARCHAR(10)   NOT NULL DEFAULT 'NORMAL',
                                                     -- NORMAL/OVERTIME/HOLIDAY/MAINTAIN
    remark              NVARCHAR(200) NULL,
    CONSTRAINT FK_capacity_wc FOREIGN KEY (wc_id) 
        REFERENCES bas_work_center(wc_id),
    CONSTRAINT UK_wc_date_shift UNIQUE (wc_id, cal_date, shift_code)
);

CREATE INDEX IX_capacity_date ON bas_wc_capacity(cal_date);
```

### 2.7 工艺路线表头 (bas_routing_head)

```sql
CREATE TABLE bas_routing_head (
    routing_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    routing_code        VARCHAR(60)   NOT NULL,
    material_id         BIGINT        NOT NULL,      -- 关联产品物料
    routing_version     VARCHAR(20)   NOT NULL DEFAULT 'V1.0',
    is_default          BIT           NOT NULL DEFAULT 1,
    is_active           BIT           NOT NULL DEFAULT 1,
    remark              NVARCHAR(500) NULL,
    created_by          VARCHAR(50)   NULL,
    created_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_routing_material FOREIGN KEY (material_id)
        REFERENCES bas_material(material_id),
    CONSTRAINT UK_routing_code UNIQUE (routing_code)
);
```

### 2.8 工艺路线工序 (bas_routing_oper)

```sql
CREATE TABLE bas_routing_oper (
    oper_id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    routing_id          BIGINT        NOT NULL,
    oper_no             INT           NOT NULL,      -- 工序号: 10, 20, 30...
    oper_name           NVARCHAR(100) NOT NULL,      -- 工序名称
    wc_id               BIGINT        NOT NULL,      -- 主工作中心
    alt_wc_id           BIGINT        NULL,          -- 替代工作中心
    setup_time          DECIMAL(10,2) NOT NULL DEFAULT 0,  -- 准备时间(分钟)
    run_time_per_unit   DECIMAL(10,4) NOT NULL,      -- 单件加工时间(分钟)
    run_time_per_batch  DECIMAL(10,2) NULL,          -- 批量加工时间(分钟)，与单件二选一
    capacity_per_hour   DECIMAL(18,3) NULL,          -- 每小时产出(吨/件/米)
    transfer_batch      DECIMAL(18,3) NULL,          -- 转移批量(流转数量)
    move_time_hours     DECIMAL(8,2)  NOT NULL DEFAULT 0,  -- 工序间转移时间(小时)
    overlap_pct         DECIMAL(5,2)  NOT NULL DEFAULT 0,  -- 工序重叠百分比(0~100)
    is_subcontract      BIT           NOT NULL DEFAULT 0,  -- 是否委外工序
    scrap_rate          DECIMAL(8,4)  NOT NULL DEFAULT 0,
    is_milestone        BIT           NOT NULL DEFAULT 0,  -- 是否关键里程碑工序
    remark              NVARCHAR(200) NULL,
    CONSTRAINT FK_oper_routing FOREIGN KEY (routing_id)
        REFERENCES bas_routing_head(routing_id),
    CONSTRAINT FK_oper_wc FOREIGN KEY (wc_id)
        REFERENCES bas_work_center(wc_id)
);

CREATE INDEX IX_oper_routing ON bas_routing_oper(routing_id, oper_no);
```

### 2.9 工厂日历 (bas_factory_calendar)

```sql
CREATE TABLE bas_factory_calendar (
    calendar_id         BIGINT IDENTITY(1,1) PRIMARY KEY,
    factory_code        VARCHAR(30)   NOT NULL,
    cal_date            DATE          NOT NULL,
    day_type            VARCHAR(10)   NOT NULL,      -- WORK/REST/HOLIDAY/MAINTAIN
    shift1_start        VARCHAR(5)    NULL,          -- 班次1 开始时间 HH:MM
    shift1_end          VARCHAR(5)    NULL,
    shift2_start        VARCHAR(5)    NULL,
    shift2_end          VARCHAR(5)    NULL,
    shift3_start        VARCHAR(5)    NULL,
    shift3_end          VARCHAR(5)    NULL,
    remark              NVARCHAR(200) NULL,
    CONSTRAINT UK_factory_date UNIQUE (factory_code, cal_date)
);

CREATE INDEX IX_calendar_date ON bas_factory_calendar(cal_date);
```

---

## 3. 需求管理表

### 3.1 需求单表头 (dem_demand_head)

```sql
CREATE TABLE dem_demand_head (
    demand_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    demand_no           VARCHAR(30)   NOT NULL,      -- 需求单号
    demand_source       VARCHAR(10)   NOT NULL,      -- MTO=订货合同 MTS=期货 SSK=安全库存补货
    source_doc_no       VARCHAR(60)   NULL,          -- 来源单据编号(合同号/期货单号)
    customer_code       VARCHAR(30)   NULL,          -- 客户编码
    customer_name       NVARCHAR(100) NULL,
    priority            INT           NOT NULL DEFAULT 50,  -- 优先级(1最高, 100最低)
    required_date       DATETIME      NOT NULL,      -- 需求日期(交期)
    demand_status       VARCHAR(10)   NOT NULL DEFAULT 'NEW',
                                                     -- NEW/CONFIRMED/MRP_RUNNING/MRP_DONE/CLOSED/CANCELLED
    mrp_run_id          BIGINT        NULL,          -- 关联的 MRP 运算批次
    remark              NVARCHAR(500) NULL,
    created_by          VARCHAR(50)   NULL,
    created_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    updated_by          VARCHAR(50)   NULL,
    updated_time        DATETIME      NULL,
    CONSTRAINT UK_demand_no UNIQUE (demand_no)
);

CREATE INDEX IX_demand_source ON dem_demand_head(demand_source);
CREATE INDEX IX_demand_status ON dem_demand_head(demand_status);
CREATE INDEX IX_demand_date ON dem_demand_head(required_date);
```

### 3.2 需求单明细 (dem_demand_line) — V3.0 最终版

> **V3.0 修正：** 增加材质、产地、重量字段。钢铁行业客户下单时必然指定材质，
> 可能指定产地，数量以重量(吨)为主、件数/米数为辅。

```sql
CREATE TABLE dem_demand_line (
    demand_line_id      BIGINT IDENTITY(1,1) PRIMARY KEY,
    demand_id           BIGINT        NOT NULL,
    line_no             INT           NOT NULL,
    material_id         BIGINT        NOT NULL,      -- 需求物料(品类+规格)
    
    -- ═══ 材质与产地(钢铁行业核心属性) ═══
    grade_code          VARCHAR(30)   NOT NULL,      -- 材质(必填): Q235B, Q345B, 20#...
    origin_code         VARCHAR(30)   NULL,          -- 产地(可选): 客户可能指定也可能不限
    grade_flexible      BIT           NOT NULL DEFAULT 0,  -- 材质是否允许替代
    origin_flexible     BIT           NOT NULL DEFAULT 1,  -- 产地是否不限(默认不限)
    
    -- ═══ 数量与重量(双单位) ═══
    required_qty        DECIMAL(18,3) NOT NULL,      -- 需求数量(主单位: T/KG/M/PCS)
    required_weight     DECIMAL(18,3) NULL,          -- 需求重量(吨) — 钢铁核心计量
                                                     -- 当主单位=T时, 与required_qty相同
                                                     -- 当主单位=PCS/M时, 由理论重量换算
    price_weight        DECIMAL(18,3) NULL,          -- 计价重量(吨) — 可能与需求重量不同
                                                     -- 如: 按理论重量计价 vs 按过磅重量计价
    
    required_date       DATETIME      NOT NULL,      -- 行级交期
    
    -- ═══ 进度跟踪(也按重量) ═══
    allocated_qty       DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 已分配量
    allocated_weight    DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 已分配重量
    produced_qty        DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 已完成量
    produced_weight     DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 已完成重量
    delivered_qty       DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 已发货量
    delivered_weight    DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 已发货重量
    
    line_status         VARCHAR(10)   NOT NULL DEFAULT 'OPEN',
                                                     -- OPEN/PARTIAL/COMPLETED/CANCELLED
    spec_desc           NVARCHAR(200) NULL,          -- 特殊规格要求
    remark              NVARCHAR(200) NULL,
    CONSTRAINT FK_demand_line_head FOREIGN KEY (demand_id)
        REFERENCES dem_demand_head(demand_id),
    CONSTRAINT FK_demand_line_material FOREIGN KEY (material_id)
        REFERENCES bas_material(material_id)
);

CREATE INDEX IX_demand_line_head ON dem_demand_line(demand_id);
CREATE INDEX IX_demand_line_material ON dem_demand_line(material_id);
CREATE INDEX IX_demand_line_grade ON dem_demand_line(grade_code);
```

---

## 4. 库存管理表

### 4.1 即时库存 (inv_stock) — V3.0 最终版

> **V3.0 修正：** 库存的唯一性维度 = 物料+材质+产地+仓库+库位+批次。
> 同一物料不同材质/产地是不同的库存记录。
> 增加重量字段，数量和重量双轨记录。

```sql
CREATE TABLE inv_stock (
    stock_id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    material_id         BIGINT        NOT NULL,      -- 物料(品类+规格)
    
    -- ═══ 材质与产地(库存核心维度) ═══
    grade_code          VARCHAR(30)   NOT NULL,      -- 材质(必填)
    origin_code         VARCHAR(30)   NULL,          -- 产地
    
    -- ═══ 仓库与批次 ═══
    warehouse_code      VARCHAR(30)   NOT NULL,
    location_code       VARCHAR(30)   NULL,          -- 库位编码
    batch_no            VARCHAR(60)   NULL,          -- 批次号/炉号
    heat_no             VARCHAR(30)   NULL,          -- 炉号(钢铁特有)
    coil_no             VARCHAR(30)   NULL,          -- 卷号(钢卷/带钢特有)
    cert_no             VARCHAR(60)   NULL,          -- 质保书编号
    
    -- ═══ 数量(主单位) ═══
    on_hand_qty         DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 库存数量(主单位)
    reserved_qty        DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 已预留量
    in_transit_qty      DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 在途量
    in_process_qty      DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 在制量
    quality_hold_qty    DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 质检冻结量
    
    -- ═══ 重量(吨) — 钢铁核心计量 ═══
    on_hand_weight      DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 库存重量(吨)
    reserved_weight     DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 已预留重量
    in_transit_weight   DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 在途重量
    in_process_weight   DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 在制重量
    quality_hold_weight DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 冻结重量
    
    -- ═══ 实际规格(可能与物料标准规格有偏差) ═══
    actual_thickness    DECIMAL(10,3) NULL,         -- 实际厚度(钢卷实测)
    actual_width        DECIMAL(10,3) NULL,         -- 实际宽度
    actual_length       DECIMAL(10,3) NULL,         -- 实际长度
    actual_weight       DECIMAL(18,3) NULL,         -- 实际过磅重量
    coil_outer_dia      DECIMAL(10,3) NULL,         -- 钢卷外径
    coil_inner_dia      DECIMAL(10,3) NULL,         -- 钢卷内径
    
    -- ═══ 成本信息 ═══
    unit_price          DECIMAL(18,2) NULL,         -- 单价(元/吨)
    purchase_date       DATETIME      NULL,         -- 采购日期
    
    last_updated        DATETIME      NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_stock_material FOREIGN KEY (material_id)
        REFERENCES bas_material(material_id)
);

-- 可用量 = on_hand_qty - reserved_qty - quality_hold_qty
-- 可用重量 = on_hand_weight - reserved_weight - quality_hold_weight

CREATE INDEX IX_stock_material ON inv_stock(material_id);
CREATE INDEX IX_stock_grade ON inv_stock(grade_code);
CREATE INDEX IX_stock_origin ON inv_stock(origin_code);
CREATE INDEX IX_stock_mat_grade ON inv_stock(material_id, grade_code);
CREATE INDEX IX_stock_coil ON inv_stock(coil_no);
CREATE INDEX IX_stock_warehouse ON inv_stock(warehouse_code);
```

### 4.2 安全库存预警配置 (inv_safety_stock)

```sql
CREATE TABLE inv_safety_stock (
    safety_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    material_id         BIGINT        NOT NULL,
    warehouse_code      VARCHAR(30)   NOT NULL,
    safety_qty          DECIMAL(18,3) NOT NULL,      -- 安全库存量
    reorder_point       DECIMAL(18,3) NULL,          -- 再订货点
    max_stock_qty       DECIMAL(18,3) NULL,          -- 最大库存
    auto_replenish      BIT           NOT NULL DEFAULT 1, -- 低于安全库存自动生成需求
    check_frequency     VARCHAR(10)   NOT NULL DEFAULT 'DAILY',
                                                     -- DAILY/WEEKLY/REALTIME
    CONSTRAINT FK_safety_material FOREIGN KEY (material_id)
        REFERENCES bas_material(material_id)
);
```

---

## 5. MRP 相关表

### 5.1 MRP 运行记录 (mrp_run_log)

```sql
CREATE TABLE mrp_run_log (
    run_id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    run_no              VARCHAR(30)   NOT NULL,      -- 运行批次号
    run_type            VARCHAR(10)   NOT NULL,      -- FULL=完整重算 NET=净变更 SELECTIVE=选择性
    run_scope           VARCHAR(10)   NOT NULL DEFAULT 'ALL',
                                                     -- ALL/MATERIAL/ORDER
    scope_filter        NVARCHAR(500) NULL,          -- 范围过滤条件(JSON格式但存为文本)
    plan_horizon_days   INT           NOT NULL DEFAULT 90,  -- 计划展望期(天)
    run_status          VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
                                                     -- PENDING/RUNNING/COMPLETED/ERROR/CANCELLED
    start_time          DATETIME      NULL,
    end_time            DATETIME      NULL,
    demand_count        INT           NULL,          -- 处理需求行数
    plan_order_count    INT           NULL,          -- 生成计划订单数
    purchase_count      INT           NULL,          -- 生成采购建议数
    error_message       NVARCHAR(2000) NULL,
    run_by              VARCHAR(50)   NULL,
    created_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    CONSTRAINT UK_run_no UNIQUE (run_no)
);
```

### 5.2 MRP 计划订单 (mrp_plan_order) — V3.0 最终版

> **V3.0 修正：** MRP 计划订单必须携带材质和产地，否则无法与库存匹配、
> 无法正确排产。增加重量字段（成材率按重量计算）。
> 合并 08 文档中的品类BOM展开相关字段。

```sql
CREATE TABLE mrp_plan_order (
    plan_order_id       BIGINT IDENTITY(1,1) PRIMARY KEY,
    run_id              BIGINT        NOT NULL,      -- 关联运行批次
    plan_order_no       VARCHAR(30)   NOT NULL,
    material_id         BIGINT        NOT NULL,      -- 计划物料(品类+规格)
    
    -- ═══ 材质与产地(钢铁行业必填) ═══
    grade_code          VARCHAR(30)   NOT NULL,      -- 材质: Q235B, Q345B...
                                                     -- 来源: 继承自需求行的 grade_code
    origin_code         VARCHAR(30)   NULL,          -- 产地: 继承自需求行(可为空=不限)
    grade_flexible      BIT           NOT NULL DEFAULT 0,  -- 材质是否允许替代
    origin_flexible     BIT           NOT NULL DEFAULT 1,  -- 产地是否不限
    
    order_type          VARCHAR(10)   NOT NULL,      -- MFG=制造 PUR=采购 SUB=委外
    
    -- ═══ 数量与重量(双单位) ═══
    planned_qty         DECIMAL(18,3) NOT NULL,      -- 计划数量(主单位)
    planned_weight      DECIMAL(18,3) NULL,          -- 计划重量(吨) — 钢铁核心
    
    planned_start_date  DATETIME      NOT NULL,      -- 计划开始日期
    planned_end_date    DATETIME      NOT NULL,      -- 计划完成日期
    demand_source       VARCHAR(10)   NULL,          -- 需求来源: MTO/MTS/SSK
    source_demand_id    BIGINT        NULL,          -- 溯源需求单ID
    source_demand_line  BIGINT        NULL,          -- 溯源需求行ID
    parent_plan_order   BIGINT        NULL,          -- 父级计划订单(BOM 展开层次)
    bom_level           INT           NOT NULL DEFAULT 0,  -- BOM 层级
    order_status        VARCHAR(10)   NOT NULL DEFAULT 'PLANNED',
                                                     -- PLANNED/CONFIRMED/RELEASED/CANCELLED
    is_firmed           BIT           NOT NULL DEFAULT 0,  -- 是否已确认(人工锁定)
    
    -- ═══ 品类BOM展开时使用(来自08文档) ═══
    is_category_bom     BIT           NOT NULL DEFAULT 0,  -- 是否品类BOM展开
    raw_category_code   VARCHAR(20)   NULL,         -- 原料品类
    raw_width_min       DECIMAL(10,3) NULL,         -- 原料宽度下限
    raw_width_max       DECIMAL(10,3) NULL,         -- 原料宽度上限
    raw_thickness_min   DECIMAL(10,3) NULL,         -- 原料厚度下限
    raw_thickness_max   DECIMAL(10,3) NULL,         -- 原料厚度上限
    raw_grade_code      VARCHAR(30)   NULL,         -- 原料要求材质
    raw_origin_code     VARCHAR(30)   NULL,         -- 原料要求产地
    raw_grade_flexible  BIT           NOT NULL DEFAULT 0,
    raw_origin_flexible BIT           NOT NULL DEFAULT 1,
    
    -- 原料匹配结果(阶段1排产后填入)
    matched_material_id BIGINT        NULL,         -- 匹配到的实际原料物料
    matched_stock_id    BIGINT        NULL,         -- 匹配到的库存批次
    matched_coil_no     VARCHAR(30)   NULL,         -- 匹配到的卷号
    matched_grade_code  VARCHAR(30)   NULL,         -- 匹配到的实际材质
    matched_origin_code VARCHAR(30)   NULL,         -- 匹配到的实际产地
    match_status        VARCHAR(10)   NULL,         -- UNMATCHED/MATCHED/PARTIAL
    
    remark              NVARCHAR(500) NULL,
    created_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_plan_order_run FOREIGN KEY (run_id)
        REFERENCES mrp_run_log(run_id),
    CONSTRAINT FK_plan_order_material FOREIGN KEY (material_id)
        REFERENCES bas_material(material_id),
    CONSTRAINT UK_plan_order_no UNIQUE (plan_order_no)
);

CREATE INDEX IX_plan_order_material ON mrp_plan_order(material_id);
CREATE INDEX IX_plan_order_grade ON mrp_plan_order(grade_code);
CREATE INDEX IX_plan_order_status ON mrp_plan_order(order_status);
CREATE INDEX IX_plan_order_date ON mrp_plan_order(planned_start_date);
```

### 5.3 MRP 钉量记录 (mrp_pegging)

```sql
-- 需求与供给的对应追溯关系
CREATE TABLE mrp_pegging (
    pegging_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    run_id              BIGINT        NOT NULL,
    demand_type         VARCHAR(10)   NOT NULL,      -- DEMAND/SAFETY/FORECAST
    demand_id           BIGINT        NOT NULL,      -- dem_demand_line.demand_line_id
    supply_type         VARCHAR(10)   NOT NULL,      -- STOCK/IN_TRANSIT/IN_PROCESS/PLAN_ORDER
    supply_id           BIGINT        NULL,          -- 对应 inv_stock 或 mrp_plan_order 的ID
    pegged_qty          DECIMAL(18,3) NOT NULL,      -- 钉住数量
    CONSTRAINT FK_pegging_run FOREIGN KEY (run_id)
        REFERENCES mrp_run_log(run_id)
);

CREATE INDEX IX_pegging_demand ON mrp_pegging(demand_id);
CREATE INDEX IX_pegging_supply ON mrp_pegging(supply_id);
```

---

## 6. 排产调度表

### 6.1 排产主计划 (aps_schedule) — V3.0 最终版

> **V3.0 修正：** 排产单是生产执行的核心单据，必须完整表达"生产什么物料、
> 什么材质、什么产地、多少重量、用什么原料"。合并 08/09/13 文档中的所有增量字段。

```sql
CREATE TABLE aps_schedule (
    schedule_id         BIGINT IDENTITY(1,1) PRIMARY KEY,
    schedule_no         VARCHAR(30)   NOT NULL,      -- 排产单号
    plan_order_id       BIGINT        NULL,          -- 关联 MRP 计划订单
    material_id         BIGINT        NOT NULL,      -- 产出物料(品类+规格)
    
    -- ═══ 产出材质与产地 ═══
    -- 需求要求的材质/产地(来自订单, 不可变)
    demand_grade_code   VARCHAR(30)   NOT NULL,      -- 需求材质
    demand_origin_code  VARCHAR(30)   NULL,          -- 需求产地(客户指定, 可为空)
    -- 实际使用原料的材质/产地(可能因替代而与需求不同)
    actual_grade_code   VARCHAR(30)   NULL,          -- 实际原料材质
    actual_origin_code  VARCHAR(30)   NULL,          -- 实际原料产地
    -- 产出成品标记的材质/产地(按继承规则自动计算)
    output_grade_code   VARCHAR(30)   NULL,          -- 产出标记材质
    output_origin_code  VARCHAR(30)   NULL,          -- 产出标记产地
    -- 替代标记
    grade_substituted   BIT           NOT NULL DEFAULT 0,
    origin_substituted  BIT           NOT NULL DEFAULT 0,
    
    -- ═══ 数量与重量(双单位, 重量为核心) ═══
    planned_qty         DECIMAL(18,3) NOT NULL,      -- 排产数量(主单位: T/PCS/M)
    planned_weight      DECIMAL(18,3) NOT NULL,      -- 排产重量(吨) — 核心计量
    good_qty            DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 合格产出量
    good_weight         DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 合格产出重量(吨)
    scrap_qty           DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 报废量
    scrap_weight        DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 报废重量(吨)
    -- 投入原料重量(用于计算成材率)
    input_weight        DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 投入原料重量(吨)
    -- 成材率 = good_weight / input_weight
    yield_rate          DECIMAL(8,4)  NULL,          -- 成材率(完工后计算)
    
    -- ═══ 时间 ═══
    schedule_start      DATETIME      NOT NULL,      -- 排产开始时间
    schedule_end        DATETIME      NOT NULL,      -- 排产结束时间
    priority            INT           NOT NULL DEFAULT 50,
    
    -- ═══ 需求溯源 ═══
    demand_source       VARCHAR(10)   NULL,          -- MTO/MTS/SSK
    source_demand_no    VARCHAR(60)   NULL,          -- 溯源订单号
    customer_code       VARCHAR(30)   NULL,          -- 客户编码
    customer_name       NVARCHAR(100) NULL,          -- 客户名称
    
    -- ═══ 原料信息(阶段1排产绑定) ═══
    raw_material_id     BIGINT        NULL,          -- 原料物料ID
    raw_stock_id        BIGINT        NULL,          -- 原料库存批次ID
    raw_grade_code      VARCHAR(30)   NULL,          -- 原料材质
    raw_origin_code     VARCHAR(30)   NULL,          -- 原料产地
    raw_coil_no         VARCHAR(30)   NULL,          -- 原料钢卷号
    
    -- ═══ 模具信息(09文档) ═══
    mold_id             BIGINT        NULL,          -- 使用的模具
    mold_accumulated_before DECIMAL(18,3) NULL,      -- 排产前模具累计
    mold_accumulated_after  DECIMAL(18,3) NULL,      -- 排产后模具预计累计
    mold_change_required BIT          NOT NULL DEFAULT 0,
    mold_change_schedule_id BIGINT    NULL,          -- 换模后续产单ID
    
    -- ═══ 产出流向(09文档) ═══
    output_flow_type    VARCHAR(20)   NOT NULL DEFAULT 'FG_STOCK',
    output_flow_desc    NVARCHAR(100) NULL,
    next_oper_name      NVARCHAR(100) NULL,          -- 下一工序名称
    next_schedule_id    BIGINT        NULL,          -- 下一排产单ID
    next_wc_id          BIGINT        NULL,          -- 下一工序工作中心
    target_warehouse    VARCHAR(30)   NULL,          -- 目标仓库
    direct_customer     NVARCHAR(100) NULL,          -- 直发客户
    
    -- ═══ 多阶段排产(08文档) ═══
    schedule_phase      VARCHAR(10)   NOT NULL DEFAULT 'MFG',
    schedule_level      VARCHAR(10)   NOT NULL DEFAULT 'PRODUCT',
    parent_schedule_id  BIGINT        NULL,
    is_multi_output     BIT           NOT NULL DEFAULT 0,
    nesting_plan_id     BIGINT        NULL,          -- 套裁方案ID
    
    -- ═══ 状态与控制 ═══
    schedule_status     VARCHAR(10)   NOT NULL DEFAULT 'DRAFT',
                                                     -- DRAFT/CONFIRMED/RELEASED/IN_PROGRESS
                                                     -- /WAITING_MATERIAL/COMPLETED/CANCELLED
    is_locked           BIT           NOT NULL DEFAULT 0,
    lock_reason         NVARCHAR(200) NULL,
    schedule_version    INT           NOT NULL DEFAULT 1,
    remark              NVARCHAR(500) NULL,
    created_by          VARCHAR(50)   NULL,
    created_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    updated_by          VARCHAR(50)   NULL,
    updated_time        DATETIME      NULL,
    CONSTRAINT UK_schedule_no UNIQUE (schedule_no)
);

CREATE INDEX IX_schedule_date ON aps_schedule(schedule_start, schedule_end);
CREATE INDEX IX_schedule_material ON aps_schedule(material_id);
CREATE INDEX IX_schedule_grade ON aps_schedule(demand_grade_code);
CREATE INDEX IX_schedule_status ON aps_schedule(schedule_status);
CREATE INDEX IX_schedule_customer ON aps_schedule(customer_code);
```

### 6.2 排产工序计划 (aps_schedule_oper) — V3.0 最终版

> **V3.0 修正：** 增加重量、材质/产地、投入/产出物料、工序级流向字段。
> 每道工序可能改变物料形态（如分剪工序投入钢卷产出带钢），需要记录。

```sql
CREATE TABLE aps_schedule_oper (
    sched_oper_id       BIGINT IDENTITY(1,1) PRIMARY KEY,
    schedule_id         BIGINT        NOT NULL,
    oper_no             INT           NOT NULL,      -- 工序号
    oper_name           NVARCHAR(100) NOT NULL,
    wc_id               BIGINT        NOT NULL,      -- 分配的工作中心
    
    -- ═══ 数量 ═══
    planned_qty         DECIMAL(18,3) NOT NULL,
    completed_qty       DECIMAL(18,3) NOT NULL DEFAULT 0,
    scrap_qty           DECIMAL(18,3) NOT NULL DEFAULT 0,
    
    -- ═══ 重量(吨) — V3.0新增 ═══
    planned_weight      DECIMAL(18,3) NULL,          -- 计划重量
    completed_weight    DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 完成重量
    scrap_weight        DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 报废重量
    input_weight        DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 投入原料重量
    
    -- ═══ 材质/产地 — V3.0新增 ═══
    grade_code          VARCHAR(30)   NULL,
    origin_code         VARCHAR(30)   NULL,
    
    -- ═══ 投入/产出物料(工序可能改变形态) — V3.0新增 ═══
    input_material_id   BIGINT        NULL,          -- 本工序投入物料
    output_material_id  BIGINT        NULL,          -- 本工序产出物料
    
    -- ═══ 时间 ═══
    setup_start         DATETIME      NULL,
    setup_end           DATETIME      NULL,
    oper_start          DATETIME      NOT NULL,
    oper_end            DATETIME      NOT NULL,
    actual_start        DATETIME      NULL,
    actual_end          DATETIME      NULL,
    
    oper_status         VARCHAR(10)   NOT NULL DEFAULT 'PLANNED',
                                                     -- PLANNED/SETUP/RUNNING/COMPLETED/CANCELLED
    is_locked           BIT           NOT NULL DEFAULT 0,
    sequence_in_wc      INT           NULL,
    
    -- ═══ 工序流向 — V3.0新增 ═══
    output_flow_type    VARCHAR(20)   NULL,           -- NEXT_OPER/STOCK/CUSTOMER
    output_flow_desc    NVARCHAR(100) NULL,
    next_sched_oper_id  BIGINT        NULL,           -- 下一工序ID
    
    -- ═══ 模具(09文档) ═══
    mold_id             BIGINT        NULL,
    is_mold_change      BIT           NOT NULL DEFAULT 0,
    mold_change_from    BIGINT        NULL,
    mold_change_to      BIGINT        NULL,
    
    remark              NVARCHAR(200) NULL,
    CONSTRAINT FK_sched_oper_head FOREIGN KEY (schedule_id)
        REFERENCES aps_schedule(schedule_id),
    CONSTRAINT FK_sched_oper_wc FOREIGN KEY (wc_id)
        REFERENCES bas_work_center(wc_id)
);

CREATE INDEX IX_sched_oper_schedule ON aps_schedule_oper(schedule_id);
CREATE INDEX IX_sched_oper_wc ON aps_schedule_oper(wc_id, oper_start);
CREATE INDEX IX_sched_oper_time ON aps_schedule_oper(oper_start, oper_end);
```

### 6.3 排产调整日志 (aps_schedule_change_log)

```sql
CREATE TABLE aps_schedule_change_log (
    change_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    schedule_id         BIGINT        NOT NULL,
    change_type         VARCHAR(20)   NOT NULL,      -- MOVE/SPLIT/MERGE/PRIORITY/CANCEL/INSERT/WC_CHANGE
    change_desc         NVARCHAR(500) NULL,
    old_value           NVARCHAR(500) NULL,          -- 变更前值
    new_value           NVARCHAR(500) NULL,          -- 变更后值
    changed_by          VARCHAR(50)   NOT NULL,
    changed_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_change_schedule FOREIGN KEY (schedule_id)
        REFERENCES aps_schedule(schedule_id)
);
```

### 6.4 工作中心负荷 (aps_wc_load)

```sql
-- 排产后的工作中心负荷快照(方便产能分析)
CREATE TABLE aps_wc_load (
    load_id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    wc_id               BIGINT        NOT NULL,
    load_date           DATE          NOT NULL,
    shift_code          VARCHAR(10)   NOT NULL,
    available_hours     DECIMAL(8,2)  NOT NULL,      -- 可用工时
    loaded_hours        DECIMAL(8,2)  NOT NULL,      -- 已分配工时
    setup_hours         DECIMAL(8,2)  NOT NULL DEFAULT 0,  -- 换产占用工时
    load_rate           AS (CASE WHEN available_hours > 0 
                            THEN loaded_hours / available_hours 
                            ELSE 0 END),             -- 负荷率(计算列)
    overload_flag       AS (CASE WHEN loaded_hours > available_hours 
                            THEN 1 ELSE 0 END),     -- 超负荷标记
    CONSTRAINT FK_load_wc FOREIGN KEY (wc_id)
        REFERENCES bas_work_center(wc_id)
);

CREATE INDEX IX_wc_load ON aps_wc_load(wc_id, load_date);
```

---

## 7. 生产执行表 — V3.0 最终版

> **V3.0 修正：** 新增上料/领料表(prd_material_issue)、库存出入库流水(inv_transaction)、
> 生产入库单(inv_receipt)。报工表增加重量/材质/产地字段。
> 详细设计和数据走查示例参见 **[16-execution-fullchain-walkthrough.md](16-execution-fullchain-walkthrough.md)**。

### 7.1 上料/领料记录 (prd_material_issue) — V3.0 新增

```sql
CREATE TABLE prd_material_issue (
    issue_id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    issue_no            VARCHAR(30)   NOT NULL,
    issue_type          VARCHAR(20)   NOT NULL,     -- NORMAL/REPLENISH/SWAP/RETURN
    schedule_id         BIGINT        NOT NULL,
    sched_oper_id       BIGINT        NULL,
    wc_id               BIGINT        NOT NULL,
    material_id         BIGINT        NOT NULL,
    grade_code          VARCHAR(30)   NOT NULL,
    origin_code         VARCHAR(30)   NULL,
    stock_id            BIGINT        NOT NULL,
    coil_no             VARCHAR(30)   NULL,
    batch_no            VARCHAR(60)   NULL,
    issue_qty           DECIMAL(18,3) NOT NULL,
    issue_weight        DECIMAL(18,3) NOT NULL,     -- 领料重量(吨)
    warehouse_code      VARCHAR(30)   NOT NULL,
    location_code       VARCHAR(30)   NULL,
    issue_status        VARCHAR(10)   NOT NULL DEFAULT 'REQUESTED',
                                                    -- REQUESTED/APPROVED/PICKED/ISSUED
                                                    -- /DELIVERED/RETURNED
    requested_by        VARCHAR(50)   NOT NULL,
    requested_time      DATETIME      NOT NULL DEFAULT GETDATE(),
    issued_by           VARCHAR(50)   NULL,
    issued_time         DATETIME      NULL,
    received_by         VARCHAR(50)   NULL,
    received_time       DATETIME      NULL,
    remark              NVARCHAR(200) NULL,
    CONSTRAINT UK_issue_no UNIQUE (issue_no),
    CONSTRAINT FK_issue_schedule FOREIGN KEY (schedule_id) 
        REFERENCES aps_schedule(schedule_id)
);

CREATE INDEX IX_issue_schedule ON prd_material_issue(schedule_id);
CREATE INDEX IX_issue_stock ON prd_material_issue(stock_id);
```

### 7.2 生产报工 (prd_report) — V3.0 增强

```sql
CREATE TABLE prd_report (
    report_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    schedule_id         BIGINT        NOT NULL,
    sched_oper_id       BIGINT        NOT NULL,
    report_time         DATETIME      NOT NULL DEFAULT GETDATE(),
    shift_code          VARCHAR(10)   NOT NULL,
    
    -- ═══ 数量 ═══
    report_qty          DECIMAL(18,3) NOT NULL,
    good_qty            DECIMAL(18,3) NOT NULL,
    scrap_qty           DECIMAL(18,3) NOT NULL DEFAULT 0,
    rework_qty          DECIMAL(18,3) NOT NULL DEFAULT 0,
    
    -- ═══ 重量(吨) — V3.0新增 ═══
    report_weight       DECIMAL(18,3) NULL,         -- 报工重量
    good_weight         DECIMAL(18,3) NULL,         -- 合格重量
    scrap_weight        DECIMAL(18,3) NULL,         -- 废品重量
    input_weight        DECIMAL(18,3) NULL,         -- 投入原料重量
    
    -- ═══ 材质/产地/追溯 — V3.0新增 ═══
    grade_code          VARCHAR(30)   NULL,          -- 实际使用材质
    origin_code         VARCHAR(30)   NULL,          -- 实际使用产地
    coil_no             VARCHAR(30)   NULL,          -- 使用卷号
    output_batch_no     VARCHAR(60)   NULL,          -- 产出批次号
    output_coil_no      VARCHAR(30)   NULL,          -- 产出卷号
    
    start_time          DATETIME      NULL,
    end_time            DATETIME      NULL,
    operator_code       VARCHAR(30)   NULL,
    remark              NVARCHAR(200) NULL,
    CONSTRAINT FK_report_schedule FOREIGN KEY (schedule_id)
        REFERENCES aps_schedule(schedule_id),
    CONSTRAINT FK_report_oper FOREIGN KEY (sched_oper_id)
        REFERENCES aps_schedule_oper(sched_oper_id)
);

CREATE INDEX IX_report_schedule ON prd_report(schedule_id);
CREATE INDEX IX_report_time ON prd_report(report_time);
```

### 7.3 库存出入库流水 (inv_transaction) — V3.0 新增

```sql
CREATE TABLE inv_transaction (
    txn_id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    txn_no              VARCHAR(30)   NOT NULL,
    txn_type            VARCHAR(20)   NOT NULL,     -- PURCHASE_IN/PRODUCE_IN/SLIT_IN
                                                    -- /ISSUE_OUT/SHIP_OUT/TRANSFER
                                                    -- /ADJUST/SCRAP_OUT/RETURN_IN
    txn_direction       VARCHAR(3)    NOT NULL,     -- IN/OUT
    material_id         BIGINT        NOT NULL,
    grade_code          VARCHAR(30)   NOT NULL,
    origin_code         VARCHAR(30)   NULL,
    stock_id            BIGINT        NULL,
    coil_no             VARCHAR(30)   NULL,
    batch_no            VARCHAR(60)   NULL,
    txn_qty             DECIMAL(18,3) NOT NULL,
    txn_weight          DECIMAL(18,3) NOT NULL,     -- 交易重量(吨)
    warehouse_code      VARCHAR(30)   NOT NULL,
    location_code       VARCHAR(30)   NULL,
    to_warehouse_code   VARCHAR(30)   NULL,
    to_location_code    VARCHAR(30)   NULL,
    source_doc_type     VARCHAR(20)   NULL,
    source_doc_id       BIGINT        NULL,
    source_doc_no       VARCHAR(30)   NULL,
    before_qty          DECIMAL(18,3) NULL,
    after_qty           DECIMAL(18,3) NULL,
    before_weight       DECIMAL(18,3) NULL,
    after_weight        DECIMAL(18,3) NULL,
    txn_time            DATETIME      NOT NULL DEFAULT GETDATE(),
    operated_by         VARCHAR(50)   NOT NULL,
    remark              NVARCHAR(200) NULL,
    CONSTRAINT UK_txn_no UNIQUE (txn_no)
);

CREATE INDEX IX_txn_material ON inv_transaction(material_id);
CREATE INDEX IX_txn_stock ON inv_transaction(stock_id);
CREATE INDEX IX_txn_time ON inv_transaction(txn_time);
CREATE INDEX IX_txn_source ON inv_transaction(source_doc_type, source_doc_id);
```

### 7.4 生产入库单 (inv_receipt) — V3.0 新增

```sql
CREATE TABLE inv_receipt (
    receipt_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    receipt_no          VARCHAR(30)   NOT NULL,
    receipt_type        VARCHAR(20)   NOT NULL,     -- PRODUCE/SLIT/PURCHASE/RETURN
    schedule_id         BIGINT        NULL,
    sched_oper_id       BIGINT        NULL,
    purchase_order_no   VARCHAR(30)   NULL,
    material_id         BIGINT        NOT NULL,
    grade_code          VARCHAR(30)   NOT NULL,
    origin_code         VARCHAR(30)   NULL,
    receipt_qty         DECIMAL(18,3) NOT NULL,
    receipt_weight      DECIMAL(18,3) NOT NULL,     -- 入库重量(吨)
    theory_weight       DECIMAL(18,3) NULL,
    actual_weight       DECIMAL(18,3) NULL,         -- 过磅重量
    qc_status           VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
                                                    -- PENDING/PASSED/FAILED/WAIVED
    qc_by               VARCHAR(50)   NULL,
    qc_time             DATETIME      NULL,
    warehouse_code      VARCHAR(30)   NOT NULL,
    location_code       VARCHAR(30)   NULL,
    target_stock_id     BIGINT        NULL,
    coil_no             VARCHAR(30)   NULL,
    receipt_status      VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
                                                    -- PENDING/QC/RECEIVED/REJECTED
    received_by         VARCHAR(50)   NULL,
    received_time       DATETIME      NULL,
    created_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    remark              NVARCHAR(200) NULL,
    CONSTRAINT UK_receipt_no UNIQUE (receipt_no)
);

CREATE INDEX IX_receipt_schedule ON inv_receipt(schedule_id);
CREATE INDEX IX_receipt_status ON inv_receipt(receipt_status);
```

---

## 8. 换产时间矩阵 (bas_setup_matrix)

```sql
-- 钢铁行业的换规格/换辊时间与前后产品规格相关
CREATE TABLE bas_setup_matrix (
    setup_id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    wc_id               BIGINT        NOT NULL,
    from_material_group VARCHAR(30)   NULL,          -- 前一产品组
    from_spec_pattern   VARCHAR(60)   NULL,          -- 前一规格模式
    to_material_group   VARCHAR(30)   NULL,          -- 后一产品组
    to_spec_pattern     VARCHAR(60)   NULL,          -- 后一规格模式
    setup_time_minutes  INT           NOT NULL,      -- 换产时间(分钟)
    setup_cost          DECIMAL(18,2) NULL,          -- 换产成本
    CONSTRAINT FK_setup_wc FOREIGN KEY (wc_id)
        REFERENCES bas_work_center(wc_id)
);
```

---

## 9. 关键视图

### 9.1 物料可用量视图 — V3.0 (含材质/产地/重量)

```sql
CREATE VIEW v_material_available AS
SELECT 
    s.material_id,
    m.material_code,
    m.material_name,
    m.spec_desc,
    m.category_code,
    s.grade_code,              -- 材质(维度)
    s.origin_code,             -- 产地(维度)
    s.warehouse_code,
    s.batch_no,
    s.coil_no,
    -- 数量
    s.on_hand_qty,
    s.reserved_qty,
    s.in_transit_qty,
    s.in_process_qty,
    s.quality_hold_qty,
    (s.on_hand_qty - s.reserved_qty - s.quality_hold_qty) AS available_qty,
    (s.on_hand_qty - s.reserved_qty - s.quality_hold_qty 
     + s.in_transit_qty + s.in_process_qty) AS projected_available_qty,
    -- 重量(吨)
    s.on_hand_weight,
    s.reserved_weight,
    s.in_transit_weight,
    s.in_process_weight,
    s.quality_hold_weight,
    (s.on_hand_weight - s.reserved_weight - s.quality_hold_weight) AS available_weight,
    (s.on_hand_weight - s.reserved_weight - s.quality_hold_weight 
     + s.in_transit_weight + s.in_process_weight) AS projected_available_weight,
    -- 安全库存
    ss.safety_qty,
    CASE WHEN (s.on_hand_weight - s.reserved_weight - s.quality_hold_weight) 
              < ISNULL(ss.safety_qty, 0) 
         THEN 1 ELSE 0 END AS below_safety_flag
FROM inv_stock s
INNER JOIN bas_material m ON s.material_id = m.material_id
LEFT JOIN inv_safety_stock ss ON s.material_id = ss.material_id 
    AND s.warehouse_code = ss.warehouse_code;
```

---

## 10. 材质/产地/重量 全链路数据流说明 (V3.0 新增)

> 钢铁行业的三大核心属性 — 材质(grade)、产地(origin)、重量(weight)
> 必须贯穿 需求→库存→MRP→排产→报工→入库 的完整链路。

```
┌───────────────────────────────────────────────────────────────────────────┐
│          材质/产地/重量 在全链路中的流转                                     │
│                                                                           │
│  ┌─ 需求 (dem_demand_line) ─────────────────────────────────────────────┐│
│  │  客户下单: 方管100×50×4.0 | Q235B | 鞍钢(可选) | 50吨               ││
│  │  字段:     material_id    | grade  | origin     | weight             ││
│  │  含义:     品类+规格       | 必填   | 客户指定   | 核心计量(非件数)   ││
│  └──────────────────────────────────┬──────────────────────────────────┘│
│                                     │ MRP 需求收集                       │
│  ┌──────────────────────────────────▼──────────────────────────────────┐│
│  │  库存匹配 (inv_stock)                                               ││
│  │  匹配条件: material_id + grade_code + origin_code(如有) + 可用重量   ││
│  │  同物料不同材质 = 不同库存行, 可用量按重量计算                        ││
│  │  找到: 库存 8T (Q235B 鞍钢) → 净需求 = 50-8 = 42T (按重量)          ││
│  └──────────────────────────────────┬──────────────────────────────────┘│
│                                     │ MRP 计划订单                       │
│  ┌──────────────────────────────────▼──────────────────────────────────┐│
│  │  MRP计划 (mrp_plan_order)                                           ││
│  │  计划: 方管100×50×4.0 | Q235B | 鞍钢(继承) | 42吨                   ││
│  │  BOM展开时: 原料需求也带材质 → 带钢290×4.0 Q235B 43.7吨(含损耗)     ││
│  │  材质/产地从需求行继承, 重量按成材率反算                              ││
│  └──────────────────────────────────┬──────────────────────────────────┘│
│                                     │ 排产                               │
│  ┌──────────────────────────────────▼──────────────────────────────────┐│
│  │  排产 (aps_schedule)                                                ││
│  │  需求材质/产地:   demand_grade = Q235B, demand_origin = 鞍钢         ││
│  │  实际使用原料:    actual_grade = Q235B, actual_origin = 首钢(替代)   ││
│  │  产出标记:        output_grade = Q235B, output_origin = 首钢        ││
│  │  排产重量:        planned_weight = 42T                              ││
│  │  投入原料重量:    input_weight = 43.7T                              ││
│  │  产出重量:        good_weight = 40.3T (实际)                        ││
│  │  成材率:          yield_rate = 40.3/43.7 = 92.2%                    ││
│  └──────────────────────────────────┬──────────────────────────────────┘│
│                                     │ 报工入库                           │
│  ┌──────────────────────────────────▼──────────────────────────────────┐│
│  │  入库 → inv_stock 新增一条:                                         ││
│  │  material_id = 方管100×50×4.0                                       ││
│  │  grade_code = Q235B (产出标记材质)                                   ││
│  │  origin_code = 首钢 (产出标记产地, 真实原料来源)                      ││
│  │  on_hand_weight = 40.3T                                             ││
│  │  heat_no = 来源原料的炉号 (追溯)                                     ││
│  └─────────────────────────────────────────────────────────────────────┘│
│                                                                           │
│  ══ 重量是钢铁行业的"第一语言" ══                                          │
│  · MRP 净需求按重量计算, 不是按件数                                        │
│  · 成材率 = 产出重量 / 投入重量 (不是数量比)                               │
│  · 产能以 吨/小时 衡量, 不是 件/小时                                       │
│  · 库存可用量以重量判断, 不是件数                                          │
│  · 价格以 元/吨 计算                                                       │
│                                                                           │
└───────────────────────────────────────────────────────────────────────────┘
```

### 9.2 工作中心负荷率视图

```sql
CREATE VIEW v_wc_load_summary AS
SELECT 
    wl.wc_id,
    wc.wc_code,
    wc.wc_name,
    wl.load_date,
    SUM(wl.available_hours) AS total_available,
    SUM(wl.loaded_hours) AS total_loaded,
    SUM(wl.setup_hours) AS total_setup,
    CASE WHEN SUM(wl.available_hours) > 0 
         THEN SUM(wl.loaded_hours) / SUM(wl.available_hours) * 100
         ELSE 0 END AS load_pct
FROM aps_wc_load wl
INNER JOIN bas_work_center wc ON wl.wc_id = wc.wc_id
GROUP BY wl.wc_id, wc.wc_code, wc.wc_name, wl.load_date;
```
