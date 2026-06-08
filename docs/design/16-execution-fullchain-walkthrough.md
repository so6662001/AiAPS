# AiAPS — 生产执行全链路数据走查与缺失表补充

> 版本：1.0 | 最后更新：2026-03-17
>
> 以"热卷2.75×1500 开平分剪→制管"两道工序为实例，完整走查上料→生产→报工→
> 收货→入库→出库→订单进度→工序进度的全链路数据，并补充设计中缺失的表结构。

---

## 0. 业务场景

```
客户订单: 方管 125×125×2.75, 9m定尺, Q235B, 20T

生产过程(两道工序):
  
  工序1: 开平/分剪
    原料: 热轧卷 2.75×1500×C Q235B 鞍钢, 1卷约22T
    操作: 分剪成 500mm 和 1000mm 两个窄卷
    产出: 带钢 2.75×500 (约7.3T) + 带钢 2.75×1000 (约14.5T)
    流向: 500的卷 → 制管(工序2)
          1000的卷 → 入半成品库(另有用途)

  工序2: 制管
    原料: 带钢 2.75×500 Q235B (来自工序1的产出)
    操作: 成型→焊接→定径→切割9m定尺
    产出: 方管 125×125×2.75×9000 Q235B (约7.0T, 约64根)
    流向: → 成品入库 → 发货给客户

  方管 125×125×2.75 周长计算:
    周长 ≈ 4×125 - 8×R + 2πR ≈ 500 - 44 + 34.6 ≈ 490.6mm
    带钢宽度 ≈ 491 + 成型余量 ≈ 497mm → 用500mm带钢(略宽, 切边)
```

---

## 1. 设计缺陷诊断与补充

### 1.1 缺失的表

```
检测结果: 现有设计缺失以下关键表, 无法支撑完整的生产执行流程:

┌──┬──────────────────┬──────────────────────────────────────────┐
│# │缺失的表           │影响                                      │
├──┼──────────────────┼──────────────────────────────────────────┤
│1 │上料/领料记录      │没有正式的"领料出库→产线"记录              │
│  │(prd_material_issue)│无法追踪谁领了什么料到哪条产线              │
├──┼──────────────────┼──────────────────────────────────────────┤
│2 │库存出入库流水     │inv_stock只有余额, 没有事务流水              │
│  │(inv_transaction)  │无法查"这卷钢什么时候出的库, 谁领的"        │
│  │                  │也无法做库存变动审计                        │
├──┼──────────────────┼──────────────────────────────────────────┤
│3 │生产入库单         │产品生产完成后入库没有正式单据               │
│  │(inv_receipt)      │报工 ≠ 入库, 报工是车间操作, 入库是仓库操作  │
└──┴──────────────────┴──────────────────────────────────────────┘
```

### 1.2 缺失的字段

```
┌──┬──────────────────────┬──────────────────────────────────────┐
│# │表/字段               │问题                                  │
├──┼──────────────────────┼──────────────────────────────────────┤
│1 │aps_schedule_oper     │缺少重量/材质/产地字段                 │
│  │(排产工序计划)         │无法在工序级别跟踪重量和材质            │
├──┼──────────────────────┼──────────────────────────────────────┤
│2 │prd_report            │缺少重量字段和材质/产地                │
│  │(生产报工)             │报工只记数量不记重量, 钢铁行业不适用    │
├──┼──────────────────────┼──────────────────────────────────────┤
│3 │aps_schedule_oper     │缺少产出物料ID和流向                   │
│  │                      │多工序串联时无法标识每道工序的产出是什么 │
└──┴──────────────────────┴──────────────────────────────────────┘
```

---

## 2. 补充表设计

### 2.1 上料/领料记录 (prd_material_issue)

```sql
CREATE TABLE prd_material_issue (
    issue_id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    issue_no            VARCHAR(30)   NOT NULL,     -- 领料单号
    issue_type          VARCHAR(20)   NOT NULL,     -- NORMAL=正常领料 REPLENISH=补料
                                                    -- SWAP=换料后新领 RETURN=退料
    schedule_id         BIGINT        NOT NULL,     -- 关联排产单
    sched_oper_id       BIGINT        NULL,         -- 关联工序(可选)
    wc_id               BIGINT        NOT NULL,     -- 目标工作中心/产线
    
    -- ═══ 领用物料信息 ═══
    material_id         BIGINT        NOT NULL,     -- 物料
    grade_code          VARCHAR(30)   NOT NULL,     -- 材质
    origin_code         VARCHAR(30)   NULL,         -- 产地
    stock_id            BIGINT        NOT NULL,     -- 库存批次(精确到卷号)
    coil_no             VARCHAR(30)   NULL,         -- 卷号
    batch_no            VARCHAR(60)   NULL,         -- 批次号
    
    -- ═══ 数量与重量 ═══
    issue_qty           DECIMAL(18,3) NOT NULL,     -- 领料数量
    issue_weight        DECIMAL(18,3) NOT NULL,     -- 领料重量(吨)
    
    -- ═══ 仓库信息 ═══
    warehouse_code      VARCHAR(30)   NOT NULL,     -- 出库仓库
    location_code       VARCHAR(30)   NULL,         -- 出库库位
    
    -- ═══ 状态 ═══
    issue_status        VARCHAR(10)   NOT NULL DEFAULT 'REQUESTED',
                                                    -- REQUESTED=已申请 APPROVED=已审批
                                                    -- PICKED=已拣料 ISSUED=已出库
                                                    -- DELIVERED=已送达产线 RETURNED=已退回
    
    requested_by        VARCHAR(50)   NOT NULL,     -- 申请人
    requested_time      DATETIME      NOT NULL DEFAULT GETDATE(),
    issued_by           VARCHAR(50)   NULL,         -- 出库操作人(仓库)
    issued_time         DATETIME      NULL,         -- 出库时间
    received_by         VARCHAR(50)   NULL,         -- 产线接收人
    received_time       DATETIME      NULL,         -- 接收时间
    
    remark              NVARCHAR(200) NULL,
    CONSTRAINT UK_issue_no UNIQUE (issue_no),
    CONSTRAINT FK_issue_schedule FOREIGN KEY (schedule_id) 
        REFERENCES aps_schedule(schedule_id)
);

CREATE INDEX IX_issue_schedule ON prd_material_issue(schedule_id);
CREATE INDEX IX_issue_stock ON prd_material_issue(stock_id);
CREATE INDEX IX_issue_time ON prd_material_issue(issued_time);
```

### 2.2 库存出入库流水 (inv_transaction)

```sql
CREATE TABLE inv_transaction (
    txn_id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    txn_no              VARCHAR(30)   NOT NULL,     -- 流水号
    txn_type            VARCHAR(20)   NOT NULL,     -- PURCHASE_IN=采购入库
                                                    -- PRODUCE_IN=生产入库
                                                    -- SLIT_IN=分剪入库
                                                    -- ISSUE_OUT=领料出库
                                                    -- SHIP_OUT=发货出库
                                                    -- TRANSFER=调拨
                                                    -- ADJUST=盘点调整
                                                    -- SCRAP_OUT=报废出库
                                                    -- RETURN_IN=退料入库
    txn_direction       VARCHAR(3)    NOT NULL,     -- IN=入库 OUT=出库
    
    -- ═══ 物料信息 ═══
    material_id         BIGINT        NOT NULL,
    grade_code          VARCHAR(30)   NOT NULL,
    origin_code         VARCHAR(30)   NULL,
    stock_id            BIGINT        NULL,         -- 关联库存批次
    coil_no             VARCHAR(30)   NULL,
    batch_no            VARCHAR(60)   NULL,
    
    -- ═══ 数量与重量 ═══
    txn_qty             DECIMAL(18,3) NOT NULL,     -- 交易数量(正数)
    txn_weight          DECIMAL(18,3) NOT NULL,     -- 交易重量(吨, 正数)
    
    -- ═══ 仓库 ═══
    warehouse_code      VARCHAR(30)   NOT NULL,
    location_code       VARCHAR(30)   NULL,
    -- 调拨时的目标仓库
    to_warehouse_code   VARCHAR(30)   NULL,
    to_location_code    VARCHAR(30)   NULL,
    
    -- ═══ 关联单据 ═══
    source_doc_type     VARCHAR(20)   NULL,         -- SCHEDULE=排产 ISSUE=领料 RECEIPT=入库
                                                    -- PURCHASE=采购 SHIP=发货 NESTING=套裁
    source_doc_id       BIGINT        NULL,         -- 关联单据ID
    source_doc_no       VARCHAR(30)   NULL,         -- 关联单据号
    
    -- ═══ 库存变动前后 ═══
    before_qty          DECIMAL(18,3) NULL,         -- 变动前数量
    after_qty           DECIMAL(18,3) NULL,         -- 变动后数量
    before_weight       DECIMAL(18,3) NULL,         -- 变动前重量
    after_weight        DECIMAL(18,3) NULL,         -- 变动后重量
    
    txn_time            DATETIME      NOT NULL DEFAULT GETDATE(),
    operated_by         VARCHAR(50)   NOT NULL,
    remark              NVARCHAR(200) NULL,
    CONSTRAINT UK_txn_no UNIQUE (txn_no)
);

CREATE INDEX IX_txn_type ON inv_transaction(txn_type);
CREATE INDEX IX_txn_material ON inv_transaction(material_id);
CREATE INDEX IX_txn_stock ON inv_transaction(stock_id);
CREATE INDEX IX_txn_time ON inv_transaction(txn_time);
CREATE INDEX IX_txn_source ON inv_transaction(source_doc_type, source_doc_id);
```

### 2.3 生产入库单 (inv_receipt)

```sql
CREATE TABLE inv_receipt (
    receipt_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    receipt_no          VARCHAR(30)   NOT NULL,
    receipt_type        VARCHAR(20)   NOT NULL,     -- PRODUCE=生产入库 SLIT=分剪入库
                                                    -- PURCHASE=采购入库 RETURN=退货入库
    
    -- ═══ 来源 ═══
    schedule_id         BIGINT        NULL,         -- 来源排产单
    sched_oper_id       BIGINT        NULL,         -- 来源工序
    purchase_order_no   VARCHAR(30)   NULL,         -- 来源采购单
    
    -- ═══ 入库物料 ═══
    material_id         BIGINT        NOT NULL,
    grade_code          VARCHAR(30)   NOT NULL,
    origin_code         VARCHAR(30)   NULL,
    
    -- ═══ 数量与重量 ═══
    receipt_qty         DECIMAL(18,3) NOT NULL,     -- 入库数量
    receipt_weight      DECIMAL(18,3) NOT NULL,     -- 入库重量(吨)
    theory_weight       DECIMAL(18,3) NULL,         -- 理论重量
    actual_weight       DECIMAL(18,3) NULL,         -- 过磅重量
    
    -- ═══ 质检 ═══
    qc_status           VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
                                                    -- PENDING=待检 PASSED=合格
                                                    -- FAILED=不合格 WAIVED=免检
    qc_by               VARCHAR(50)   NULL,
    qc_time             DATETIME      NULL,
    
    -- ═══ 入库仓库 ═══
    warehouse_code      VARCHAR(30)   NOT NULL,
    location_code       VARCHAR(30)   NULL,
    target_stock_id     BIGINT        NULL,         -- 入库后的库存批次ID
    coil_no             VARCHAR(30)   NULL,         -- 入库卷号/批号
    
    -- ═══ 状态 ═══
    receipt_status      VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
                                                    -- PENDING=待入库 QC=质检中
                                                    -- RECEIVED=已入库 REJECTED=拒收
    
    received_by         VARCHAR(50)   NULL,
    received_time       DATETIME      NULL,
    created_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    remark              NVARCHAR(200) NULL,
    CONSTRAINT UK_receipt_no UNIQUE (receipt_no)
);

CREATE INDEX IX_receipt_schedule ON inv_receipt(schedule_id);
CREATE INDEX IX_receipt_status ON inv_receipt(receipt_status);
```

### 2.4 aps_schedule_oper 补充字段

```sql
-- 排产工序计划增加重量/材质/产出物料/流向(V3.0补充)
ALTER TABLE aps_schedule_oper ADD (
    -- ═══ 重量 ═══
    planned_weight      DECIMAL(18,3) NULL,         -- 计划重量(吨)
    completed_weight    DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 完成重量
    scrap_weight        DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 报废重量
    input_weight        DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 投入原料重量
    
    -- ═══ 材质/产地 ═══
    grade_code          VARCHAR(30)   NULL,
    origin_code         VARCHAR(30)   NULL,
    
    -- ═══ 产出物料(工序可能改变物料形态) ═══
    output_material_id  BIGINT        NULL,         -- 本工序产出物料
                                                    -- 如: 分剪工序投入钢卷, 产出带钢
    input_material_id   BIGINT        NULL,         -- 本工序投入物料
    
    -- ═══ 工序流向 ═══
    output_flow_type    VARCHAR(20)   NULL,         -- NEXT_OPER/STOCK/CUSTOMER
    output_flow_desc    NVARCHAR(100) NULL,
    next_sched_oper_id  BIGINT        NULL          -- 下一工序ID(工序间关联)
);
```

### 2.5 prd_report 补充字段

```sql
-- 报工表增加重量和材质(V3.0补充)
ALTER TABLE prd_report ADD (
    -- ═══ 重量(钢铁核心) ═══
    report_weight       DECIMAL(18,3) NULL,         -- 报工重量(吨)
    good_weight         DECIMAL(18,3) NULL,         -- 合格重量(吨)
    scrap_weight        DECIMAL(18,3) NULL,         -- 废品重量(吨)
    input_weight        DECIMAL(18,3) NULL,         -- 本次投入原料重量
    
    -- ═══ 材质/产地(实际生产使用的) ═══
    grade_code          VARCHAR(30)   NULL,
    origin_code         VARCHAR(30)   NULL,
    coil_no             VARCHAR(30)   NULL,         -- 使用的卷号
    
    -- ═══ 产出追溯 ═══
    output_batch_no     VARCHAR(60)   NULL,         -- 产出批次号
    output_coil_no      VARCHAR(30)   NULL          -- 产出卷号(分剪产出时)
);
```

---

## 3. 完整数据走查

### 3.1 基础数据

```
涉及物料 (bas_material):
┌──────┬───────────────┬──────────┬──────┬──────┬──────┬──────┐
│mat_id│material_code  │spec_desc │categ │thick │width │unit  │
├──────┼───────────────┼──────────┼──────┼──────┼──────┼──────┤
│ 4001 │COIL-2.75×1500 │2.75×1500 │COIL  │2.750 │1500  │T     │
│ 4101 │STRIP-2.75×500 │2.75×500  │STRIP │2.750 │500   │T     │
│ 4102 │STRIP-2.75×1000│2.75×1000 │STRIP │2.750 │1000  │T     │
│ 4201 │RPIPE-125×125  │125×125   │RECT  │2.750 │—    │T     │
│      │×2.75×9000     │×2.75×9000│_PIPE │      │      │      │
└──────┴───────────────┴──────────┴──────┴──────┴──────┴──────┘
```

### 3.2 需求

```
dem_demand_head:
  demand_id=1001, demand_no='DEM-2026-0080', demand_source='MTO'
  customer_code='CUST-X', customer_name='XX钢构'

dem_demand_line:
┌───────┬──────┬──────┬──────┬──────┬──────┬──────────┐
│line_id│mat_id│grade │origin│req_  │req_  │req_date  │
│       │      │_code │_code │qty   │weight│          │
├───────┼──────┼──────┼──────┼──────┼──────┼──────────┤
│ 2010  │4201  │Q235B │NULL  │64.000│20.000│2026-04-01│
│       │方管  │      │(不限)│(64根)│(20T) │          │
└───────┴──────┴──────┴──────┴──────┴──────┴──────────┘
```

### 3.3 MRP计划订单

```
mrp_plan_order:
┌──────┬────────┬──────┬──────┬──────┬──────┬──────┬──────┬─────────────────────┐
│po_id │po_no   │mat_id│grade │type  │qty   │weight│LLC   │说明                 │
├──────┼────────┼──────┼──────┼──────┼──────┼──────┼──────┼─────────────────────┤
│3010  │PO-0201 │4201  │Q235B │MFG   │64.000│20.000│ 0    │产出: 方管125×125    │
│      │        │方管  │      │      │(64根)│      │      │                     │
├──────┼────────┼──────┼──────┼──────┼──────┼──────┼──────┼─────────────────────┤
│3011  │PO-0202 │4101  │Q235B │MFG   │1.000 │7.300 │ 1    │中间: 带钢500        │
│      │        │带钢  │      │      │(1卷) │      │      │品类BOM展开          │
│      │        │500   │      │      │      │      │      │is_category_bom=1    │
├──────┼────────┼──────┼──────┼──────┼──────┼──────┼──────┼─────────────────────┤
│3012  │PO-0203 │4001  │Q235B │MFG   │1.000 │22.000│ 2    │原料: 热轧卷1500     │
│      │        │热卷  │      │      │(1卷) │      │      │品类BOM展开          │
│      │        │1500  │      │      │      │      │      │match: stock_id=8010 │
│      │        │      │      │      │      │      │      │HRC-2026-0200        │
└──────┴────────┴──────┴──────┴──────┴──────┴──────┴──────┴─────────────────────┘

注意:
  方管(LLC=0) → 带钢500(LLC=1) → 热轧卷1500(LLC=2)
  每条都带 grade_code=Q235B 和 weight
```

### 3.4 套裁方案 + 排产

```
=== 排产1: 分剪 (aps_schedule) ===
┌────────────────────┬───────────────────────────────────────────────────┐
│字段                │值                                                 │
├────────────────────┼───────────────────────────────────────────────────┤
│schedule_id         │ 9010                                              │
│schedule_no         │ SCH-SLIT-0200                                     │
│material_id         │ 4001 (热轧卷2.75×1500)                           │
│demand_grade_code   │ Q235B                                             │
│actual_grade_code   │ Q235B                                             │
│actual_origin_code  │ 鞍钢                                              │
│output_grade_code   │ Q235B                                             │
│output_origin_code  │ 鞍钢                                              │
│planned_qty         │ 1.000 (1次)                                       │
│planned_weight      │ 22.000 (投入母卷重量)                              │
│input_weight        │ 22.000                                            │
│schedule_start      │ 2026-03-22 08:00                                  │
│schedule_end        │ 2026-03-22 10:30                                  │
│raw_stock_id        │ 8010                                              │
│raw_coil_no         │ HRC-2026-0200                                     │
│schedule_phase      │ PREP (备料阶段)                                   │
│is_multi_output     │ 1 (一分多)                                        │
│nesting_plan_id     │ 7010                                              │
│output_flow_type    │ NEXT_SCHEDULE                                     │
│output_flow_desc    │ → 带钢500→制管 + 带钢1000→半成品库               │
│schedule_status     │ RELEASED                                          │
│customer_code       │ CUST-X                                            │
│source_demand_no    │ DEM-2026-0080                                     │
└────────────────────┴───────────────────────────────────────────────────┘

=== 排产1的工序 (aps_schedule_oper) ===
┌──────────┬──────┬──────────┬──────┬──────┬──────┬──────┬──────────────┐
│oper_id   │oper  │oper_name │wc_id │plan_ │plan_ │grade │output_flow   │
│          │_no   │          │      │qty   │weight│_code │              │
├──────────┼──────┼──────────┼──────┼──────┼──────┼──────┼──────────────┤
│ 91001    │10    │上料开卷  │分剪线│1.000 │22.000│Q235B │NEXT_OPER     │
│          │      │          │      │(1卷) │      │      │→ 分剪        │
├──────────┼──────┼──────────┼──────┼──────┼──────┼──────┼──────────────┤
│ 91002    │20    │分剪(两刀)│分剪线│1.000 │21.800│Q235B │STOCK +       │
│          │      │          │      │(1次) │(产出)│      │NEXT_SCHEDULE │
└──────────┴──────┴──────────┴──────┴──────┴──────┴──────┴──────────────┘

=== 套裁方案 (aps_nesting_plan) ===
  nesting_id=7010, 母卷: 2.75×1500, 22T, Q235B 鞍钢, HRC-2026-0200
  slit_count=3 (分3条: 500+1000+余)

=== 套裁明细 (aps_nesting_detail) ===
┌────────┬────┬──────┬──────┬──────┬──────┬──────┬────────────────────┐
│det_id  │line│mat_id│width │count │单重  │总重  │type / 去向          │
├────────┼────┼──────┼──────┼──────┼──────┼──────┼────────────────────┤
│ 71001  │ 1  │4101  │500.0 │1     │7.260 │7.260 │PRODUCT → 制管      │
│        │    │带钢  │      │(1卷) │      │      │schedule_id=9020    │
│        │    │500   │      │      │      │      │demand_line_id=2010 │
├────────┼────┼──────┼──────┼──────┼──────┼──────┼────────────────────┤
│ 71002  │ 2  │4102  │1000.0│1     │14.520│14.520│PRODUCT → 半成品库  │
│        │    │带钢  │      │(1卷) │      │      │                    │
│        │    │1000  │      │      │      │      │                    │
├────────┼────┼──────┼──────┼──────┼──────┼──────┼────────────────────┤
│ 71003  │ 3  │NULL  │—    │—    │0.220 │0.220 │WASTE 边丝废料       │
└────────┴────┴──────┴──────┴──────┴──────┴──────┴────────────────────┘

=== 排产2: 制管 (aps_schedule) ===
┌────────────────────┬───────────────────────────────────────────────────┐
│字段                │值                                                 │
├────────────────────┼───────────────────────────────────────────────────┤
│schedule_id         │ 9020                                              │
│schedule_no         │ SCH-PIPE-0201                                     │
│material_id         │ 4201 (方管125×125×2.75×9000)                      │
│demand_grade_code   │ Q235B                                             │
│actual_grade_code   │ Q235B                                             │
│actual_origin_code  │ 鞍钢                                              │
│planned_qty         │ 64.000 (64根)                                     │
│planned_weight      │ 7.000 (产出目标)                                  │
│input_weight        │ 7.260 (投入带钢)                                  │
│schedule_start      │ 2026-03-22 13:00                                  │
│schedule_end        │ 2026-03-22 18:00                                  │
│raw_material_id     │ 4101 (带钢2.75×500)                              │
│raw_coil_no         │ SLT-0200-1 (分剪产出的卷号)                       │
│raw_grade_code      │ Q235B                                             │
│raw_origin_code     │ 鞍钢                                              │
│mold_id             │ M-010 (方管125×125模具)                           │
│parent_schedule_id  │ 9010 (关联分剪排产)                               │
│schedule_phase      │ MFG (制造阶段)                                    │
│output_flow_type    │ FG_STOCK                                          │
│output_flow_desc    │ → 成品入库                                        │
│customer_code       │ CUST-X                                            │
│source_demand_no    │ DEM-2026-0080                                     │
└────────────────────┴───────────────────────────────────────────────────┘

=== 排产2的工序 (aps_schedule_oper) ===
┌──────────┬──────┬──────────┬──────┬──────┬──────┬──────┬──────────────┐
│oper_id   │oper  │oper_name │wc_id │plan_ │plan_ │grade │output_flow   │
│          │_no   │          │      │qty   │weight│_code │              │
├──────────┼──────┼──────────┼──────┼──────┼──────┼──────┼──────────────┤
│ 92001    │10    │上料穿带  │焊管  │1.000 │7.260 │Q235B │NEXT_OPER     │
│          │      │          │1线   │(1卷) │(投入)│      │→ 成型焊接    │
├──────────┼──────┼──────────┼──────┼──────┼──────┼──────┼──────────────┤
│ 92002    │20    │成型焊接  │焊管  │64.000│7.000 │Q235B │NEXT_OPER     │
│          │      │定径切割  │1线   │(64根)│(产出)│      │→ 质检下线    │
├──────────┼──────┼──────────┼──────┼──────┼──────┼──────┼──────────────┤
│ 92003    │30    │质检打包  │包装  │64.000│7.000 │Q235B │FG_STOCK      │
│          │      │          │区    │(64根)│      │      │→ 成品入库    │
└──────────┴──────┴──────────┴──────┴──────┴──────┴──────┴──────────────┘
```

---

## 4. 生产执行过程数据

### 4.1 工序1-分剪: 领料出库

```
=== 领料申请 (prd_material_issue) ===
┌────────────────────┬────────────────────────────────────────────┐
│字段                │值                                          │
├────────────────────┼────────────────────────────────────────────┤
│issue_id            │ 15001                                      │
│issue_no            │ ISS-2026-0301                              │
│issue_type          │ NORMAL (正常领料)                           │
│schedule_id         │ 9010 (分剪排产单)                           │
│sched_oper_id       │ 91001 (上料开卷工序)                        │
│wc_id               │ (分剪线)                                   │
│material_id         │ 4001 (热轧卷2.75×1500)                     │
│grade_code          │ Q235B                                      │
│origin_code         │ 鞍钢                                       │
│stock_id            │ 8010                                       │
│coil_no             │ HRC-2026-0200                              │
│issue_qty           │ 1.000 (1卷)                                │
│issue_weight        │ 22.000 (吨)                                │
│warehouse_code      │ RAW-A (原料仓)                              │
│issue_status        │ ISSUED (已出库)                              │
│requested_by        │ 张三 (分剪操作员)                           │
│issued_by           │ 李四 (仓管员)                               │
│issued_time         │ 2026-03-22 07:30                           │
└────────────────────┴────────────────────────────────────────────┘

=== 库存流水 (inv_transaction) ===
┌────────────────────┬────────────────────────────────────────────┐
│字段                │值                                          │
├────────────────────┼────────────────────────────────────────────┤
│txn_id              │ 50001                                      │
│txn_no              │ TXN-2026-030001                            │
│txn_type            │ ISSUE_OUT (领料出库)                        │
│txn_direction       │ OUT                                        │
│material_id         │ 4001                                       │
│grade_code          │ Q235B                                      │
│origin_code         │ 鞍钢                                       │
│stock_id            │ 8010                                       │
│coil_no             │ HRC-2026-0200                              │
│txn_qty             │ 1.000                                      │
│txn_weight          │ 22.000                                     │
│warehouse_code      │ RAW-A                                      │
│source_doc_type     │ ISSUE                                      │
│source_doc_id       │ 15001                                      │
│source_doc_no       │ ISS-2026-0301                              │
│before_weight       │ 22.000 (出库前)                             │
│after_weight        │ 0.000 (出库后)                              │
│operated_by         │ 李四                                       │
│txn_time            │ 2026-03-22 07:30                           │
└────────────────────┴────────────────────────────────────────────┘

=== 库存余额变化 (inv_stock) ===
  stock_id=8010: on_hand_qty 1→0, on_hand_weight 22.000→0.000
```

### 4.2 工序1-分剪: 报工

```
=== 分剪报工 (prd_report) ===
┌────────────────────┬────────────────────────────────────────────┐
│字段                │值                                          │
├────────────────────┼────────────────────────────────────────────┤
│report_id           │ 20001                                      │
│schedule_id         │ 9010                                       │
│sched_oper_id       │ 91002 (分剪工序)                            │
│report_qty          │ 1.000 (1次分剪)                             │
│good_qty            │ 2.000 (2条产品卷)                           │
│scrap_qty           │ 0 (废料另计)                                │
│report_weight       │ 21.780 (产出总重)                           │
│good_weight         │ 21.780                                     │
│scrap_weight        │ 0.220 (边丝)                                │
│input_weight        │ 22.000 (投入)                               │
│grade_code          │ Q235B                                      │
│origin_code         │ 鞍钢                                       │
│coil_no             │ HRC-2026-0200 (母卷)                        │
│output_batch_no     │ SLT-0200                                   │
│shift_code          │ SHIFT1                                     │
│operator_code       │ 张三                                       │
│start_time          │ 2026-03-22 08:00                           │
│end_time            │ 2026-03-22 10:15                           │
└────────────────────┴────────────────────────────────────────────┘

=== 排产单更新 (aps_schedule, id=9010) ===
  good_weight: 0 → 21.780
  scrap_weight: 0 → 0.220
  input_weight: 22.000
  yield_rate: 21.780/22.000 = 0.9900
  schedule_status: RELEASED → COMPLETED

=== 工序更新 (aps_schedule_oper, id=91002) ===
  completed_weight: 0 → 21.780
  scrap_weight: 0 → 0.220
  oper_status: PLANNED → COMPLETED
  actual_start: 2026-03-22 08:00
  actual_end: 2026-03-22 10:15
```

### 4.3 工序1-分剪: 产出入库

```
=== 入库单 (inv_receipt) — 带钢500 ===
┌────────────────────┬────────────────────────────────────────────┐
│字段                │值                                          │
├────────────────────┼────────────────────────────────────────────┤
│receipt_id          │ 25001                                      │
│receipt_no          │ RCV-2026-0501                              │
│receipt_type        │ SLIT (分剪入库)                             │
│schedule_id         │ 9010                                       │
│material_id         │ 4101 (带钢2.75×500)                        │
│grade_code          │ Q235B                                      │
│origin_code         │ 鞍钢                                       │
│receipt_qty         │ 1.000 (1卷)                                │
│receipt_weight      │ 7.260                                      │
│qc_status           │ WAIVED (免检, 工序间直接流转)               │
│warehouse_code      │ SEMI-A (半成品库) 或 WIP(在制)              │
│coil_no             │ SLT-0200-1                                 │
│target_stock_id     │ 8101 (新增库存记录)                         │
│receipt_status      │ RECEIVED                                   │
└────────────────────┴────────────────────────────────────────────┘

=== 入库单 (inv_receipt) — 带钢1000 ===
  receipt_no='RCV-2026-0502', mat_id=4102, weight=14.520
  coil_no='SLT-0200-2', target_stock_id=8102

=== 库存流水 (inv_transaction) — 带钢500入库 ===
  txn_type=SLIT_IN, direction=IN, mat_id=4101, grade=Q235B, origin=鞍钢
  txn_weight=7.260, coil_no=SLT-0200-1, warehouse=SEMI-A
  source_doc_type=RECEIPT, source_doc_no=RCV-2026-0501

=== 库存流水 (inv_transaction) — 带钢1000入库 ===
  txn_type=SLIT_IN, direction=IN, mat_id=4102, grade=Q235B, origin=鞍钢
  txn_weight=14.520, coil_no=SLT-0200-2, warehouse=SEMI-A

=== 新增库存 (inv_stock) ===
  stock_id=8101: mat=4101(带钢500), Q235B, 鞍钢, 7.260T, SLT-0200-1
  stock_id=8102: mat=4102(带钢1000), Q235B, 鞍钢, 14.520T, SLT-0200-2
```

### 4.4 工序2-制管: 领料(带钢500从半成品库出库)

```
=== 领料 (prd_material_issue) ===
  issue_no=ISS-2026-0302, schedule_id=9020(制管排产)
  material_id=4101(带钢500), grade=Q235B, origin=鞍钢
  stock_id=8101, coil_no=SLT-0200-1
  issue_weight=7.260, warehouse=SEMI-A

=== 库存流水 (inv_transaction) ===
  txn_type=ISSUE_OUT, mat_id=4101, txn_weight=7.260
  before_weight=7.260, after_weight=0.000

=== 库存变化 (inv_stock) ===
  stock_id=8101: on_hand_weight 7.260 → 0.000
```

### 4.5 工序2-制管: 报工

```
=== 制管报工 (prd_report) ===
  schedule_id=9020, sched_oper_id=92002(成型焊接)
  report_qty=64(64根), good_qty=62(62根合格), scrap_qty=2(2根废品)
  report_weight=7.000, good_weight=6.783, scrap_weight=0.217
  input_weight=7.260
  grade_code=Q235B, origin_code=鞍钢
  coil_no=SLT-0200-1 (使用的带钢卷号)

=== 排产单更新 (aps_schedule, id=9020) ===
  good_qty: 0 → 62
  good_weight: 0 → 6.783
  scrap_qty: 0 → 2
  scrap_weight: 0 → 0.217
  input_weight: 7.260
  yield_rate: 6.783/7.260 = 0.9342 (93.4%)
  schedule_status: RELEASED → COMPLETED

=== 各工序状态 (aps_schedule_oper) ===
  92001(上料穿带): COMPLETED, actual 08:00~08:20
  92002(成型焊接): COMPLETED, actual 08:20~17:30
    completed_weight=6.783, scrap_weight=0.217
  92003(质检打包): COMPLETED, actual 17:30~18:00
```

### 4.6 工序2-制管: 成品入库

```
=== 入库单 (inv_receipt) ===
  receipt_no=RCV-2026-0601, receipt_type=PRODUCE
  schedule_id=9020, material_id=4201(方管125×125)
  grade_code=Q235B, origin_code=鞍钢
  receipt_qty=62(62根), receipt_weight=6.783
  qc_status=PASSED (质检合格)
  warehouse_code=FG-A(成品库), coil_no=NULL(管材无卷号)
  target_stock_id=8201

=== 库存流水 (inv_transaction) ===
  txn_type=PRODUCE_IN, direction=IN
  mat_id=4201, grade=Q235B, origin=鞍钢
  txn_qty=62, txn_weight=6.783
  warehouse=FG-A
  source_doc_type=RECEIPT, source_doc_no=RCV-2026-0601

=== 新增成品库存 (inv_stock) ===
  stock_id=8201: mat=4201(方管125×125×2.75×9000)
  grade_code=Q235B, origin_code=鞍钢
  on_hand_qty=62.000(62根), on_hand_weight=6.783T
  warehouse_code=FG-A
```

### 4.7 需求进度更新

```
dem_demand_line (id=2010) 更新:
┌──────────────┬──────────┬──────────┐
│字段          │修改前    │修改后    │
├──────────────┼──────────┼──────────┤
│produced_qty  │ 0        │ 62.000   │
│produced_weight│ 0       │ 6.783    │
│allocated_qty │ 64.000   │ 64.000   │
│allocated_weight│ 20.000 │ 20.000   │
│line_status   │ OPEN     │ PARTIAL  │
└──────────────┴──────────┴──────────┘

进度: 62/64根 = 96.9% (按数量)
      6.783/20.000T = 33.9% (按重量)
      
说明: 20T需求需要约3卷带钢500, 本次只用了1卷(7.26T投入→6.78T产出)
      还需继续生产约13.2T, 需另外分剪或采购带钢
```

---

## 5. 进度查询视图

### 5.1 订单生产进度查询

```sql
-- 订单进度视图: 一条SQL查出订单的完整进度
CREATE VIEW v_order_progress AS
SELECT 
    dh.demand_no,
    dh.demand_source,
    dh.customer_name,
    dl.demand_line_id,
    dl.line_no,
    m.material_code,
    m.spec_desc,
    dl.grade_code,
    dl.origin_code,
    dl.required_qty,
    dl.required_weight,
    dl.produced_qty,
    dl.produced_weight,
    dl.delivered_qty,
    dl.delivered_weight,
    dl.required_date,
    dl.line_status,
    -- 进度百分比(按重量)
    CASE WHEN dl.required_weight > 0 
         THEN dl.produced_weight / dl.required_weight * 100 
         ELSE 0 END AS progress_pct,
    -- 是否延期
    CASE WHEN dl.line_status != 'COMPLETED' AND dl.required_date < GETDATE()
         THEN 1 ELSE 0 END AS is_overdue
FROM dem_demand_line dl
INNER JOIN dem_demand_head dh ON dl.demand_id = dh.demand_id
INNER JOIN bas_material m ON dl.material_id = m.material_id;
```

### 5.2 工序进度查询

```sql
-- 工序进度视图: 查询某个排产单每道工序的完成情况
CREATE VIEW v_oper_progress AS
SELECT 
    s.schedule_no,
    s.schedule_id,
    m.material_code,
    m.spec_desc,
    s.demand_grade_code AS grade_code,
    s.planned_weight AS schedule_weight,
    so.sched_oper_id,
    so.oper_no,
    so.oper_name,
    wc.wc_name,
    so.planned_weight AS oper_planned_weight,
    so.completed_weight AS oper_completed_weight,
    so.scrap_weight AS oper_scrap_weight,
    so.oper_status,
    so.oper_start AS plan_start,
    so.oper_end AS plan_end,
    so.actual_start,
    so.actual_end,
    -- 工序完成百分比
    CASE WHEN so.planned_weight > 0 
         THEN so.completed_weight / so.planned_weight * 100 
         ELSE 0 END AS oper_progress_pct,
    -- 工序是否延迟
    CASE WHEN so.oper_status NOT IN ('COMPLETED','CANCELLED') 
              AND so.oper_end < GETDATE()
         THEN 1 ELSE 0 END AS is_delayed,
    -- 流向
    so.output_flow_type,
    so.output_flow_desc
FROM aps_schedule_oper so
INNER JOIN aps_schedule s ON so.schedule_id = s.schedule_id
INNER JOIN bas_material m ON s.material_id = m.material_id
LEFT JOIN bas_work_center wc ON so.wc_id = wc.wc_id;
```

### 5.3 全链路追溯查询

```sql
-- 从订单追溯到每道工序的完整链路
-- 查询: DEM-2026-0080 的完整生产链路

-- 第1层: 订单进度
SELECT '订单' AS layer, demand_no, material_code, grade_code,
       required_weight, produced_weight, progress_pct
FROM v_order_progress WHERE demand_no = 'DEM-2026-0080';

-- 第2层: 关联排产单
SELECT '排产' AS layer, s.schedule_no, m.spec_desc, s.demand_grade_code,
       s.planned_weight, s.good_weight, s.yield_rate, s.schedule_status,
       s.output_flow_desc
FROM aps_schedule s
INNER JOIN bas_material m ON s.material_id = m.material_id
WHERE s.source_demand_no = 'DEM-2026-0080';

-- 第3层: 各排产单的工序进度
SELECT '工序' AS layer, schedule_no, oper_no, oper_name, wc_name,
       oper_planned_weight, oper_completed_weight, oper_progress_pct,
       oper_status, actual_start, actual_end
FROM v_oper_progress
WHERE schedule_id IN (SELECT schedule_id FROM aps_schedule 
                      WHERE source_demand_no = 'DEM-2026-0080')
ORDER BY schedule_id, oper_no;

-- 第4层: 出入库流水
SELECT '库存' AS layer, txn_no, txn_type, txn_direction,
       m.spec_desc, t.grade_code, t.origin_code,
       t.txn_weight, t.coil_no, t.txn_time
FROM inv_transaction t
INNER JOIN bas_material m ON t.material_id = m.material_id
WHERE t.source_doc_id IN (SELECT schedule_id FROM aps_schedule 
                          WHERE source_demand_no = 'DEM-2026-0080')
ORDER BY t.txn_time;
```
