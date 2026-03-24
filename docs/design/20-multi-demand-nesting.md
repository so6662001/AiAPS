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

## 8. 与现有设计的关系

```
本文档(20)增强了以下现有设计:

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
  aps_nesting_pool     — 需求合并池
  aps_nesting_2d_layout — 二维排版坐标
```
