# AiAPS — 物料追溯体系与条码管理设计

> 版本：1.0 | 最后更新：2026-03-17
>
> 覆盖：字段命名对齐、卡号/捆包号/条码三级标识体系、
> 单支管条码管理（品类可控）、全链路物料追溯。

---

## 0. 字段命名对齐

### 0.1 现有设计字段 → 实际系统字段映射

```
┌────────────────────────┬────────────────────────┬──────────────────────┐
│ 现有设计字段名          │ 实际系统字段名          │ 含义                 │
├────────────────────────┼────────────────────────┼──────────────────────┤
│ material_id            │ PrdtID                 │ 物料ID               │
│ material_code          │ PrdtNo                 │ 物料编号             │
│ material_name          │ PrdtName               │ 物料名称             │
│ grade_code             │ PATName                │ 材质名称             │
│ (grade_id)             │ PrdtAttsID             │ 材质ID               │
│ origin_code            │ PAName                 │ 产地名称             │
│ (origin_id)            │ PAID                   │ 产地ID               │
│ coil_no                │ ResNo                  │ 资源号(卷号)         │
│ batch_no               │ CardNo (卡号)          │ 批次号 = 卡号        │
│ (新增)                 │ BindNo                 │ 捆包号               │
│ remark                 │ CardRemark             │ 批次备注1            │
│ (新增)                 │ CardRemark2            │ 批次备注2            │
│ purchase_date          │ EnterDate              │ 入库日期             │
│ (新增)                 │ ItemBarcode            │ 单支管条码           │
└────────────────────────┴────────────────────────┴──────────────────────┘

后续设计中:
  · 数据库物理字段名使用实际系统字段名 (PrdtID, PATName, ResNo 等)
  · 文档中为便于理解仍使用描述性名称, 括号标注实际字段名
```

### 0.2 三级标识体系

```
卡号 (CardNo/批次号)
  │  含义: 一批次的唯一标识, 可能包含多个捆包
  │  粒度: 一次生产/入库的一个批次
  │  示例: CD-2026-03-0001
  │
  ├── 捆包号 (BindNo)
  │    │  含义: 每件货物(一捆/一包/一卷)的唯一标识
  │    │  粒度: 物理上一个独立包装单元
  │    │  示例: BN-2026-03-00001
  │    │  关系: 一个卡号下可能有多个捆包号
  │    │        如: 62根方管, 每6根一捆 → 约10个捆包 + 1个零散捆
  │    │
  │    └── 单支条码 (ItemBarcode)
  │         含义: 每支管/每张板的唯一标识
  │         粒度: 最小可追溯单元
  │         示例: IT-2026-03-00001-001
  │         关系: 一个捆包号下有多支管
  │               如: 一捆6根 → 6个条码
  │         控制: 可按品类参数控制是否启用
  │               管材=启用, 板材=不启用(按张追溯不经济)

层次关系:
  卡号 CD-2026-03-0001
  ├── 捆包 BN-001 (6根)
  │   ├── 条码 IT-001-001
  │   ├── 条码 IT-001-002
  │   ├── ...
  │   └── 条码 IT-001-006
  ├── 捆包 BN-002 (6根)
  │   └── ...
  ├── ...
  ├── 捆包 BN-010 (6根)
  └── 捆包 BN-011 (2根, 零散捆)
      ├── 条码 IT-011-001
      └── 条码 IT-011-002
```

---

## 1. 库存表字段增强 (inv_stock)

```sql
-- inv_stock 增加卡号/捆包/备注等实际业务字段
ALTER TABLE inv_stock ADD (
    -- ═══ 卡号 (批次号, 原 batch_no → 改用 CardNo) ═══
    CardNo              VARCHAR(60)   NULL,         -- 卡号 = 批次号

    -- ═══ 资源号 (原 coil_no → 改用 ResNo) ═══
    -- ResNo 已在设计中(对应原 coil_no)

    -- ═══ 捆包号 ═══
    BindNo              VARCHAR(60)   NULL,         -- 捆包号(每件唯一)

    -- ═══ 备注 ═══
    CardRemark          NVARCHAR(500) NULL,         -- 批次备注1
    CardRemark2         NVARCHAR(500) NULL,         -- 批次备注2

    -- ═══ 入库日期 ═══
    EnterDate           DATETIME      NULL          -- 入库日期
);

-- 字段映射注释:
-- material_id     → PrdtID
-- material_code   → PrdtNo (物料编号)
-- material_name   → PrdtName (物料名称)
-- grade_code      → PATName (材质名称)
-- origin_code     → PAName (产地名称)
-- coil_no         → ResNo (资源号/卷号)
-- batch_no        → CardNo (卡号/批次号)
```

### 1.1 库存粒度说明

```
库存记录的粒度选择:

  方式A: 按卡号(批次)粒度 — 一条记录 = 一个批次(可能含多个捆包)
    inv_stock: CardNo='CD-001', PrdtID=方管, qty=62根, weight=6.78T
    捆包明细通过 inv_stock_bind 子表关联
    
  方式B: 按捆包粒度 — 一条记录 = 一个捆包
    inv_stock: BindNo='BN-001', CardNo='CD-001', qty=6根, weight=0.66T
    inv_stock: BindNo='BN-002', CardNo='CD-001', qty=6根, weight=0.66T
    ...
    
  推荐: 方式A(按批次) + 捆包子表
  原因: 按捆包存储会导致库存记录数爆炸(62根÷6=11条), 
        但需要按捆包管理出库时, 通过子表支持
```

---

## 2. 捆包管理表 (inv_stock_bind)

```sql
-- 捆包明细: 一个库存批次(卡号)下的捆包清单
CREATE TABLE inv_stock_bind (
    bind_id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    stock_id            BIGINT        NOT NULL,     -- 关联库存记录
    CardNo              VARCHAR(60)   NOT NULL,     -- 所属卡号
    BindNo              VARCHAR(60)   NOT NULL,     -- 捆包号(唯一)
    
    -- ═══ 数量与重量 ═══
    bind_qty            DECIMAL(18,3) NOT NULL,     -- 捆包内数量(根/张)
    bind_weight         DECIMAL(18,3) NOT NULL,     -- 捆包重量(吨)
    theory_weight       DECIMAL(18,3) NULL,         -- 理论重量
    actual_weight       DECIMAL(18,3) NULL,         -- 过磅重量
    
    -- ═══ 规格(可能与物料标准有偏差) ═══
    product_length      DECIMAL(10,3) NULL,         -- 实际长度(mm)
    length_display      NVARCHAR(20)  NULL,         -- 长度展示
    
    -- ═══ 状态 ═══
    bind_status         VARCHAR(10)   NOT NULL DEFAULT 'IN_STOCK',
                                                    -- IN_STOCK=在库 RESERVED=已预留
                                                    -- SHIPPED=已发货 SCRAPPED=已报废
    
    -- ═══ 备注 ═══
    CardRemark          NVARCHAR(500) NULL,         -- 备注1
    CardRemark2         NVARCHAR(500) NULL,         -- 备注2
    
    -- ═══ 合同 ═══
    contract_no         VARCHAR(60)   NULL,         -- 合同号
    
    -- ═══ 追溯 ═══
    schedule_id         BIGINT        NULL,         -- 来源排产单
    report_id           BIGINT        NULL,         -- 来源报工单
    
    created_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    
    CONSTRAINT UK_BindNo UNIQUE (BindNo),
    CONSTRAINT FK_bind_stock FOREIGN KEY (stock_id) 
        REFERENCES inv_stock(stock_id)
);

CREATE INDEX IX_bind_stock ON inv_stock_bind(stock_id);
CREATE INDEX IX_bind_card ON inv_stock_bind(CardNo);
```

---

## 3. 单支管条码管理

### 3.1 品类级条码控制参数

```sql
-- 品类参数: 控制该品类是否启用单支条码管理
ALTER TABLE bas_material ADD (
    -- (实际加在品类配置表或物料表上)
    enable_item_barcode BIT NOT NULL DEFAULT 0       -- 是否启用单支条码
    -- 0=不启用(按捆包管理即可)
    -- 1=启用(每支管/每张板有独立条码)
);

-- 也可以放在品类配置表:
CREATE TABLE bas_category_config (
    config_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    category_code       VARCHAR(20)   NOT NULL,     -- 品类编码
    
    enable_item_barcode BIT NOT NULL DEFAULT 0,     -- 是否启用单支条码
    barcode_rule        VARCHAR(30)   NULL,         -- 条码生成规则
    barcode_prefix      VARCHAR(10)   NULL,         -- 条码前缀
    
    -- 其他品类级参数...
    enable_length_track BIT NOT NULL DEFAULT 1,     -- 是否追踪长度
    default_bind_qty    INT NULL,                   -- 默认每捆数量
    
    CONSTRAINT UK_category_config UNIQUE (category_code)
);

-- 示例:
-- RECT_PIPE (方管): enable_item_barcode=1, default_bind_qty=6
-- ROUND_PIPE(圆管): enable_item_barcode=1, default_bind_qty=6
-- PLATE     (板材): enable_item_barcode=0 (不启用, 按张/按重)
-- STRIP     (带钢): enable_item_barcode=0 (不启用, 按卷)
-- COIL      (钢卷): enable_item_barcode=0 (不启用, 按卷)
```

### 3.2 单支条码表 (inv_item_barcode)

```sql
CREATE TABLE inv_item_barcode (
    item_id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    ItemBarcode         VARCHAR(60)   NOT NULL,     -- 单支条码(全局唯一)
    
    -- ═══ 归属 ═══
    stock_id            BIGINT        NOT NULL,     -- 归属库存批次
    CardNo              VARCHAR(60)   NOT NULL,     -- 归属卡号
    BindNo              VARCHAR(60)   NOT NULL,     -- 归属捆包号
    bind_id             BIGINT        NOT NULL,     -- 归属捆包记录ID
    
    -- ═══ 物料信息(冗余, 方便扫码即查) ═══
    PrdtID              BIGINT        NOT NULL,     -- 物料ID
    PrdtNo              VARCHAR(60)   NULL,         -- 物料编号
    PrdtName            NVARCHAR(200) NULL,         -- 物料名称
    PATName             VARCHAR(30)   NULL,         -- 材质
    PAName              VARCHAR(30)   NULL,         -- 产地
    
    -- ═══ 单支规格 ═══
    item_length         DECIMAL(10,3) NULL,         -- 长度(mm)
    item_weight         DECIMAL(18,6) NULL,         -- 单支重量(吨)
    spec_display        NVARCHAR(200) NULL,         -- 完整规格展示
                                                    -- "方管 125×125×2.75 × 9m Q235B 鞍钢"
    
    -- ═══ 质量 ═══
    qc_status           VARCHAR(10)   NOT NULL DEFAULT 'PASSED',
                                                    -- PASSED/FAILED/PENDING
    defect_desc         NVARCHAR(200) NULL,         -- 缺陷描述(不合格时)
    
    -- ═══ 状态 ═══
    item_status         VARCHAR(10)   NOT NULL DEFAULT 'IN_STOCK',
                                                    -- IN_STOCK=在库 RESERVED=已预留
                                                    -- SHIPPED=已发货 SCRAPPED=已报废
                                                    -- REWORK=返工中
    
    -- ═══ 追溯 ═══
    schedule_id         BIGINT        NULL,         -- 生产排产单
    contract_no         VARCHAR(60)   NULL,         -- 合同号
    ResNo               VARCHAR(30)   NULL,         -- 原料资源号(卷号)
    
    -- ═══ 发货 ═══
    ship_doc_no         VARCHAR(30)   NULL,         -- 发货单号
    ship_time           DATETIME      NULL,         -- 发货时间
    
    created_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    
    CONSTRAINT UK_ItemBarcode UNIQUE (ItemBarcode)
);

CREATE INDEX IX_item_stock ON inv_item_barcode(stock_id);
CREATE INDEX IX_item_card ON inv_item_barcode(CardNo);
CREATE INDEX IX_item_bind ON inv_item_barcode(BindNo);
CREATE INDEX IX_item_prdt ON inv_item_barcode(PrdtID);
CREATE INDEX IX_item_contract ON inv_item_barcode(contract_no);
CREATE INDEX IX_item_schedule ON inv_item_barcode(schedule_id);
CREATE INDEX IX_item_status ON inv_item_barcode(item_status);
```

### 3.3 条码生成时机

```
条码生成在"报工+入库"环节:

  制管报工: 产出62根方管
    ↓
  入库时: 按每6根一捆打包
    ↓
  生成捆包号: BN-001~BN-011 (10捆×6根 + 1捆×2根)
    ↓
  如果 enable_item_barcode=true:
    每支管生成条码: IT-001-001 ~ IT-011-002 (共62个条码)
    打印条码标签贴在每支管上
    ↓
  如果 enable_item_barcode=false:
    只打印捆包标签(捆包号), 不逐支打码
```

---

## 4. 物料追溯体系

### 4.1 追溯链路模型

```
物料追溯 = 从任意一个节点(条码/捆包/卡号/排产单/合同号)出发,
          向上追溯原料来源, 向下追溯产出去向。

追溯链路:

  原料采购入库                   生产                        成品
  ──────────                   ────                        ────
  
  热轧卷(母卷)                  分剪                        带钢(子卷)
  ┌──────────┐                ┌──────────┐               ┌──────────┐
  │ResNo:    │  领料出库       │排产:     │  入库          │CardNo:   │
  │HRC-0200  │───────────────►│SCH-SLIT  │──────────────►│CD-0201   │
  │CardNo:   │  inv_txn       │-0200     │  inv_receipt  │BindNo:   │
  │CD-0200   │                │          │               │BN-S001   │
  │PATName:  │                │套裁方案   │               │ResNo:    │
  │Q235B     │                │NEST-0088 │               │SLT-0200-1│
  │PAName:   │                │          │               │(带钢500) │
  │鞍钢      │                └──────────┘               └─────┬────┘
  │合同:     │                                                 │
  │HT-0100  │                                                 │ 领料出库
  └──────────┘                                                 ▼
                                                          ┌──────────┐
                                               制管       │排产:     │
                                                          │SCH-PIPE  │
                                                          │-0201     │
                                                          └─────┬────┘
                                                                │
                                                    报工入库     │
                                                                ▼
                                                          ┌──────────┐
                                        成品               │CardNo:   │
                                                          │CD-0301   │
                                                          │PrdtName: │
                                                          │方管125×125│
                                                          │×2.75     │
                                                          │PATName:  │
                                                          │Q235B     │
                                                          │合同:     │
                                                          │HT-0100  │
                                                          ├──────────┤
                                                          │BindNo:   │
                                                          │BN-P001   │
                                                          │(6根一捆) │
                                                          │          │
                                                          │条码:     │
                                                          │IT-001-001│
                                                          │IT-001-002│
                                                          │...       │
                                                          │IT-001-006│
                                                          └──────────┘
```

### 4.2 追溯关联表 (trc_trace_link)

```sql
-- 追溯关联表: 记录每一层物料转换的上下游关系
-- 核心: 从任意节点出发, 可正向/反向遍历整条链
CREATE TABLE trc_trace_link (
    trace_id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    
    -- ═══ 上游(输入/来源) ═══
    source_type         VARCHAR(20)   NOT NULL,     -- STOCK=库存 SCHEDULE=排产产出
                                                    -- PURCHASE=采购 RETURN=退货
    source_stock_id     BIGINT        NULL,         -- 来源库存批次
    source_CardNo       VARCHAR(60)   NULL,         -- 来源卡号
    source_BindNo       VARCHAR(60)   NULL,         -- 来源捆包号
    source_ResNo        VARCHAR(30)   NULL,         -- 来源资源号(卷号)
    source_PrdtID       BIGINT        NULL,         -- 来源物料
    source_PATName      VARCHAR(30)   NULL,         -- 来源材质
    source_PAName       VARCHAR(30)   NULL,         -- 来源产地
    source_weight       DECIMAL(18,3) NULL,         -- 来源重量
    
    -- ═══ 转换过程 ═══
    process_type        VARCHAR(20)   NOT NULL,     -- SLIT=分剪 PIPE=制管 LEVEL=开平
                                                    -- GALV=镀锌 CUT=切割 BEND=折弯
                                                    -- WELD=焊接 PACK=打包
                                                    -- ISSUE=领料 RECEIPT=入库
    schedule_id         BIGINT        NULL,         -- 排产单
    schedule_no         VARCHAR(30)   NULL,
    nesting_id          BIGINT        NULL,         -- 套裁方案(分剪时)
    
    -- ═══ 下游(输出/产出) ═══
    target_type         VARCHAR(20)   NOT NULL,     -- STOCK=库存 SCHEDULE=下一排产
                                                    -- SHIP=发货 SCRAP=报废
    target_stock_id     BIGINT        NULL,         -- 产出库存批次
    target_CardNo       VARCHAR(60)   NULL,         -- 产出卡号
    target_BindNo       VARCHAR(60)   NULL,         -- 产出捆包号
    target_ResNo        VARCHAR(30)   NULL,         -- 产出资源号
    target_PrdtID       BIGINT        NULL,         -- 产出物料
    target_PATName      VARCHAR(30)   NULL,         -- 产出材质
    target_PAName       VARCHAR(30)   NULL,         -- 产出产地
    target_weight       DECIMAL(18,3) NULL,         -- 产出重量
    
    -- ═══ 上下文 ═══
    contract_no         VARCHAR(60)   NULL,         -- 合同号
    trace_time          DATETIME      NOT NULL DEFAULT GETDATE(),
    operated_by         VARCHAR(50)   NULL,
    remark              NVARCHAR(200) NULL
);

CREATE INDEX IX_trace_source_card ON trc_trace_link(source_CardNo);
CREATE INDEX IX_trace_source_res ON trc_trace_link(source_ResNo);
CREATE INDEX IX_trace_target_card ON trc_trace_link(target_CardNo);
CREATE INDEX IX_trace_target_bind ON trc_trace_link(target_BindNo);
CREATE INDEX IX_trace_schedule ON trc_trace_link(schedule_id);
CREATE INDEX IX_trace_contract ON trc_trace_link(contract_no);
```

### 4.3 追溯链路数据实例

```
以案例走查 trc_trace_link 的数据:

=== 第1环: 热轧卷领料(原料仓→分剪线) ===
┌───────────────────────────────────────────────────────────────┐
│ trace_id: 1                                                   │
│ source: STOCK, CardNo=CD-0200, ResNo=HRC-0200                │
│         PrdtID=热轧卷2.75×1500, PATName=Q235B, PAName=鞍钢   │
│         weight=22.000T                                        │
│ process: ISSUE (领料), schedule=SCH-SLIT-0200                 │
│ target: SCHEDULE, schedule_id=9010                            │
│ contract: HT-2026-0100                                        │
└───────────────────────────────────────────────────────────────┘

=== 第2环: 分剪(热轧卷→带钢500) ===
┌───────────────────────────────────────────────────────────────┐
│ trace_id: 2                                                   │
│ source: STOCK, CardNo=CD-0200, ResNo=HRC-0200                │
│         PrdtID=热轧卷2.75×1500, weight=22.000T               │
│ process: SLIT (分剪), schedule=SCH-SLIT-0200, nesting=7010   │
│ target: STOCK, CardNo=CD-0201, ResNo=SLT-0200-1              │
│         PrdtID=带钢2.75×500, weight=7.260T                   │
│ contract: HT-2026-0100                                        │
└───────────────────────────────────────────────────────────────┘

=== 第3环: 分剪(热轧卷→带钢1000) ===
┌───────────────────────────────────────────────────────────────┐
│ trace_id: 3                                                   │
│ source: STOCK, CardNo=CD-0200, ResNo=HRC-0200                │
│ process: SLIT, schedule=SCH-SLIT-0200, nesting=7010           │
│ target: STOCK, CardNo=CD-0202, ResNo=SLT-0200-2              │
│         PrdtID=带钢2.75×1000, weight=14.520T                 │
│ contract: HT-2026-0100                                        │
└───────────────────────────────────────────────────────────────┘

=== 第4环: 带钢500领料(半成品库→制管线) ===
┌───────────────────────────────────────────────────────────────┐
│ trace_id: 4                                                   │
│ source: STOCK, CardNo=CD-0201, ResNo=SLT-0200-1              │
│         PrdtID=带钢2.75×500, weight=7.260T                   │
│ process: ISSUE, schedule=SCH-PIPE-0201                        │
│ target: SCHEDULE, schedule_id=9020                            │
│ contract: HT-2026-0100                                        │
└───────────────────────────────────────────────────────────────┘

=== 第5环: 制管(带钢500→方管125×125×2.75) ===
┌───────────────────────────────────────────────────────────────┐
│ trace_id: 5                                                   │
│ source: STOCK, CardNo=CD-0201, ResNo=SLT-0200-1              │
│         PrdtID=带钢2.75×500, weight=7.260T                   │
│ process: PIPE (制管), schedule=SCH-PIPE-0201                  │
│ target: STOCK, CardNo=CD-0301, target_BindNo=BN-P001~P011    │
│         PrdtID=方管125×125×2.75, weight=6.783T, 62根          │
│ contract: HT-2026-0100                                        │
└───────────────────────────────────────────────────────────────┘
```

### 4.4 追溯查询

```java
/**
 * 物料追溯查询服务
 * 支持从任意节点(条码/捆包号/卡号/资源号/合同号)出发的正反向追溯
 */
public class TraceabilityService {
    
    /**
     * 追溯结果: 树形结构
     */
    public static class TraceNode {
        private String nodeType;        // RAW/SEMI/FG
        private String CardNo;
        private String BindNo;
        private String ResNo;
        private String ItemBarcode;
        private String PrdtName;        // 物料名称
        private String specDisplay;     // 完整规格
        private String PATName;         // 材质
        private String PAName;          // 产地
        private BigDecimal weight;
        private String contractNo;
        private String processType;     // 本节点经历的加工类型
        private String scheduleNo;
        private List<TraceNode> children;  // 下游节点
        private List<TraceNode> parents;   // 上游节点
    }
    
    /**
     * 正向追溯: 从原料追溯到成品
     * "这卷钢最终做成了什么产品, 卖给了谁?"
     */
    public TraceNode traceForward(String sourceCardNo) {
        // 查找以此卡号为 source 的所有 trace_link
        List<TraceLink> links = traceLinkMapper.selectBySourceCard(sourceCardNo);
        // 递归构建树...
    }
    
    /**
     * 反向追溯: 从成品追溯到原料
     * "这支管是用哪卷钢做的, 是谁生产的, 什么时候入的库?"
     */
    public TraceNode traceBackward(String targetCardNo) {
        List<TraceLink> links = traceLinkMapper.selectByTargetCard(targetCardNo);
        // 递归构建树...
    }
    
    /**
     * 按条码追溯: 扫一支管的条码, 追溯完整链路
     */
    public TraceNode traceByBarcode(String itemBarcode) {
        ItemBarcode item = itemBarcodeMapper.selectByBarcode(itemBarcode);
        return traceBackward(item.getCardNo());
    }
    
    /**
     * 按合同追溯: 这个合同的所有物料流转
     */
    public List<TraceNode> traceByContract(String contractNo) {
        List<TraceLink> links = traceLinkMapper.selectByContract(contractNo);
        // 构建完整链路图...
    }
}
```

### 4.5 追溯查询页面

```
┌──────────────────────────────────────────────────────────────────────────┐
│  物料追溯中心                                                            │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  查询方式: ● 扫条码  ○ 卡号  ○ 捆包号  ○ 资源号  ○ 合同号              │
│                                                                          │
│  扫码: [ IT-2026-03-00001-003      ] [🔍追溯]                           │
│                                                                          │
│  ══ 追溯结果 ══                                                          │
│                                                                          │
│  📍 当前产品:                                                            │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │  条码: IT-2026-03-00001-003                                       │ │
│  │  产品: 方管 125×125×2.75 × 9m Q235B 鞍钢                          │ │
│  │  捆包: BN-P001 (第3根/共6根) | 卡号: CD-0301                      │ │
│  │  重量: 0.109T (单支) | 合同: HT-2026-0100 (XX钢构)                │ │
│  │  备注1: 一级品 | 备注2: 03-22白班生产                               │ │
│  │  状态: 在库 | 仓库: 成品A | 入库: 2026-03-22                      │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  ↑ 反向追溯(原料来源):                                                   │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                                                                    │ │
│  │  [原料] 热轧卷 2.75×1500 Q235B 鞍钢                               │ │
│  │  卡号: CD-0200 | 资源号: HRC-2026-0200 | 重量: 22.000T             │ │
│  │  入库: 2026-03-18 | 供应商: 鞍钢集团 | 炉号: A2026-03-888         │ │
│  │  合同: HT-2026-0100                                               │ │
│  │         │                                                          │ │
│  │         │ [分剪] SCH-SLIT-0200 分剪线 03-22 08:00~10:15            │ │
│  │         │ 套裁: 500+1000, 成材率99.0%                              │ │
│  │         ▼                                                          │ │
│  │  [半成品] 带钢 2.75×500 Q235B 鞍钢                                 │ │
│  │  卡号: CD-0201 | 资源号: SLT-0200-1 | 重量: 7.260T                 │ │
│  │  合同: HT-2026-0100                                               │ │
│  │         │                                                          │ │
│  │         │ [制管] SCH-PIPE-0201 焊管1线 03-22 08:20~17:30           │ │
│  │         │ 模具: M-010 | 成材率: 93.4%                              │ │
│  │         ▼                                                          │ │
│  │  [成品] 方管 125×125×2.75 × 9m Q235B 鞍钢                         │ │
│  │  卡号: CD-0301 | 62根 / 6.783T                                     │ │
│  │  → 捆包 BN-P001 (6根) → 条码 IT-...-003 ← 你查的这支              │ │
│  │  合同: HT-2026-0100                                               │ │
│  │                                                                    │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  ↓ 正向追溯(后续去向):                                                   │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │  状态: 在库(未发货)                                                │ │
│  │  如已发货: 发货单 SHP-xxx → 客户 XX钢构 → 签收时间                 │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  [打印追溯报告]  [导出PDF]  [查看关联合同全部追溯]                        │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 5. API 接口

```
# ═══ 追溯查询 ═══
GET  /api/v1/trace/barcode/{itemBarcode}     # 按单支条码追溯
GET  /api/v1/trace/card/{cardNo}             # 按卡号追溯
GET  /api/v1/trace/bind/{bindNo}             # 按捆包号追溯
GET  /api/v1/trace/resource/{resNo}          # 按资源号追溯
GET  /api/v1/trace/contract/{contractNo}     # 按合同号追溯全链路
GET  /api/v1/trace/schedule/{scheduleId}     # 按排产单追溯上下游

# ═══ 捆包管理 ═══
GET  /api/v1/stock/{stockId}/binds            # 查询某库存的捆包清单
POST /api/v1/stock/bind                       # 创建捆包(打包时)
PUT  /api/v1/stock/bind/{bindNo}/status       # 更新捆包状态

# ═══ 条码管理 ═══
POST /api/v1/barcode/generate                 # 批量生成条码(打包入库时)
GET  /api/v1/barcode/{itemBarcode}            # 扫码查询
GET  /api/v1/barcode/bind/{bindNo}            # 查询某捆包的所有条码
PUT  /api/v1/barcode/{itemBarcode}/status      # 更新条码状态(发货/报废)
GET  /api/v1/barcode/category/{code}/config    # 查询品类条码配置
PUT  /api/v1/barcode/category/{code}/config    # 设置品类条码配置
```
