# AiAPS — 数据模型设计（SQL Server 2008 兼容）

> 版本：2.0 | 最后更新：2026-03-17
>
> 所有 DDL 严格兼容 SQL Server 2008，不使用 SEQUENCE、STRING_AGG、JSON 等高版本特性。
>
> **重要：** 本文档为基础版数据模型。钢铁行业深度适配（物料模型重构、品类BOM、
> 套裁方案表等）的增量表设计请参见 **[08-steel-industry-adaptation.md](08-steel-industry-adaptation.md)**。
> 08 文档中的表结构为增量设计，会替换/扩展本文档中对应的表。

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

### 3.2 需求单明细 (dem_demand_line)

```sql
CREATE TABLE dem_demand_line (
    demand_line_id      BIGINT IDENTITY(1,1) PRIMARY KEY,
    demand_id           BIGINT        NOT NULL,
    line_no             INT           NOT NULL,
    material_id         BIGINT        NOT NULL,      -- 需求物料
    required_qty        DECIMAL(18,3) NOT NULL,      -- 需求数量
    required_date       DATETIME      NOT NULL,      -- 行级交期
    allocated_qty       DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 已分配量
    produced_qty        DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 已完成量
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
```

---

## 4. 库存管理表

### 4.1 即时库存 (inv_stock)

```sql
CREATE TABLE inv_stock (
    stock_id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    material_id         BIGINT        NOT NULL,
    warehouse_code      VARCHAR(30)   NOT NULL,      -- 仓库编码
    location_code       VARCHAR(30)   NULL,          -- 库位编码
    on_hand_qty         DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 库存数量
    reserved_qty        DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 已预留量(已被需求分配)
    in_transit_qty      DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 在途量(采购已下单未到货)
    in_process_qty      DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 在制量(已投产未入库)
    quality_hold_qty    DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 质检冻结量
    last_updated        DATETIME      NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_stock_material FOREIGN KEY (material_id)
        REFERENCES bas_material(material_id),
    CONSTRAINT UK_stock_material_wh UNIQUE (material_id, warehouse_code, ISNULL(location_code,''))
);

-- 可用量 = on_hand_qty - reserved_qty - quality_hold_qty
-- 供给量 = on_hand_qty - reserved_qty - quality_hold_qty + in_transit_qty + in_process_qty
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

### 5.2 MRP 计划订单 (mrp_plan_order)

```sql
CREATE TABLE mrp_plan_order (
    plan_order_id       BIGINT IDENTITY(1,1) PRIMARY KEY,
    run_id              BIGINT        NOT NULL,      -- 关联运行批次
    plan_order_no       VARCHAR(30)   NOT NULL,
    material_id         BIGINT        NOT NULL,
    order_type          VARCHAR(10)   NOT NULL,      -- MFG=制造 PUR=采购 SUB=委外
    planned_qty         DECIMAL(18,3) NOT NULL,      -- 计划数量
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
    remark              NVARCHAR(500) NULL,
    created_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_plan_order_run FOREIGN KEY (run_id)
        REFERENCES mrp_run_log(run_id),
    CONSTRAINT FK_plan_order_material FOREIGN KEY (material_id)
        REFERENCES bas_material(material_id),
    CONSTRAINT UK_plan_order_no UNIQUE (plan_order_no)
);

CREATE INDEX IX_plan_order_material ON mrp_plan_order(material_id);
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

### 6.1 排产主计划 (aps_schedule)

```sql
CREATE TABLE aps_schedule (
    schedule_id         BIGINT IDENTITY(1,1) PRIMARY KEY,
    schedule_no         VARCHAR(30)   NOT NULL,      -- 排产单号
    plan_order_id       BIGINT        NULL,          -- 关联 MRP 计划订单
    material_id         BIGINT        NOT NULL,
    planned_qty         DECIMAL(18,3) NOT NULL,      -- 排产数量
    good_qty            DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 合格产出量
    scrap_qty           DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 报废量
    schedule_start      DATETIME      NOT NULL,      -- 排产开始时间
    schedule_end        DATETIME      NOT NULL,      -- 排产结束时间
    priority            INT           NOT NULL DEFAULT 50,
    demand_source       VARCHAR(10)   NULL,          -- MTO/MTS/SSK
    source_demand_no    VARCHAR(60)   NULL,          -- 溯源订单号(方便查看)
    customer_name       NVARCHAR(100) NULL,          -- 客户名(冗余，方便展示)
    schedule_status     VARCHAR(10)   NOT NULL DEFAULT 'DRAFT',
                                                     -- DRAFT/CONFIRMED/RELEASED/IN_PROGRESS/COMPLETED/CANCELLED
    is_locked           BIT           NOT NULL DEFAULT 0,  -- 锁定(不参与重排)
    lock_reason         NVARCHAR(200) NULL,
    schedule_version    INT           NOT NULL DEFAULT 1,  -- 版本号(每次调整+1)
    remark              NVARCHAR(500) NULL,
    created_by          VARCHAR(50)   NULL,
    created_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    updated_by          VARCHAR(50)   NULL,
    updated_time        DATETIME      NULL,
    CONSTRAINT UK_schedule_no UNIQUE (schedule_no)
);

CREATE INDEX IX_schedule_date ON aps_schedule(schedule_start, schedule_end);
CREATE INDEX IX_schedule_material ON aps_schedule(material_id);
CREATE INDEX IX_schedule_status ON aps_schedule(schedule_status);
```

### 6.2 排产工序计划 (aps_schedule_oper)

```sql
CREATE TABLE aps_schedule_oper (
    sched_oper_id       BIGINT IDENTITY(1,1) PRIMARY KEY,
    schedule_id         BIGINT        NOT NULL,
    oper_no             INT           NOT NULL,      -- 工序号
    oper_name           NVARCHAR(100) NOT NULL,
    wc_id               BIGINT        NOT NULL,      -- 分配的工作中心
    planned_qty         DECIMAL(18,3) NOT NULL,
    completed_qty       DECIMAL(18,3) NOT NULL DEFAULT 0,
    scrap_qty           DECIMAL(18,3) NOT NULL DEFAULT 0,
    setup_start         DATETIME      NULL,          -- 准备开始时间
    setup_end           DATETIME      NULL,          -- 准备结束时间
    oper_start          DATETIME      NOT NULL,      -- 加工开始时间
    oper_end            DATETIME      NOT NULL,      -- 加工结束时间
    actual_start        DATETIME      NULL,          -- 实际开始
    actual_end          DATETIME      NULL,          -- 实际结束
    oper_status         VARCHAR(10)   NOT NULL DEFAULT 'PLANNED',
                                                     -- PLANNED/SETUP/RUNNING/COMPLETED/CANCELLED
    is_locked           BIT           NOT NULL DEFAULT 0,
    sequence_in_wc      INT           NULL,          -- 在工作中心中的排序位置
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

## 7. 报工与实绩表

### 7.1 生产报工 (prd_report)

```sql
CREATE TABLE prd_report (
    report_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    schedule_id         BIGINT        NOT NULL,
    sched_oper_id       BIGINT        NOT NULL,
    report_time         DATETIME      NOT NULL DEFAULT GETDATE(),
    shift_code          VARCHAR(10)   NOT NULL,
    report_qty          DECIMAL(18,3) NOT NULL,      -- 报工数量
    good_qty            DECIMAL(18,3) NOT NULL,      -- 合格数量
    scrap_qty           DECIMAL(18,3) NOT NULL DEFAULT 0,
    rework_qty          DECIMAL(18,3) NOT NULL DEFAULT 0,
    start_time          DATETIME      NULL,          -- 实际开工时间
    end_time            DATETIME      NULL,          -- 实际完工时间
    operator_code       VARCHAR(30)   NULL,          -- 操作人员
    remark              NVARCHAR(200) NULL,
    CONSTRAINT FK_report_schedule FOREIGN KEY (schedule_id)
        REFERENCES aps_schedule(schedule_id),
    CONSTRAINT FK_report_oper FOREIGN KEY (sched_oper_id)
        REFERENCES aps_schedule_oper(sched_oper_id)
);

CREATE INDEX IX_report_schedule ON prd_report(schedule_id);
CREATE INDEX IX_report_time ON prd_report(report_time);
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

### 9.1 物料可用量视图

```sql
CREATE VIEW v_material_available AS
SELECT 
    s.material_id,
    m.material_code,
    m.material_name,
    m.material_spec,
    s.warehouse_code,
    s.on_hand_qty,
    s.reserved_qty,
    s.in_transit_qty,
    s.in_process_qty,
    s.quality_hold_qty,
    (s.on_hand_qty - s.reserved_qty - s.quality_hold_qty) AS available_qty,
    (s.on_hand_qty - s.reserved_qty - s.quality_hold_qty 
     + s.in_transit_qty + s.in_process_qty) AS projected_available_qty,
    ss.safety_qty,
    CASE WHEN (s.on_hand_qty - s.reserved_qty - s.quality_hold_qty) 
              < ISNULL(ss.safety_qty, 0) 
         THEN 1 ELSE 0 END AS below_safety_flag
FROM inv_stock s
INNER JOIN bas_material m ON s.material_id = m.material_id
LEFT JOIN inv_safety_stock ss ON s.material_id = ss.material_id 
    AND s.warehouse_code = ss.warehouse_code;
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
