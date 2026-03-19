# AiAPS — 长度批次化、合同号贯穿与窜料控制设计

> 版本：1.0 | 最后更新：2026-03-17
>
> 三个增强需求：
> 1. 长度从物料剥离到库存批次属性，展示规格时动态拼接长度
> 2. 合同号从第一步贯穿到最后一步（需求→计划→排产→领料→报工→库存）
> 3. 排产窜料控制（是否允许不同合同的料混用）

---

## 1. 长度批次化设计

### 1.1 为什么长度不应在物料档案中

```
现有问题:
  物料: RPIPE-125×125×2.75×9000 (长度9000mm写在物料编码里)
  同规格方管6m定尺 → 另一个物料: RPIPE-125×125×2.75×6000
  同规格方管12m定尺 → 又一个物料: RPIPE-125×125×2.75×12000
  同规格方管不定尺 → 再一个物料: RPIPE-125×125×2.75×BD
  
  结果: 物料数爆炸, 同一截面规格不同长度 = N 个物料

优化后:
  物料: RPIPE-125×125×2.75 (只到截面规格, 不含长度)
  长度 9000/6000/12000/不定尺 → 库存批次属性
  
  展示时动态拼接: "方管 125×125×2.75 × 9m" (规格 + "×" + 长度)
  
  同理适用于:
  · 管材: 方管/圆管的定尺长度
  · 板材: 开平板的定尺长度
  · 型材: H型钢的定尺长度
  · 带钢/钢卷: 卷长(连续卷, 长度是过磅推算)
```

### 1.2 物料表修改

```sql
-- bas_material: 移除 length 字段的"必要性"
-- length 字段保留但改为"参考长度/默认长度", 不再是物料唯一性维度

-- 原来: material_code = 'RPIPE-125×125×2.75×9000'
-- 现在: material_code = 'RPIPE-125×125×2.75'
--       length 字段 = NULL (不限) 或 默认值 (参考)

-- 新增字段:
ALTER TABLE bas_material ADD (
    length_in_material  BIT NOT NULL DEFAULT 0,     -- 长度是否作为物料维度
                                                    -- 0=长度在批次(推荐)
                                                    -- 1=长度在物料(兼容旧模式)
    default_length      DECIMAL(10,3) NULL,         -- 默认/参考长度(mm)
    length_unit         VARCHAR(5)  NULL DEFAULT 'mm' -- 长度单位: mm/m
);

-- 规格显示拼接函数(数据库层面的视图辅助)
-- 展示规格 = spec_desc + '×' + 长度(来自库存批次)
```

### 1.3 库存表增加长度属性

```sql
-- inv_stock: 长度作为批次属性
ALTER TABLE inv_stock ADD (
    stock_length        DECIMAL(10,3) NULL,         -- 实际长度(mm): 9000/6000/12000
    length_type         VARCHAR(10)   NULL,         -- FIXED=定尺 RANDOM=不定尺 COIL=卷长
    length_display      NVARCHAR(20)  NULL          -- 展示文本: "9m" / "6m" / "不定尺" / "卷"
);

-- 完整规格展示 = 物料规格 + 长度展示
-- 例: spec_desc='125×125×2.75' + length_display='9m' → "125×125×2.75 × 9m"
```

### 1.4 各关联表增加长度

```sql
-- dem_demand_line: 需求指定长度
ALTER TABLE dem_demand_line ADD (
    required_length     DECIMAL(10,3) NULL,         -- 需求长度(mm)
    length_type         VARCHAR(10)   NULL,         -- FIXED/RANDOM
    length_display      NVARCHAR(20)  NULL          -- "9m"
);

-- mrp_plan_order: 计划携带长度
ALTER TABLE mrp_plan_order ADD (
    plan_length         DECIMAL(10,3) NULL,
    length_type         VARCHAR(10)   NULL,
    length_display      NVARCHAR(20)  NULL
);

-- aps_schedule: 排产携带长度
ALTER TABLE aps_schedule ADD (
    product_length      DECIMAL(10,3) NULL,         -- 产出长度
    length_type         VARCHAR(10)   NULL,
    length_display      NVARCHAR(20)  NULL
);

-- prd_report: 报工携带长度
ALTER TABLE prd_report ADD (
    product_length      DECIMAL(10,3) NULL,
    length_display      NVARCHAR(20)  NULL
);

-- inv_receipt: 入库携带长度
ALTER TABLE inv_receipt ADD (
    product_length      DECIMAL(10,3) NULL,
    length_display      NVARCHAR(20)  NULL
);

-- inv_transaction: 流水携带长度
ALTER TABLE inv_transaction ADD (
    product_length      DECIMAL(10,3) NULL,
    length_display      NVARCHAR(20)  NULL
);
```

### 1.5 规格展示拼接规则

```java
/**
 * 规格展示拼接器
 * 规则: 物料规格 + "×" + 长度 + 材质 + 产地
 * 例: "方管 125×125×2.75 × 9m Q235B 鞍钢"
 */
public class SpecDisplayBuilder {
    
    /**
     * 生成完整规格展示文本
     */
    public String build(String specDesc, String lengthDisplay,
                        String gradeCode, String originCode) {
        StringBuilder sb = new StringBuilder();
        sb.append(specDesc);                          // "125×125×2.75"
        
        if (lengthDisplay != null && !lengthDisplay.isEmpty()) {
            sb.append(" × ").append(lengthDisplay);   // " × 9m"
        }
        
        if (gradeCode != null) {
            sb.append(" ").append(gradeCode);          // " Q235B"
        }
        
        if (originCode != null) {
            sb.append(" ").append(originCode);         // " 鞍钢"
        }
        
        return sb.toString();
        // 最终: "125×125×2.75 × 9m Q235B 鞍钢"
    }
    
    /**
     * 打印用的完整规格(含品类名称)
     */
    public String buildForPrint(String categoryName, String specDesc,
                                 String lengthDisplay, String gradeCode) {
        // "方管 125×125×2.75 × 9m Q235B"
        return categoryName + " " + build(specDesc, lengthDisplay, gradeCode, null);
    }
}
```

---

## 2. 合同号全链路贯穿

### 2.1 设计理念

```
合同号(contract_no) 从第一步到最后一步, 每张单据都携带:

  客户合同 HT-2026-0100
    ↓
  需求单 dem_demand_line     → contract_no = 'HT-2026-0100'
    ↓
  MRP计划 mrp_plan_order     → contract_no = 'HT-2026-0100'
    ↓
  排产单 aps_schedule        → contract_no = 'HT-2026-0100'
    ↓
  领料单 prd_material_issue  → contract_no = 'HT-2026-0100'
    ↓
  报工 prd_report            → contract_no = 'HT-2026-0100'
    ↓
  入库 inv_receipt            → contract_no = 'HT-2026-0100'
    ↓
  库存 inv_stock             → contract_no = 'HT-2026-0100' (专用库存)
    ↓
  出库流水 inv_transaction    → contract_no = 'HT-2026-0100'
    ↓
  发货                       → 按合同号捡货发货

作用:
  · 知道这批料是哪个合同的 → 防止窜料
  · 知道这批产出属于哪个合同 → 客户追溯
  · 合同维度统计: 成本/利润/用量/进度
  · 多合同排产时区分物料归属
```

### 2.2 各表增加合同号字段

```sql
-- ═══ 每张核心表增加 contract_no ═══

-- dem_demand_line
ALTER TABLE dem_demand_line ADD (
    contract_no         VARCHAR(60)   NULL          -- 客户合同号
);

-- mrp_plan_order
ALTER TABLE mrp_plan_order ADD (
    contract_no         VARCHAR(60)   NULL          -- 合同号(从需求继承)
);

-- aps_schedule
ALTER TABLE aps_schedule ADD (
    contract_no         VARCHAR(60)   NULL          -- 合同号
);

-- aps_schedule_oper
ALTER TABLE aps_schedule_oper ADD (
    contract_no         VARCHAR(60)   NULL
);

-- prd_material_issue
ALTER TABLE prd_material_issue ADD (
    contract_no         VARCHAR(60)   NULL
);

-- prd_report
ALTER TABLE prd_report ADD (
    contract_no         VARCHAR(60)   NULL
);

-- inv_receipt
ALTER TABLE inv_receipt ADD (
    contract_no         VARCHAR(60)   NULL
);

-- inv_stock
ALTER TABLE inv_stock ADD (
    contract_no         VARCHAR(60)   NULL          -- 专用库存: 某合同专用
                                                    -- NULL = 公共库存(任何合同可用)
);

-- inv_transaction
ALTER TABLE inv_transaction ADD (
    contract_no         VARCHAR(60)   NULL
);

-- aps_nesting_detail (套裁明细)
ALTER TABLE aps_nesting_detail ADD (
    contract_no         VARCHAR(60)   NULL
);
```

### 2.3 合同号继承规则

```
自动继承链:
  dem_demand_line.contract_no = 客户合同编号(手工录入/导入)
    ↓ MRP展算时继承
  mrp_plan_order.contract_no = 需求行的 contract_no
    ↓ 转排产时继承
  aps_schedule.contract_no = 计划订单的 contract_no
    ↓ 领料时继承
  prd_material_issue.contract_no = 排产的 contract_no
    ↓ 报工时继承
  prd_report.contract_no = 排产的 contract_no
    ↓ 入库时继承
  inv_receipt.contract_no = 排产的 contract_no
    ↓ 库存入库时标记
  inv_stock.contract_no = 入库的 contract_no (专用库存)

特殊情况:
  · MTS/SSK(非合同)需求 → contract_no = NULL
  · 库存 contract_no=NULL → 公共库存, 任何合同都可以领用
  · 库存 contract_no='HT-xxx' → 专用库存, 只能该合同领用(除非允许窜料)
```

---

## 3. 窜料控制

### 3.1 什么是窜料

```
窜料 = 将属于合同A的原料/半成品, 用于合同B的生产

场景:
  合同 HT-001 需要 方管125×125 Q235B 20T
  合同 HT-002 需要 方管125×125 Q235B 15T
  
  库存:
    批次1: 方管125×125 Q235B 12T — 标记 contract_no='HT-001' (专用)
    批次2: 方管125×125 Q235B 8T  — 标记 contract_no='HT-002' (专用)
    批次3: 方管125×125 Q235B 5T  — 标记 contract_no=NULL (公共)

  不允许窜料时:
    HT-001 只能用 批次1(12T) + 批次3(5T) = 17T → 差3T需继续生产
    HT-002 只能用 批次2(8T) = 8T → 差7T
    不能把批次1的料给HT-002用

  允许窜料时:
    HT-001 可以用 批次1 + 批次2的部分 → 灵活调配
    但需要记录窜料事件, 审批确认
```

### 3.2 窜料控制参数

```sql
-- 排产单级别的窜料控制
ALTER TABLE aps_schedule ADD (
    allow_mix_contract  BIT NOT NULL DEFAULT 0,     -- 是否允许窜料(使用其他合同的料)
    mix_contract_reason NVARCHAR(200) NULL           -- 允许窜料的原因
);

-- 全局和客户级窜料策略
CREATE TABLE bas_mix_contract_policy (
    policy_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    policy_scope        VARCHAR(20)   NOT NULL,     -- GLOBAL=全局 CUSTOMER=按客户
    customer_code       VARCHAR(30)   NULL,         -- 按客户时指定
    
    allow_mix           BIT           NOT NULL DEFAULT 0, -- 是否允许窜料
    need_approval       BIT           NOT NULL DEFAULT 1, -- 窜料是否需要审批
    
    -- 窜料范围控制
    mix_scope           VARCHAR(20)   NOT NULL DEFAULT 'SAME_CUSTOMER',
                                                    -- SAME_CUSTOMER=同客户不同合同可窜
                                                    -- SAME_GRADE=同材质可窜
                                                    -- ANY=任意(最宽松)
    remark              NVARCHAR(200) NULL,
    is_active           BIT           NOT NULL DEFAULT 1
);

-- 窜料记录
CREATE TABLE prd_mix_contract_log (
    mix_log_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    schedule_id         BIGINT        NOT NULL,
    
    -- 原合同(排产单的)
    original_contract   VARCHAR(60)   NOT NULL,
    -- 实际使用的料属于的合同
    actual_contract     VARCHAR(60)   NULL,         -- NULL=公共库存
    
    stock_id            BIGINT        NOT NULL,
    material_id         BIGINT        NOT NULL,
    mix_weight          DECIMAL(18,3) NOT NULL,     -- 窜料重量
    
    -- 审批
    approval_status     VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    approved_by         VARCHAR(50)   NULL,
    approved_time       DATETIME      NULL,
    
    mix_reason          NVARCHAR(500) NULL,
    created_by          VARCHAR(50)   NOT NULL,
    created_time        DATETIME      NOT NULL DEFAULT GETDATE()
);
```

### 3.3 领料时的窜料检查

```java
/**
 * 领料窜料检查器
 * 领料时检查: 要领的料是否属于当前合同, 不属于则触发窜料流程
 */
public class MixContractChecker {
    
    public static class CheckResult {
        private boolean isMatch;          // 合同是否匹配
        private boolean isMixAllowed;     // 窜料是否允许
        private boolean needApproval;     // 是否需要审批
        private String message;
    }
    
    public CheckResult check(Long scheduleId, Long stockId) {
        ScheduleTask task = scheduleMapper.selectById(scheduleId);
        StockItem stock = stockMapper.selectById(stockId);
        CheckResult r = new CheckResult();
        
        String taskContract = task.getContractNo();
        String stockContract = stock.getContractNo();
        
        // 情况1: 公共库存(contract_no=NULL) → 任何合同都可用
        if (stockContract == null) {
            r.setMatch(true);
            return r;
        }
        
        // 情况2: 合同匹配 → 正常
        if (stockContract.equals(taskContract)) {
            r.setMatch(true);
            return r;
        }
        
        // 情况3: 合同不匹配 → 窜料
        r.setMatch(false);
        
        // 检查排产单是否允许窜料
        if (task.getAllowMixContract()) {
            r.setMixAllowed(true);
            r.setNeedApproval(false);
            r.setMessage("排产单允许窜料, 库存合同" + stockContract 
                + " ≠ 排产合同" + taskContract);
            return r;
        }
        
        // 检查客户/全局策略
        MixContractPolicy policy = policyMapper.selectByCustomer(task.getCustomerCode());
        if (policy == null) policy = policyMapper.selectGlobal();
        
        if (policy != null && policy.getAllowMix()) {
            r.setMixAllowed(true);
            r.setNeedApproval(policy.getNeedApproval());
            r.setMessage("策略允许窜料" + (policy.getNeedApproval() ? ", 需审批" : ""));
        } else {
            r.setMixAllowed(false);
            r.setMessage("不允许窜料! 库存合同" + stockContract 
                + " ≠ 排产合同" + taskContract + ", 请使用正确合同的料");
        }
        
        return r;
    }
}
```

---

## 4. 操作页面示例图

### 4.1 排产甘特图(含合同号+长度+窜料标记)

```
┌──────────────────────────────────────────────────────────────────────────┐
│  排产调度中心                                                            │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  视图: [产线视图●] [合同视图]    筛选: 合同号[全部▼] 窜料[全部▼]         │
│                                                                          │
├─────────┬────────────────────────────────────────────────────────────────┤
│         │  03-22                        03-23                            │
│ 产线    │  08:00    12:00    16:00     08:00    12:00    16:00           │
├─────────┼────────────────────────────────────────────────────────────────┤
│         │                                                                │
│ 分剪线  │ ████████████████████████████                                  │
│         │ SCH-SLIT-0200                                                  │
│ 📊92%  │ 热卷 2.75×1500 Q235B 鞍钢 22T                                 │
│         │ 📋HT-2026-0100 XX钢构                                         │
│         │ → 500带钢→制管 + 1000带钢→半成品库                            │
│         │ 🔒不允许窜料                                                   │
│         │                                                                │
│ 焊管1线 │              ████████████████████████████████████████          │
│         │              SCH-PIPE-0201                                     │
│ 📊85%  │              方管 125×125×2.75 × 9m Q235B 鞍钢                 │
│         │              📋HT-2026-0100 XX钢构                             │
│         │              模具M-010 | 64根/7T                               │
│         │              → 成品入库                                        │
│         │              🔒不允许窜料                                      │
│         │                                                                │
│         │                               ██████████████████████████       │
│         │                               SCH-PIPE-0202                   │
│         │                               方管 125×125×2.75 × 6m Q235B    │
│         │                               📋HT-2026-0105 YY建材           │
│         │                               🔓允许窜料                       │
│         │                                                                │
├─────────┴────────────────────────────────────────────────────────────────┤
│  📍 选中: SCH-PIPE-0201                                                  │
│  方管 125×125×2.75 × 9m | Q235B | 鞍钢 | 64根 / 7.000T                  │
│  合同: HT-2026-0100 | 客户: XX钢构 | 交期: 04-01 | 窜料: 🔒不允许      │
│  原料: 带钢500 SLT-0200-1 | 模具: M-010 | 流向: → 成品入库              │
│                                                                          │
│  [调整时间] [更换产线] [修改窜料策略] [查看合同进度] [查看全链路]         │
└──────────────────────────────────────────────────────────────────────────┘

颜色方案:
  合同色带(甘特条左侧竖条): 不同合同用不同颜色区分
  HT-2026-0100 = 蓝色竖条
  HT-2026-0105 = 绿色竖条
  无合同(公共) = 灰色竖条
```

### 4.2 领料上料操作页面(含合同号+窜料检查)

```
┌──────────────────────────────────────────────────────────────────────────┐
│  领料上料                                                   [分剪线]    │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  当前工单: SCH-SLIT-0200                                                 │
│  产品: 分剪 2.75×1500 → 500+1000                                        │
│  合同: 📋 HT-2026-0100 (XX钢构)                                        │
│  窜料: 🔒 不允许                                                        │
│                                                                          │
│  ─── 扫码/选择物料上料 ───                                               │
│  卷号: [ HRC-2026-0200        ] [🔍扫码]                                │
│                                                                          │
│  ┌─── 物料信息 ──────────────────────────────────────────────────────┐ │
│  │  卷号: HRC-2026-0200                                              │ │
│  │  物料: 热轧卷 2.75×1500                                           │ │
│  │  材质: Q235B | 产地: 鞍钢 | 重量: 22.000T                         │ │
│  │  合同: HT-2026-0100                                               │ │
│  │  仓库: RAW-A | 库位: A-03-02                                      │ │
│  │                                                                    │ │
│  │  ✅ 合同匹配: 库存合同 = 工单合同                                  │ │
│  │  ✅ 规格匹配: 2.75×1500 符合要求                                   │ │
│  │  ✅ 材质匹配: Q235B 符合要求                                       │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  [确认领料上料]  [取消]                                                  │
│                                                                          │
│  ─── 如果扫了一卷不对的 ───                                              │
│  ┌─── ⚠ 窜料警告 ─────────────────────────────────────────────────┐   │
│  │  卷号: HRC-2026-0215                                           │   │
│  │  物料: 热轧卷 2.75×1500 Q235B 唐钢 20T                         │   │
│  │  合同: HT-2026-0105 (YY建材) ← ⚠ 与工单合同不一致!              │   │
│  │                                                                 │   │
│  │  🔒 当前工单不允许窜料                                          │   │
│  │                                                                 │   │
│  │  建议:                                                          │   │
│  │  · 请使用合同 HT-2026-0100 的物料                               │   │
│  │  · 合同HT-0100可用库存: HRC-2026-0200(22T) HRC-2026-0198(18T)  │   │
│  │                                                                 │   │
│  │  [换一卷料]  [申请窜料审批]  [调整到匹配工单(SCH-xxx)]           │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### 4.3 报工操作页面(含合同号+长度+重量)

```
┌──────────────────────────────────────────────────────────────────────────┐
│  生产报工                                              [焊管1线 班次1]  │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  工单: SCH-PIPE-0201                                                     │
│  产品: 方管 125×125×2.75 × 9m Q235B 鞍钢                                │
│  合同: 📋 HT-2026-0100 (XX钢构)                                        │
│  模具: M-010 (累计: 145/500T)                                           │
│                                                                          │
│  原料使用:                                                               │
│  ┌──────────┬────────────┬──────┬──────┬──────┬──────────────┐          │
│  │卷号      │物料        │材质  │产地  │领用量│合同          │          │
│  ├──────────┼────────────┼──────┼──────┼──────┼──────────────┤          │
│  │SLT-0200-1│带钢2.75×500│Q235B│鞍钢  │7.260T│HT-2026-0100 │          │
│  └──────────┴────────────┴──────┴──────┴──────┴──────────────┘          │
│                                                                          │
│  本次报工:                                                               │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │  合格数量: [  62  ] 根      合格重量: [  6.783  ] T              │  │
│  │  废品数量: [   2  ] 根      废品重量: [  0.217  ] T              │  │
│  │  产出长度: [  9000 ] mm (= 9m)                                   │  │
│  │                                                                  │  │
│  │  投入原料: 7.260T | 产出: 7.000T | 成材率: 93.4%                 │  │
│  │  合同: HT-2026-0100 (自动继承)                                   │  │
│  │                                                                  │  │
│  │  操作员: [张三▼]    班次: [白班▼]                                 │  │
│  │  开工时间: [2026-03-22 08:20]                                    │  │
│  │  完工时间: [2026-03-22 17:30]                                    │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  [提交报工]  [暂存]                                                     │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### 4.4 MRP计划订单审核页面(含合同号+长度)

```
┌──────────────────────────────────────────────────────────────────────────┐
│  MRP 计划订单审核                                                        │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  筛选: 合同[全部▼] 品类[全部▼] 材质[全部▼] 状态[全部▼]                  │
│                                                                          │
│  ┌──────┬──────────────────┬──────┬──────┬──────┬──────┬──────┬──────┐ │
│  │订单号│物料/规格         │材质  │产地  │重量  │长度  │合同号│状态  │ │
│  ├──────┼──────────────────┼──────┼──────┼──────┼──────┼──────┼──────┤ │
│  │PO-201│方管125×125×2.75  │Q235B │不限  │20.0T │9m   │HT-100│待确认│ │
│  │PO-202│带钢2.75×500      │Q235B │不限  │ 7.3T │卷   │HT-100│待确认│ │
│  │PO-203│热轧卷2.75×1500   │Q235B │不限  │22.0T │卷   │HT-100│待确认│ │
│  │PO-204│方管125×125×2.75  │Q235B │不限  │15.0T │6m   │HT-105│待确认│ │
│  └──────┴──────────────────┴──────┴──────┴──────┴──────┴──────┴──────┘ │
│                                                                          │
│  注意: 同一物料(方管125×125×2.75)因长度不同(9m/6m)                       │
│        以及合同不同(HT-100/HT-105), 分别独立列出                        │
│                                                                          │
│  [✓ 批量确认] [✗ 批量取消] [🔒 锁定] [→ 转排产] [📋 导出]              │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### 4.5 库存查询页面(含合同号+长度)

```
┌──────────────────────────────────────────────────────────────────────────┐
│  库存查询                                                                │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  筛选: 品类[方管▼] 材质[Q235B▼] 合同[全部▼] 仓库[全部▼]                │
│                                                                          │
│  ┌──────────────────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┐ │
│  │物料/规格         │材质  │产地  │长度  │重量  │数量  │合同号│仓库  │ │
│  ├──────────────────┼──────┼──────┼──────┼──────┼──────┼──────┼──────┤ │
│  │方管125×125×2.75  │Q235B │鞍钢  │9m    │6.783T│62根  │HT-100│成品A │ │
│  │方管125×125×2.75  │Q235B │唐钢  │6m    │8.500T│85根  │HT-105│成品A │ │
│  │方管125×125×2.75  │Q235B │鞍钢  │12m   │5.200T│26根  │(公共) │成品A │ │
│  │方管125×125×2.75  │Q235B │日照  │不定尺│3.100T│—    │(公共) │成品B │ │
│  └──────────────────┴──────┴──────┴──────┴──────┴──────┴──────┴──────┘ │
│                                                                          │
│  打印标签预览:                                                           │
│  ┌────────────────────────────────────────────┐                         │
│  │  方管 125×125×2.75 × 9m Q235B              │                         │
│  │  产地: 鞍钢   |  重量: 6.783T   |  62根     │                         │
│  │  合同: HT-2026-0100  |  客户: XX钢构        │                         │
│  │  批次: P-2026-0301   |  日期: 2026-03-22    │                         │
│  │  [二维码]                                   │                         │
│  └────────────────────────────────────────────┘                         │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 5. 报表示例图

### 5.1 合同生产进度报表

```
┌──────────────────────────────────────────────────────────────────────────┐
│  合同生产进度报表                                         [导出Excel]   │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  合同: HT-2026-0100 | 客户: XX钢构 | 合同日期: 2026-03-15               │
│                                                                          │
│  ┌────────────────────┬──────┬──────┬──────┬──────┬──────────┬────────┐ │
│  │产品                │材质  │长度  │合同量│已产量│进度      │交期    │ │
│  ├────────────────────┼──────┼──────┼──────┼──────┼──────────┼────────┤ │
│  │方管125×125×2.75    │Q235B │9m    │20.0T │6.8T  │████░ 34% │04-01   │ │
│  │方管100×50×4.0      │Q235B │6m    │30.0T │30.0T │██████100%│03-28 ✅│ │
│  │圆管Φ89×3.5        │Q235B │12m   │15.0T │8.2T  │████░ 55% │04-05   │ │
│  ├────────────────────┼──────┼──────┼──────┼──────┼──────────┼────────┤ │
│  │合计                │      │      │65.0T │45.0T │████░ 69% │        │ │
│  └────────────────────┴──────┴──────┴──────┴──────┴──────────┴────────┘ │
│                                                                          │
│  生产链路(方管125×125×2.75×9m):                                          │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐           │
│  │ 热轧卷领料 │→│ 分剪       │→│ 制管       │→│ 成品入库   │           │
│  │ 22T ✅完成 │  │ 21.8T ✅  │  │ 6.8T/20T  │  │ 6.8T      │           │
│  │ HRC-0200  │  │ →500+1000  │  │ 进行中34% │  │ 批次P-0301│           │
│  └───────────┘  └───────────┘  └───────────┘  └───────────┘           │
│                                                                          │
│  用料追溯:                                                               │
│  热卷HRC-0200(22T鞍钢) → 分剪SLT-0200-1(7.26T) → 方管P-0301(6.78T)    │
│  合同全链路: 每一步都标记 HT-2026-0100, 无窜料                           │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### 5.2 窜料使用报表

```
┌──────────────────────────────────────────────────────────────────────────┐
│  窜料使用报表                                          [本月▼] [导出]   │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  本月窜料: 3次 | 涉及重量: 8.5T | 涉及合同: 4个                         │
│                                                                          │
│  ┌──────┬────────────┬──────────┬──────────┬──────┬──────┬──────────┐ │
│  │排产号│产品        │原合同    │料的合同  │重量  │审批  │原因      │ │
│  ├──────┼────────────┼──────────┼──────────┼──────┼──────┼──────────┤ │
│  │SCH-88│方管80×40   │HT-0098  │HT-0102   │3.5T  │已批准│原料紧急  │ │
│  │      │×3.0 × 6m  │(AA钢构)  │(BB管业)  │      │      │借用      │ │
│  ├──────┼────────────┼──────────┼──────────┼──────┼──────┼──────────┤ │
│  │SCH-95│圆管Φ114   │HT-0105  │(公共库存)│2.0T  │自动  │公共库存  │ │
│  │      │×4.5 × 12m │(YY建材)  │          │      │      │无需审批  │ │
│  ├──────┼────────────┼──────────┼──────────┼──────┼──────┼──────────┤ │
│  │SCH-99│方管100×50  │HT-0110  │HT-0098   │3.0T  │已批准│合同取消  │ │
│  │      │×4.0 × 9m  │(CC建设)  │(AA钢构)  │      │      │料转用    │ │
│  └──────┴────────────┴──────────┴──────────┴──────┴──────┴──────────┘ │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### 5.3 产品规格标签打印(含长度)

```
打印模板(产品标签):

  ┌──────────────────────────────────────────────────┐
  │                                                  │
  │   XX钢铁有限公司                                  │
  │                                                  │
  │   产品: 方管 125×125×2.75 × 9m                   │
  │   材质: Q235B         执行标准: GB/T6728          │
  │   产地: 鞍钢          炉号: A2026-03-888          │
  │                                                  │
  │   重量: 6.783T (62根)                            │
  │   批次: P-2026-0301                               │
  │   合同: HT-2026-0100                              │
  │   客户: XX钢构                                    │
  │   日期: 2026-03-22                                │
  │                                                  │
  │   ┌──────────┐                                   │
  │   │ [二维码]  │  扫码查追溯链路                    │
  │   └──────────┘                                   │
  │                                                  │
  └──────────────────────────────────────────────────┘

注意: 规格展示 = "125×125×2.75 × 9m" 
      其中 "125×125×2.75" 来自物料, "9m" 来自批次的 length_display
```
