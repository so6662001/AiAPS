# AiAPS — 多客户需求合并套料设计

> 版本：1.0 | 最后更新：2026-03-17
>
> 解决：多个客户的开平/分剪/剪切需求合并为一张工单生产，最大化利用率、降低成本。
> 增强 08 文档中的套裁方案，从"单投入多产出"升级为"多需求合并→最优套料→多合同产出"。

---

## 0. 问题分析

### 0.1 当前系统的套裁 vs 用户需要的套料

```
当前系统(08文档的套裁):
  一个母卷 → 分成N条带钢(一分X)
  只解决"怎么切", 不解决"哪些需求可以合并到一起切"
  
  aps_nesting_detail.demand_line_id 只能关联1个需求行
  → 无法表达"这条带/这张板是给客户A的, 那条是给客户B的"
  → 实际上可以, 每行一个demand_line_id, 但缺少"自动合并需求"的逻辑

用户需要的套料:
  ┌─────────────────────────────────────────────────────────────────┐
  │  场景1: 开平合并套料                                             │
  │                                                                 │
  │  客户A: 开平 6.0×1500×6000 Q235B  15T  合同HT-001               │
  │  客户B: 开平 6.0×1500×4000 Q235B   8T  合同HT-002               │
  │  客户C: 开平 6.0×1500×8000 Q235B   5T  合同HT-003               │
  │                                                                 │
  │  合并: 三个客户都要 6.0厚 × 1500宽 × Q235B → 用同一卷钢卷开平     │
  │  一卷25T钢卷 → 按不同定尺长度切出各客户的板                       │
  │  节省: 只开一卷, 不用开三卷(省开卷/穿带/切头尾损耗)               │
  │                                                                 │
  │  产出: 6000mm板 → 客户A  (标记合同HT-001)                        │
  │       4000mm板 → 客户B  (标记合同HT-002)                        │
  │       8000mm板 → 客户C  (标记合同HT-003)                        │
  │       余料/短尺 → 入库备用                                      │
  └─────────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────────┐
  │  场景2: 剪切合并套料 (二维)                                      │
  │                                                                 │
  │  客户A: 剪切件 300×500×6.0  Q235B  20件  合同HT-004              │
  │  客户B: 剪切件 200×800×6.0  Q235B  15件  合同HT-005              │
  │  客户C: 剪切件 400×400×6.0  Q235B  10件  合同HT-006              │
  │                                                                 │
  │  合并: 都是6.0mm Q235B → 从一张 6.0×1500×6000 母板上切            │
  │  二维排版: 在1500×6000的板面上排列各种尺寸的矩形件                 │
  │  目标: 利用率最大化, 余料最小化                                   │
  │                                                                 │
  │  ┌─────────────────────────────────────────────┐                │
  │  │ 母板 1500×6000                               │                │
  │  │ ┌───────┐┌───────┐┌───────┐┌───────┐┌─────┐│                │
  │  │ │A:300  ││A:300  ││B:200  ││B:200  ││     ││                │
  │  │ │×500   ││×500   ││×800   ││×800   ││余料 ││                │
  │  │ ├───────┤├───────┤├───────┤├───────┤│     ││                │
  │  │ │C:400  ││A:300  ││B:200  ││C:400  ││     ││                │
  │  │ │×400   ││×500   ││×800   ││×400   ││     ││                │
  │  │ └───────┘└───────┘└───────┘└───────┘└─────┘│                │
  │  └─────────────────────────────────────────────┘                │
  └─────────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────────┐
  │  场景3: 分剪合并套料 (一维, 跨合同)                               │
  │                                                                 │
  │  客户A: 带钢 2.5×300 Q235B  10T  合同HT-007                     │
  │  客户B: 带钢 2.5×600 Q235B  15T  合同HT-008                     │
  │  内部:  带钢 2.5×400 Q235B   8T  (自用, 制管原料)                 │
  │                                                                 │
  │  合并: 一卷 2.5×1500 → 300+600+400=1300 + 边丝/锯缝              │
  │  三个需求方的带钢从同一卷母卷中切出                               │
  └─────────────────────────────────────────────────────────────────┘
```

---

## 1. 需求合并池

### 1.1 核心概念

```
套料的第一步: 将多个客户/合同的零散需求, 按可合并条件自动归组

合并条件(必须全部满足):
  · 同品类 (开平/分剪/剪切)
  · 同厚度 (6.0mm的需求不能与8.0mm合并)
  · 同材质 (Q235B不能与Q345B合并, 除非允许替代)
  · 同宽度 (开平: 母卷宽度相同; 剪切: 母板宽度相同)
  · 窜料控制通过 (如果某合同不允许窜料, 则单独组)

不要求相同:
  · 合同号 (不同合同可以合并, 这是核心价值!)
  · 客户 (不同客户可以合并)
  · 长度 (开平: 不同定尺可以从同一卷切; 剪切: 不同尺寸件可以从同一板切)
  · 产地 (只要客户都接受)
```

### 1.2 套料需求池表 (aps_nesting_pool)

```sql
-- 套料需求池: 收集所有可参与套料的待满足需求
CREATE TABLE aps_nesting_pool (
    pool_id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    pool_no             VARCHAR(30)   NOT NULL,
    
    -- ═══ 需求来源 ═══
    demand_line_id      BIGINT        NOT NULL,     -- 来源需求行
    plan_order_id       BIGINT        NULL,         -- 来源MRP计划订单
    contract_no         VARCHAR(60)   NULL,         -- 合同号
    customer_code       VARCHAR(30)   NULL,         -- 客户编码
    customer_name       NVARCHAR(100) NULL,
    
    -- ═══ 需求物料信息 ═══
    PrdtID              BIGINT        NOT NULL,     -- 物料ID
    category_code       VARCHAR(20)   NOT NULL,     -- 品类: PLATE/STRIP/CUT_PART
    PATName             VARCHAR(30)   NOT NULL,     -- 材质
    PAName              VARCHAR(30)   NULL,         -- 产地(NULL=不限)
    
    -- ═══ 需求规格 ═══
    thickness           DECIMAL(10,3) NOT NULL,     -- 厚度(合并分组键)
    width               DECIMAL(10,3) NULL,         -- 宽度(开平/分剪: 母卷宽度; 剪切: 件宽)
    cut_length          DECIMAL(10,3) NULL,         -- 定尺长度(开平) 或 件长(剪切)
    cut_width           DECIMAL(10,3) NULL,         -- 件宽(剪切二维套料时)
    
    -- ═══ 需求量 ═══
    required_qty        DECIMAL(18,3) NOT NULL,     -- 需求数量(张/件)
    required_weight     DECIMAL(18,3) NOT NULL,     -- 需求重量(T)
    fulfilled_qty       DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 已满足数量
    fulfilled_weight    DECIMAL(18,3) NOT NULL DEFAULT 0,  -- 已满足重量
    remaining_qty       DECIMAL(18,3) NOT NULL,     -- 剩余数量
    remaining_weight    DECIMAL(18,3) NOT NULL,     -- 剩余重量
    
    -- ═══ 合并控制 ═══
    allow_merge         BIT           NOT NULL DEFAULT 1,  -- 是否允许与其他需求合并
    merge_group_key     VARCHAR(100)  NULL,         -- 合并组键(系统自动计算)
                                                    -- 格式: 品类|厚度|材质|母卷宽度
    priority            INT           NOT NULL DEFAULT 50,  -- 优先级
    required_date       DATETIME      NULL,         -- 交期
    
    -- ═══ 套料分配结果 ═══
    nesting_id          BIGINT        NULL,         -- 分配到的套料方案
    nesting_detail_id   BIGINT        NULL,         -- 分配到的套料明细行
    
    -- ═══ 状态 ═══
    pool_status         VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
                                                    -- PENDING=待合并 GROUPED=已分组
                                                    -- NESTED=已套料 SCHEDULED=已排产
                                                    -- COMPLETED=已完成
    
    created_time        DATETIME      NOT NULL DEFAULT GETDATE(),
    CONSTRAINT UK_pool_no UNIQUE (pool_no)
);

CREATE INDEX IX_pool_group ON aps_nesting_pool(merge_group_key);
CREATE INDEX IX_pool_status ON aps_nesting_pool(pool_status);
CREATE INDEX IX_pool_category ON aps_nesting_pool(category_code, thickness, PATName);
```

### 1.3 合并分组键计算

```
merge_group_key 自动计算规则:

  开平需求:
    key = "LEVEL|" + thickness + "|" + PATName + "|" + 母卷宽度
    例: "LEVEL|6.0|Q235B|1500"
    → 所有 6.0mm Q235B 1500宽的开平需求归为一组
    → 不同定尺长度(6000/4000/8000)在同一组内

  分剪需求:
    key = "SLIT|" + thickness + "|" + PATName + "|" + 母卷宽度
    例: "SLIT|2.5|Q235B|1500"
    → 所有 2.5mm Q235B 的分剪需求归为一组
    → 不同子带宽度(300/600/400)在同一组内

  剪切需求(二维):
    key = "CUT|" + thickness + "|" + PATName
    例: "CUT|6.0|Q235B"
    → 所有 6.0mm Q235B 的剪切件归为一组
    → 不同尺寸件(300×500, 200×800)在同一组内
    → 从哪种规格的母板切, 由套料优化决定

  窜料限制:
    如果某条需求 allow_merge=false (合同不允许窜料)
    → key = 原key + "|" + contract_no
    → 独立成组, 不与其他合同合并
```

---

## 2. 增强套料方案

### 2.1 aps_nesting_plan 增强

```sql
-- 在原有 aps_nesting_plan 基础上增加多需求合并相关字段
ALTER TABLE aps_nesting_plan ADD (
    -- ═══ 合并模式 ═══
    nesting_mode        VARCHAR(20)   NOT NULL DEFAULT 'SINGLE',
                                                    -- SINGLE=单需求(原有模式)
                                                    -- MULTI_1D=多需求一维(开平/分剪)
                                                    -- MULTI_2D=多需求二维(剪切)
    merge_group_key     VARCHAR(100)  NULL,         -- 合并组键
    
    -- ═══ 涉及的合同/客户数量 ═══
    contract_count      INT           NOT NULL DEFAULT 1,  -- 涉及合同数
    customer_count      INT           NOT NULL DEFAULT 1,  -- 涉及客户数
    demand_count        INT           NOT NULL DEFAULT 1,  -- 涉及需求数
    
    -- ═══ 成本分摊 ═══
    total_cost          DECIMAL(18,2) NULL,         -- 总成本(原料+加工)
    cost_split_method   VARCHAR(20)   NULL          -- WEIGHT=按重量 QTY=按数量 AREA=按面积
);

-- aps_nesting_detail 增强: 每行关联到具体的合同和客户
ALTER TABLE aps_nesting_detail ADD (
    -- ═══ 多合同套料时, 每行可能属于不同合同 ═══
    customer_code       VARCHAR(30)   NULL,
    customer_name       NVARCHAR(100) NULL,
    pool_id             BIGINT        NULL,         -- 关联套料需求池
    
    -- ═══ 成本分摊 ═══
    allocated_cost      DECIMAL(18,2) NULL          -- 分摊到此行的成本
);
```

### 2.2 开平合并套料示例

```
场景: 三个客户的开平需求合并

  需求池:
  ┌──────┬──────────────┬──────┬──────┬──────┬──────┬──────┬──────────┐
  │pool  │客户/合同     │物料  │材质  │厚度  │宽度  │定尺  │需求量    │
  ├──────┼──────────────┼──────┼──────┼──────┼──────┼──────┼──────────┤
  │P-001 │客户A HT-001 │板材  │Q235B │6.0   │1500  │6000  │15T(约35张)│
  │P-002 │客户B HT-002 │板材  │Q235B │6.0   │1500  │4000  │ 8T(约28张)│
  │P-003 │客户C HT-003 │板材  │Q235B │6.0   │1500  │8000  │ 5T(约9张) │
  └──────┴──────────────┴──────┴──────┴──────┴──────┴──────┴──────────┘

  合并组键: "LEVEL|6.0|Q235B|1500" → 三条需求自动归为一组

  匹配母卷: 库存 6.0×1500 Q235B 钢卷 1卷(30T, 约420m)

  套料方案:
  ┌─────────────────────────────────────────────────────────────────┐
  │  套料方案: NEST-MULTI-001                                       │
  │  模式: MULTI_1D (多需求一维合并)                                │
  │  母卷: 6.0×1500 Q235B 鞍钢 30T (C-0500)                       │
  │  涉及: 3个合同, 3个客户                                         │
  │                                                                 │
  │  开平切割序列(按优先级/交期排序):                                │
  │                                                                 │
  │  ──→ 母卷展开方向 ──────────────────────────────────→           │
  │  │6000│6000│6000│...│6000│4000│4000│...│4000│8000│8000│...│     │
  │  │ A  │ A  │ A  │   │ A  │ B  │ B  │   │ B  │ C  │ C  │   │     │
  │  │第1张│第2张│第3张│   │第35│第1张│第2张│   │第28│第1张│第2张│   │     │
  │  │HT01│HT01│HT01│   │HT01│HT02│HT02│   │HT02│HT03│HT03│   │     │
  │                                                                 │
  │  产出汇总:                                                      │
  │  客户A(HT-001): 6000mm × 35张 = 14.85T  (15T需求, 差0.15T→余料)│
  │  客户B(HT-002): 4000mm × 28张 =  7.92T  (8T需求, 满足)         │
  │  客户C(HT-003): 8000mm ×  9张 =  5.09T  (5T需求, 满足)         │
  │  余料/短尺:                       2.14T  (入库备用)             │
  │  合计: 14.85 + 7.92 + 5.09 + 2.14 = 30T ✓                     │
  │  利用率: (30-2.14)/30 = 92.9%                                  │
  │                                                                 │
  │  成本分摊(按重量):                                               │
  │  客户A: 14.85/27.86 × 总成本 = 53.3%                           │
  │  客户B:  7.92/27.86 × 总成本 = 28.4%                           │
  │  客户C:  5.09/27.86 × 总成本 = 18.3%                           │
  └─────────────────────────────────────────────────────────────────┘
```

### 2.3 剪切二维套料示例

```
场景: 三个客户的剪切件从一张母板上切

  需求池:
  ┌──────┬──────┬──────────────┬──────┬──────┬──────┐
  │pool  │合同  │件尺寸        │材质  │厚度  │数量  │
  ├──────┼──────┼──────────────┼──────┼──────┼──────┤
  │P-010 │HT-04│300×500×6.0   │Q235B │6.0   │20件  │
  │P-011 │HT-05│200×800×6.0   │Q235B │6.0   │15件  │
  │P-012 │HT-06│400×400×6.0   │Q235B │6.0   │10件  │
  └──────┴──────┴──────────────┴──────┴──────┴──────┘

  合并组键: "CUT|6.0|Q235B" → 三条归一组

  母板: 6.0×1500×6000 Q235B (一张开平板, 约0.424T)

  二维排版算法结果:
  ┌───────────────────────────────────────────────────────────┐
  │  母板 1500mm × 6000mm                                     │
  │                                                           │
  │  ┌─────┐┌─────┐┌─────┐┌─────┐┌───────┐┌───────┐┌─────┐│
  │  │A    ││A    ││A    ││A    ││B      ││B      ││     ││
  │  │300  ││300  ││300  ││300  ││200    ││200    ││余料 ││
  │  │×500 ││×500 ││×500 ││×500 ││×800   ││×800   ││     ││
  │  ├─────┤├─────┤├─────┤├─────┤│       ││       ││     ││
  │  │C    ││C    ││A    ││A    ││       ││       ││     ││
  │  │400  ││400  ││300  ││300  ││       ││       ││     ││
  │  │×400 ││×400 ││×500 ││×500 ││       ││       ││     ││
  │  ├─────┤├─────┤├─────┤├─────┤├───────┤├───────┤│     ││
  │  │C    ││C    ││B    ││B    ││B      ││B      ││     ││
  │  │400  ││400  ││200  ││200  ││200    ││200    ││     ││
  │  │×400 ││×400 ││×800 ││×800 ││×800   ││×800   ││     ││
  │  └─────┘└─────┘└─────┘└─────┘└───────┘└───────┘└─────┘│
  │                                                           │
  │  本板产出: A×6件 + B×6件 + C×4件                          │
  │  利用率: 约82%                                            │
  │  需要: 约4张母板才能满足全部需求                            │
  └───────────────────────────────────────────────────────────┘
```

---

## 3. 二维套料排版表 (aps_nesting_2d_layout)

```sql
-- 二维套料排版: 每张母板上每个件的位置
CREATE TABLE aps_nesting_2d_layout (
    layout_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    nesting_detail_id   BIGINT        NOT NULL,     -- 所属套料明细行
    nesting_id          BIGINT        NOT NULL,     -- 所属套料方案
    sheet_no            INT           NOT NULL,     -- 第几张母板(一个方案可能需要多张)
    
    -- ═══ 件信息 ═══
    pool_id             BIGINT        NULL,         -- 来源需求池
    contract_no         VARCHAR(60)   NULL,         -- 合同号
    piece_width         DECIMAL(10,3) NOT NULL,     -- 件宽(mm)
    piece_length        DECIMAL(10,3) NOT NULL,     -- 件长(mm)
    piece_qty           INT           NOT NULL DEFAULT 1,
    
    -- ═══ 排版位置(坐标, 左下角为原点) ═══
    pos_x               DECIMAL(10,3) NOT NULL,     -- X坐标(mm)
    pos_y               DECIMAL(10,3) NOT NULL,     -- Y坐标(mm)
    is_rotated          BIT           NOT NULL DEFAULT 0,  -- 是否旋转90°
    
    -- ═══ 类型 ═══
    piece_type          VARCHAR(10)   NOT NULL DEFAULT 'PRODUCT',
                                                    -- PRODUCT/REMAINDER/WASTE
    remark              NVARCHAR(200) NULL,
    
    CONSTRAINT FK_layout_detail FOREIGN KEY (nesting_detail_id)
        REFERENCES aps_nesting_detail(nesting_detail_id)
);
```

---

## 4. 套料优化引擎

### 4.1 合并流程

```
┌──────────────────────────────────────────────────────────────────┐
│  多需求合并套料流程                                               │
│                                                                  │
│  Step 1: 收集需求 → 需求池 (aps_nesting_pool)                    │
│    · 从 MRP 计划订单中收集开平/分剪/剪切类需求                    │
│    · 或从需求单直接导入                                          │
│    · 每条需求带: 物料/厚度/宽度/长度/材质/合同/客户/量            │
│    · 检查窜料控制: allow_merge = 合同策略允许?                    │
│                                                                  │
│  Step 2: 自动分组                                                │
│    · 计算 merge_group_key = 品类|厚度|材质|母卷宽度               │
│    · 同 key 的需求归为一组                                       │
│    · allow_merge=false 的需求独立成组                            │
│                                                                  │
│  Step 3: 匹配母料                                                │
│    · 每个组: 查找厚度+材质匹配的母卷/母板库存                    │
│    · 计算: 本组总需求量, 需要几卷/几张母板                       │
│                                                                  │
│  Step 4: 套料优化                                                │
│    · 开平(一维): 按定尺长度排列, 贪心+尾料最小化                 │
│    · 分剪(一维): 按子带宽度组合, 一维装箱算法                    │
│    · 剪切(二维): 按件尺寸排版, 二维装箱算法(BL算法/条带法)       │
│    · 输出: 利用率/废料量/方案对比                                │
│                                                                  │
│  Step 5: 生成套料方案                                            │
│    · 创建 aps_nesting_plan (nesting_mode=MULTI_*)                │
│    · 创建 aps_nesting_detail (每行带 contract_no + pool_id)      │
│    · 剪切: 额外创建 aps_nesting_2d_layout (排版坐标)             │
│    · 更新需求池状态: PENDING → NESTED                            │
│                                                                  │
│  Step 6: 成本分摊                                                │
│    · 按 cost_split_method 计算每个合同的分摊成本                  │
│    · 记录到 aps_nesting_detail.allocated_cost                    │
│                                                                  │
│  Step 7: 转排产                                                  │
│    · 套料方案确认后 → 创建排产单                                 │
│    · 一个套料方案 = 一个排产单(一次生产操作)                     │
│    · 排产单产出: 多合同的产品(通过套料明细区分归属)               │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

### 4.2 开平合并套料算法

```java
/**
 * 开平合并套料优化器
 * 目标: 从一卷钢卷中按不同定尺长度切出多个客户的板材
 */
public class LevelingNestingOptimizer {
    
    /**
     * 开平套料需求
     */
    public static class LevelDemand {
        private Long poolId;
        private String contractNo;
        private String customerName;
        private BigDecimal cutLength;       // 定尺长度(mm)
        private int requiredSheets;         // 需求张数
        private int fulfilledSheets;        // 已满足张数
        private int priority;               // 交期优先级
    }
    
    /**
     * 为一卷钢卷制定开平套料方案
     * 
     * @param coilLengthM   钢卷长度(米)
     * @param demands       多个客户的开平需求
     * @param kerfLength    切割损耗(mm)
     * @return 切割序列
     */
    public List<CutPlan> optimize(BigDecimal coilLengthM,
                                   List<LevelDemand> demands,
                                   BigDecimal kerfLength) {
        
        BigDecimal coilLengthMm = coilLengthM.multiply(new BigDecimal("1000"));
        BigDecimal remaining = coilLengthMm;
        List<CutPlan> cuts = new ArrayList<>();
        
        // 按优先级排序(交期紧的先切)
        demands.sort(Comparator.comparingInt(LevelDemand::getPriority));
        
        // 贪心: 按优先级依次切出各客户的定尺板
        for (LevelDemand demand : demands) {
            int needed = demand.getRequiredSheets() - demand.getFulfilledSheets();
            BigDecimal cutLen = demand.getCutLength().add(kerfLength);
            
            while (needed > 0 && remaining.compareTo(cutLen) >= 0) {
                CutPlan cut = new CutPlan();
                cut.setPoolId(demand.getPoolId());
                cut.setContractNo(demand.getContractNo());
                cut.setCutLength(demand.getCutLength());
                cut.setType("PRODUCT");
                cuts.add(cut);
                
                remaining = remaining.subtract(cutLen);
                needed--;
                demand.setFulfilledSheets(demand.getFulfilledSheets() + 1);
            }
        }
        
        // 剩余长度 → 余料
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            CutPlan remainder = new CutPlan();
            remainder.setCutLength(remaining);
            remainder.setType(remaining.compareTo(new BigDecimal("500")) > 0
                ? "REMAINDER" : "WASTE");
            cuts.add(remainder);
        }
        
        return cuts;
    }
}
```

### 4.3 二维剪切套料算法

```java
/**
 * 二维剪切套料优化器
 * 算法: Bottom-Left (BL) 矩形装箱
 * 将不同尺寸的矩形件排列在母板上, 最大化利用率
 */
public class CuttingNestingOptimizer {
    
    /**
     * 二维套料需求
     */
    public static class CutPiece {
        private Long poolId;
        private String contractNo;
        private BigDecimal pieceWidth;      // 件宽
        private BigDecimal pieceLength;     // 件长
        private int requiredQty;            // 需求数量
        private int placedQty;              // 已排入数量
    }
    
    /**
     * 为一张母板排列剪切件
     * 
     * @param sheetWidth   母板宽度(mm)
     * @param sheetLength  母板长度(mm)
     * @param pieces       所有待排件
     * @return 排版结果(每件的坐标)
     */
    public List<PlacedPiece> optimize(BigDecimal sheetWidth,
                                       BigDecimal sheetLength,
                                       List<CutPiece> pieces) {
        
        List<PlacedPiece> placed = new ArrayList<>();
        
        // 按面积从大到小排序(大件优先)
        List<PieceInstance> instances = expandInstances(pieces);
        instances.sort(Comparator.comparing(
            (PieceInstance p) -> p.width.multiply(p.length)).reversed());
        
        // BL算法: 每个件尝试放在最左下的可用位置
        List<FreeRect> freeRects = new ArrayList<>();
        freeRects.add(new FreeRect(BigDecimal.ZERO, BigDecimal.ZERO,
                                    sheetWidth, sheetLength));
        
        for (PieceInstance inst : instances) {
            PlacedPiece result = tryPlace(inst, freeRects, sheetWidth, sheetLength);
            if (result != null) {
                placed.add(result);
                splitFreeRects(freeRects, result);
                inst.source.setPlacedQty(inst.source.getPlacedQty() + 1);
            }
            // 尝试旋转90度再放
            else {
                PieceInstance rotated = new PieceInstance(
                    inst.source, inst.length, inst.width, true);
                result = tryPlace(rotated, freeRects, sheetWidth, sheetLength);
                if (result != null) {
                    placed.add(result);
                    splitFreeRects(freeRects, result);
                    inst.source.setPlacedQty(inst.source.getPlacedQty() + 1);
                }
            }
        }
        
        return placed;
    }
    
    /**
     * 多张母板套料(直到所有需求满足)
     */
    public MultiSheetResult optimizeMultiSheet(
            BigDecimal sheetWidth, BigDecimal sheetLength,
            List<CutPiece> pieces) {
        
        MultiSheetResult result = new MultiSheetResult();
        int sheetNo = 1;
        
        while (hasUnfulfilled(pieces)) {
            List<PlacedPiece> sheetPlan = optimize(sheetWidth, sheetLength, pieces);
            if (sheetPlan.isEmpty()) break;
            
            result.addSheet(sheetNo, sheetPlan);
            sheetNo++;
        }
        
        result.calcUtilization();
        return result;
    }
}
```

---

## 5. 成本分摊

```
多合同共用一卷/一板时的成本分摊方式:

  ┌───────────────────────────────────────────────────────────────┐
  │  分摊方式                                                     │
  │                                                               │
  │  1. 按重量分摊 (cost_split_method='WEIGHT') — 推荐             │
  │     各合同分摊成本 = 总成本 × (该合同产出重量 / 总产出重量)     │
  │     适用: 开平、分剪                                          │
  │                                                               │
  │  2. 按数量分摊 (cost_split_method='QTY')                      │
  │     各合同分摊成本 = 总成本 × (该合同件数 / 总件数)            │
  │     适用: 剪切(件数明确)                                      │
  │                                                               │
  │  3. 按面积分摊 (cost_split_method='AREA')                     │
  │     各合同分摊成本 = 总成本 × (该合同面积 / 总面积)            │
  │     适用: 二维剪切(不同尺寸件)                                │
  │                                                               │
  │  总成本 = 原料成本 + 加工成本 + 废料处理成本                    │
  │  原料成本 = 母卷/母板重量 × 单价                               │
  │  废料成本由全体合同按比例共担                                   │
  └───────────────────────────────────────────────────────────────┘
```

---

## 6. 套料UI交互

```
┌──────────────────────────────────────────────────────────────────────────┐
│  多需求合并套料                                                          │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  模式: [开平合并▼]  材质: [Q235B▼]  厚度: [6.0▼]  [加载待合并需求]       │
│                                                                          │
│  ┌─── 需求池(可合并的需求) ──────────────────────────────────────────┐  │
│  │                                                                    │  │
│  │  合并组: LEVEL|6.0|Q235B|1500    共3条需求, 总需求28T               │  │
│  │                                                                    │  │
│  │  ┌──┬──────┬────────┬──────────┬──────┬──────┬──────┬──────────┐ │  │
│  │  │☑ │合同  │客户    │定尺长度  │材质  │需求量│交期  │优先级    │ │  │
│  │  ├──┼──────┼────────┼──────────┼──────┼──────┼──────┼──────────┤ │  │
│  │  │☑ │HT-01│客户A   │6000mm    │Q235B │15T   │04-01 │高        │ │  │
│  │  │☑ │HT-02│客户B   │4000mm    │Q235B │ 8T   │04-05 │中        │ │  │
│  │  │☑ │HT-03│客户C   │8000mm    │Q235B │ 5T   │04-10 │低        │ │  │
│  │  └──┴──────┴────────┴──────────┴──────┴──────┴──────┴──────────┘ │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ┌─── 可用母卷 ──────────────────────────────────────────────────────┐ │
│  │  ┌──┬──────────┬──────┬──────┬──────┬──────────────────────────┐  │ │
│  │  │● │卷号      │规格  │材质  │重量  │可切出                    │  │ │
│  │  ├──┼──────────┼──────┼──────┼──────┼──────────────────────────┤  │ │
│  │  │● │C-0500    │6.0   │Q235B │30T   │6m×35张+4m×28张+8m×9张   │  │ │
│  │  │  │          │×1500 │鞍钢  │      │余料2.14T 利用率92.9%     │  │ │
│  │  └──┴──────────┴──────┴──────┴──────┴──────────────────────────┘  │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  [🔄 自动优化套料方案]                                                   │
│                                                                          │
│  ┌─── 套料方案预览 ──────────────────────────────────────────────────┐ │
│  │                                                                    │ │
│  │  方案: NEST-MULTI-001 | 利用率: 92.9% | 3合同 | 3客户             │ │
│  │                                                                    │ │
│  │  切割顺序:                                                         │ │
│  │  ═══→ [6000×35张 HT-01] → [4000×28张 HT-02] → [8000×9张 HT-03]  │ │
│  │                                                                    │ │
│  │  产出分配:                                                         │ │
│  │  ┌──────┬────────┬──────┬──────┬──────┬──────────┐                │ │
│  │  │合同  │客户    │定尺  │张数  │重量  │成本分摊  │                │ │
│  │  ├──────┼────────┼──────┼──────┼──────┼──────────┤                │ │
│  │  │HT-01│客户A   │6000  │35张  │14.85T│¥62,370  │                │ │
│  │  │HT-02│客户B   │4000  │28张  │ 7.92T│¥33,264  │                │ │
│  │  │HT-03│客户C   │8000  │ 9张  │ 5.09T│¥21,378  │                │ │
│  │  │余料  │—      │短尺  │ —   │ 2.14T│(共担)   │                │ │
│  │  └──────┴────────┴──────┴──────┴──────┴──────────┘                │ │
│  │                                                                    │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  [确认套料方案 → 转排产]  [调整方案]  [取消]                             │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 7. API 接口

```
# ═══ 套料需求池 ═══
POST   /api/v1/nesting-pool                     # 手动添加到需求池
POST   /api/v1/nesting-pool/collect              # 从MRP计划自动收集
GET    /api/v1/nesting-pool                     # 需求池列表(按合并组分组)
GET    /api/v1/nesting-pool/groups               # 获取合并组列表

# ═══ 多需求合并套料 ═══
POST   /api/v1/nesting/multi-optimize            # 自动优化套料(传入组key或poolIds)
POST   /api/v1/nesting/multi-optimize/preview    # 套料方案预览(不保存)
POST   /api/v1/nesting/multi-create              # 创建多需求套料方案
GET    /api/v1/nesting/{id}/cost-split            # 查看成本分摊

# ═══ 二维排版 ═══
GET    /api/v1/nesting/{id}/2d-layout             # 获取二维排版数据(含坐标)
POST   /api/v1/nesting/2d-optimize                # 二维排版优化
```

---

---

## 8. MRP 自动化衔接：套料嵌入 MRP 展算流程

### 8.1 问题：套料不能是孤立的手动环节

```
当前的断裂点:

  MRP展算 → 生成计划订单(带钢300×10T / 带钢600×15T / 板材6000×15T ...)
                ↓
         (人工干预: 计划员手动把需求放到套料需求池, 手动触发优化)
                ↓
         套料方案 → 再手动转排产
                ↓
         排产

应该的自动化流程:

  MRP展算:
    Step 3g BOM展开时 → 遇到开平/分剪/剪切类品类需求
    → 自动识别为"套料候选"
    → 不直接生成独立计划订单
    → 改为汇入套料需求池
    → MRP结束后自动触发套料优化
    → 套料结果反馈回MRP（确定实际母卷/母板用量）
    → 生成合并后的计划订单和排产单
```

### 8.2 MRP 增强：套料感知的 BOM 展开

```
MRP 展算 Step 3g 的增强逻辑:

  对于每个计划订单(成品/半成品级):
    ├── 查BOM(离散或品类)
    ├── 展开得到子层原料需求
    │
    ├── 判断: 子层原料的获取方式是什么?
    │   ├── 采购(PUR) → 正常生成采购建议
    │   ├── 自制-直接(MFG) → 正常生成制造计划
    │   └── 自制-需套料(MFG+NESTING) → ★ 新增路径 ★
    │       │
    │       │ 识别条件(满足任一):
    │       │   · 品类=STRIP 且 原料品类=COIL → 纵剪(分剪)
    │       │   · 品类=PLATE 且 原料品类=COIL → 开平
    │       │   · 品类=CUT_PART → 剪切
    │       │
    │       └── 不直接生成子层计划订单
    │           → 将需求放入套料需求池(aps_nesting_pool)
    │           → 标记为 pool_status='MRP_GENERATED'
    │           → 继续展算下一个需求(不在此处展开母卷层)

  MRP Step 3 全部展算完成后:
    ├── 新增 Step 3.5: 套料自动合并优化
    │   ├── 从需求池取出所有 MRP_GENERATED 的条目
    │   ├── 按 merge_group_key 自动分组
    │   ├── 每组调用套料优化算法
    │   ├── 生成套料方案(aps_nesting_plan)
    │   ├── 根据套料方案 → 确定实际需要的母卷/母板数量和规格
    │   └── 为母卷/母板生成采购建议或库存匹配
    │
    └── Step 4: 输出
        ├── 制造计划订单(成品/半成品级, 正常)
        ├── 套料方案(代替零散的带钢/板材计划订单)
        ├── 母卷/母板采购建议(套料优化后的合并数量, 非零散)
        └── 追溯: 每条套料明细→需求行→合同号
```

### 8.3 MRP 增强后的完整流程图

```
┌──────────────────────────────────────────────────────────────────────────┐
│                MRP + 套料 自动化完整流程                                   │
│                                                                          │
│  ┌──────────────┐                                                        │
│  │ Step 0~1:    │  收集需求 (各合同的成品/半成品需求)                      │
│  │ 需求收集     │                                                        │
│  └──────┬───────┘                                                        │
│         │                                                                │
│  ┌──────▼───────┐                                                        │
│  │ Step 2:      │  双轨LLC (品类BOM + 离散BOM)                            │
│  │ LLC计算      │                                                        │
│  └──────┬───────┘                                                        │
│         │                                                                │
│  ┌──────▼───────┐                                                        │
│  │ Step 3:      │  按LLC逐层展算                                          │
│  │ 逐层展算     │                                                        │
│  │              │  ┌────────────────────────────────────────────────┐    │
│  │ 3a~3f:      │  │ 对每个需求:                                    │    │
│  │ 正常展算     │  │   净需求 → 批量 → 提前期 → 计划订单             │    │
│  │              │  └────────────────────────────────────────────────┘    │
│  │              │                                                        │
│  │ 3g: BOM展开  │  ┌────────────────────────────────────────────────┐    │
│  │ (增强)       │  │ 展开子层时判断:                                 │    │
│  │              │  │                                                │    │
│  │              │  │ 子层是否为"套料类"品类?                         │    │
│  │              │  │   STRIP←COIL (纵剪)                            │    │
│  │              │  │   PLATE←COIL (开平)                            │    │
│  │              │  │   CUT_PART   (剪切)                            │    │
│  │              │  │                                                │    │
│  │              │  │ 否 → 正常生成子层计划订单                       │    │
│  │              │  │ 是 → 放入套料需求池(不生成独立计划订单)          │    │
│  │              │  │      标记来源: 父计划订单+合同+材质+规格         │    │
│  │              │  └────────────────────────────────────────────────┘    │
│  └──────┬───────┘                                                        │
│         │                                                                │
│  ┌──────▼───────────────────────────────────────────────────────┐        │
│  │ Step 3.5: 套料自动合并优化  ← ★ 新增步骤 ★                   │        │
│  │                                                               │        │
│  │  3.5a: 从需求池取出所有 MRP_GENERATED 的需求                   │        │
│  │                                                               │        │
│  │  3.5b: 按 merge_group_key 自动分组                             │        │
│  │    例: "SLIT|2.5|Q235B|1500" → 合同A带钢300 + 合同B带钢600      │        │
│  │        "LEVEL|6.0|Q235B|1500" → 合同C板6m + 合同D板4m           │        │
│  │                                                               │        │
│  │  3.5c: 每组匹配库存母卷/母板                                    │        │
│  │    查: inv_stock 中 品类=COIL, 厚度=X, 材质=Y 的可用卷          │        │
│  │                                                               │        │
│  │  3.5d: 调用套料优化算法                                        │        │
│  │    纵剪 → 一维装箱(宽度组合)                                   │        │
│  │    开平 → 一维排列(定尺序列)                                   │        │
│  │    剪切 → 二维排版(BL算法)                                     │        │
│  │                                                               │        │
│  │  3.5e: 生成套料方案 (aps_nesting_plan + detail)                │        │
│  │    每行明细带: contract_no, pool_id, demand_line_id             │        │
│  │                                                               │        │
│  │  3.5f: 根据套料方案确定母料需求                                 │        │
│  │    套料方案需要3卷1500宽母卷 → 库存有2卷 → 采购1卷              │        │
│  │    生成: 母卷采购建议(合并后的数量, 非零散)                     │        │
│  │                                                               │        │
│  │  3.5g: 更新需求池状态: MRP_GENERATED → NESTED                  │        │
│  │                                                               │        │
│  └──────┬────────────────────────────────────────────────────────┘        │
│         │                                                                │
│  ┌──────▼───────┐                                                        │
│  │ Step 4:      │  输出                                                   │
│  │ 生成结果     │  · 制造计划订单(成品级, 正常)                            │
│  │              │  · 套料方案(代替零散的带钢/板材独立计划)                  │
│  │              │  · 母卷/母板 采购建议(套料合并后的数量)                   │
│  │              │  · MRP运行日志: 新增 nesting_count 字段                  │
│  └──────┬───────┘                                                        │
│         │                                                                │
│  ┌──────▼───────┐                                                        │
│  │ Step 5:      │  异常报告                                               │
│  │ 异常         │  · 新增: 套料利用率低于阈值(如<80%)的警告                │
│  │              │  · 新增: 无合适母卷可匹配的套料组                        │
│  │              │  · 新增: 窜料受限导致无法合并的需求                      │
│  └──────────────┘                                                        │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### 8.4 MRP 计划订单与套料方案的关系变化

```
原来(不合并):
  MRP为每个需求独立生成计划订单:
  ├── PO-001: 带钢300×2.5 Q235B 10T (合同A)  ← 各自独立
  ├── PO-002: 带钢600×2.5 Q235B 15T (合同B)  ← 各自独立
  ├── PO-003: 母卷1500×2.5 Q235B 10.4T (PO-001的原料)
  └── PO-004: 母卷1500×2.5 Q235B 15.6T (PO-002的原料)
  
  问题: 两个母卷独立采购, 实际只需要一卷就够

增强后(套料合并):
  MRP展开时, 带钢300和带钢600的需求进入套料池:
  ├── 套料池: [带钢300 10T 合同A] + [带钢600 15T 合同B]
  │           merge_group_key = "SLIT|2.5|Q235B|1500"
  ├── 套料优化: 300+600=900 ≤ 1500-边丝 → 一卷搞定!
  │           方案: 1500 → 300×1条 + 600×1条 + 590余料
  └── 输出:
      ├── 套料方案 NEST-001: 1卷1500 → 300(合同A) + 600(合同B)
      ├── PO-合并: 母卷1500×2.5 Q235B 26T (一卷, 合并后)
      └── (不生成独立的PO-001和PO-002, 由套料方案替代)
      
  节省: 1卷代替2卷, 省一次开卷+穿带+切头尾
```

---

## 9. 排产自动化衔接：套料方案自动转排产

### 9.1 套料方案自动转排产流程

```
MRP Step 3.5 生成套料方案后 → 自动进入排产流程:

  ┌──────────────────────────────────────────────────────────────────┐
  │  套料方案确认后的排产自动化                                       │
  │                                                                  │
  │  Step A: 套料方案 → 排产单(自动创建)                              │
  │                                                                  │
  │  每个套料方案生成 1~2 个排产单:                                    │
  │                                                                  │
  │  方案NEST-001 (纵剪):                                             │
  │  → SCH-SLIT-xxx: 分剪排产单                                       │
  │    material_id = 母卷(投入物料)                                    │
  │    nesting_plan_id = NEST-001                                     │
  │    is_multi_output = 1                                            │
  │    schedule_phase = 'PREP' (备料阶段)                              │
  │    raw_stock_id = 匹配的母卷库存                                   │
  │    涉及多合同 → 排产单不绑定单一合同(contract_no=NULL或"MULTI")     │
  │                                                                  │
  │  方案NEST-002 (开平):                                              │
  │  → SCH-LEVEL-xxx: 开平排产单                                      │
  │    同理                                                           │
  │                                                                  │
  │  Step B: 排产单工序自动展开                                        │
  │                                                                  │
  │  分剪排产:                                                        │
  │  工序10: 上料开卷 → 工序20: 纵剪(分条) → 工序30: 卷取(多卷)        │
  │                                                                  │
  │  开平排产:                                                        │
  │  工序10: 上料开卷 → 工序20: 矫平 → 工序30: 定尺剪切(多规格)       │
  │                                                                  │
  │  Step C: 排产单与需求的多对多关联                                  │
  │                                                                  │
  │  一个排产单(SCH-SLIT-xxx)的产出服务多个合同:                       │
  │  ├── 产出1(带钢300): → 合同A, demand_line=D-001                   │
  │  ├── 产出2(带钢600): → 合同B, demand_line=D-002                   │
  │  └── 产出3(余料590): → 入库(公共库存)                             │
  │                                                                  │
  │  关联通过 aps_nesting_detail 实现:                                │
  │  每行detail记录了 contract_no + demand_line_id + schedule_id       │
  │                                                                  │
  │  Step D: 后续工序的排产链接                                        │
  │                                                                  │
  │  分剪产出的带钢300 → 如果合同A后续要制管                           │
  │  → 自动创建制管排产单(SCH-PIPE-xxx)                               │
  │  → parent_schedule_id = SCH-SLIT-xxx                              │
  │  → raw_material = 带钢300(来自分剪产出)                           │
  │  → 排产开始时间 ≥ 分剪完成时间                                    │
  │                                                                  │
  │  Step E: 排产甘特图展示                                            │
  │                                                                  │
  │  分剪线: ████ SCH-SLIT-xxx (NEST-001: 合同A+B合并)                │
  │  焊管线:               ████ SCH-PIPE-xxx (合同A: 带钢300→方管)     │
  │                                                                  │
  │  排产单上标注: "合并套料: 3合同" 或列出合同号列表                   │
  └──────────────────────────────────────────────────────────────────┘
```

### 9.2 自动化配置参数

```sql
-- 套料自动化配置参数
CREATE TABLE bas_nesting_auto_config (
    config_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    
    -- ═══ MRP套料自动化 ═══
    mrp_auto_nesting    BIT NOT NULL DEFAULT 1,     -- MRP展算时是否自动触发套料合并
    min_merge_count     INT NOT NULL DEFAULT 2,     -- 最少合并需求数(1=不合并也做套料)
    min_utilization_pct DECIMAL(5,2) NOT NULL DEFAULT 75.0, -- 最低利用率要求(%)
                                                    -- 低于此值不合并, 各自独立生产
    
    -- ═══ 排产自动化 ═══
    auto_schedule       BIT NOT NULL DEFAULT 1,     -- 套料方案确认后是否自动创建排产单
    auto_link_next      BIT NOT NULL DEFAULT 1,     -- 是否自动链接后续制管/加工排产
    
    -- ═══ 品类级开关 ═══
    -- 哪些品类启用套料合并(为空=全部启用)
    enabled_categories  NVARCHAR(200) NULL,         -- 逗号分隔: "STRIP,PLATE,CUT_PART"
    
    -- ═══ 套料阈值 ═══
    max_contracts_per_nest INT NOT NULL DEFAULT 10,  -- 单个套料方案最多合并的合同数
    max_coil_count      INT NOT NULL DEFAULT 5,     -- 单次套料最多使用的母卷数
    
    is_active           BIT NOT NULL DEFAULT 1
);
```

### 9.3 MRP运行日志增强

```sql
-- mrp_run_log 增加套料相关统计
ALTER TABLE mrp_run_log ADD (
    nesting_pool_count  INT NULL,          -- 进入套料池的需求数
    nesting_plan_count  INT NULL,          -- 生成的套料方案数
    nesting_merge_count INT NULL,          -- 合并的需求组数
    avg_utilization_pct DECIMAL(5,2) NULL  -- 平均套料利用率(%)
);
```

### 9.4 MRP 展算伪代码增强

```java
public class MrpEngineService {
    
    @Autowired
    private NestingAutoService nestingAutoService;  // 套料自动化服务
    
    public void runMrp(...) {
        // Step 0~2: 初始化, 收集需求, LLC计算 (不变)
        
        // Step 3: 逐层展算
        for (String demandKey : demandKeys) {
            // ... 3a~3f: 正常的净需求→计划订单 ...
            
            // 3g: BOM展开 (增强)
            if ("MFG".equals(order.getOrderType())) {
                List<MrpDemand> childDemands = bomExploder.explode(...);
                
                for (MrpDemand child : childDemands) {
                    // ★ 判断是否为套料类需求 ★
                    if (nestingAutoService.isNestingCandidate(child)) {
                        // 放入套料需求池, 不生成独立计划订单
                        nestingAutoService.addToPool(child, order, runLog.getRunId());
                    } else {
                        // 正常加入下层需求队列
                        demandsByKey.computeIfAbsent(...).add(child);
                    }
                }
            }
        }
        
        // ★ Step 3.5: 套料自动合并优化 ★
        NestingAutoResult nestingResult = nestingAutoService.autoOptimize(runLog.getRunId());
        
        // 套料优化产生的母卷/母板需求 → 加入MRP继续展算(或直接生成采购建议)
        for (RawMaterialDemand rawDemand : nestingResult.getRawDemands()) {
            // 查库存匹配
            BigDecimal available = getAvailableWeight(rawDemand);
            if (available < rawDemand.getWeight()) {
                // 生成采购建议
                createPurchaseSuggestion(rawDemand);
            }
        }
        
        // Step 4: 输出(增强)
        runLog.setNestingPoolCount(nestingResult.getPoolCount());
        runLog.setNestingPlanCount(nestingResult.getPlanCount());
        runLog.setNestingMergeCount(nestingResult.getMergeCount());
        runLog.setAvgUtilizationPct(nestingResult.getAvgUtilization());
        
        // Step 5: 异常(增强)
        if (nestingResult.hasLowUtilization()) {
            exceptions.add("套料利用率低于" + config.getMinUtilizationPct() + "%");
        }
        if (nestingResult.hasUnmatchedGroups()) {
            exceptions.add("以下套料组无合适母卷: ...");
        }
    }
}

/**
 * 套料自动化服务
 */
public class NestingAutoService {
    
    /**
     * 判断一个MRP子层需求是否为套料候选
     */
    public boolean isNestingCandidate(MrpDemand child) {
        if (!config.getMrpAutoNesting()) return false;
        
        String category = child.getCategoryCode();
        // 纵剪: 需求品类=STRIP, 原料品类=COIL
        // 开平: 需求品类=PLATE, 原料品类=COIL
        // 剪切: 需求品类=CUT_PART
        return "STRIP".equals(category) 
            || "PLATE".equals(category) 
            || "CUT_PART".equals(category);
    }
    
    /**
     * MRP展算完成后, 自动执行套料合并优化
     */
    @Transactional
    public NestingAutoResult autoOptimize(Long runId) {
        // 1. 取出本次MRP生成的所有套料池需求
        List<NestingPool> pools = poolMapper.selectByRunId(runId);
        
        // 2. 按merge_group_key分组
        Map<String, List<NestingPool>> groups = pools.stream()
            .collect(Collectors.groupingBy(NestingPool::getMergeGroupKey));
        
        NestingAutoResult result = new NestingAutoResult();
        
        for (Map.Entry<String, List<NestingPool>> group : groups.entrySet()) {
            List<NestingPool> groupPools = group.getValue();
            
            // 3. 检查是否满足合并条件
            if (groupPools.size() < config.getMinMergeCount()) {
                // 不满足合并条件 → 转为独立计划订单
                convertToIndependentOrders(groupPools);
                continue;
            }
            
            // 4. 匹配母卷库存
            String[] keyParts = group.getKey().split("\\|");
            List<InvStock> motherCoils = findMotherCoils(keyParts);
            
            // 5. 调用套料优化算法
            NestingPlan plan = null;
            if ("SLIT".equals(keyParts[0])) {
                plan = slittingOptimizer.optimizeMultiDemand(motherCoils, groupPools);
            } else if ("LEVEL".equals(keyParts[0])) {
                plan = levelingOptimizer.optimizeMultiDemand(motherCoils, groupPools);
            } else if ("CUT".equals(keyParts[0])) {
                plan = cuttingOptimizer.optimizeMultiDemand(motherCoils, groupPools);
            }
            
            // 6. 利用率检查
            if (plan.getUtilizationPct() < config.getMinUtilizationPct()) {
                result.addLowUtilizationWarning(group.getKey(), plan.getUtilizationPct());
            }
            
            // 7. 保存套料方案
            savePlan(plan);
            
            // 8. 确定母料需求(套料后的合并数量)
            result.addRawDemand(plan.getSourceMaterial(), plan.getSourceWeight());
            
            // 9. 自动创建排产单(如果配置允许)
            if (config.getAutoSchedule()) {
                createScheduleFromNesting(plan);
            }
            
            result.addPlan(plan);
        }
        
        return result;
    }
}
```

---

## 10. 与现有设计的关系

```
本文档(20)增强了以下现有设计:

03-mrp-engine.md MRP展算引擎
  原有: BOM展开直接生成子层计划订单
  增强: + 套料类品类识别 + 需求池汇入 + Step 3.5自动合并优化
        + 套料后的母料需求合并(减少采购)

04-scheduling-engine.md 排产引擎
  原有: 计划订单→排产单(一对一)
  增强: + 套料方案→排产单(一对多合同) + 自动链接后续工序排产

08-steel-industry-adaptation.md §4 套裁方案
  原有: 单投入→多产出, 一维分剪
  增强: + 多需求合并(需求池) + 跨合同 + 二维排版

17-length-contract-mixcontrol.md §3 窜料控制
  原有: 排产级窜料控制
  增强: 套料级窜料控制(allow_merge字段)

aps_nesting_plan / aps_nesting_detail
  原有: 基本的套裁明细
  增强: + nesting_mode + merge_group_key + contract_count
         + allocated_cost + 每行带 contract_no + customer

新增表:
  aps_nesting_pool       — 需求合并池(含MRP自动生成标记)
  aps_nesting_2d_layout  — 二维排版坐标
  bas_nesting_auto_config— 套料自动化配置参数

mrp_run_log 增强:
  + nesting_pool_count / nesting_plan_count / nesting_merge_count / avg_utilization_pct
```
