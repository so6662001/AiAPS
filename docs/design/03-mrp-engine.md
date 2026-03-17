# AiAPS — MRP 展算引擎设计

> 版本：2.0 | 最后更新：2026-03-17
>
> **重要：** MRP 引擎对品类 BOM 的展算逻辑增强、多阶段排产的衔接设计、
> 以及原料驱动型排产（反向 MRP）请参见 **[08-steel-industry-adaptation.md](08-steel-industry-adaptation.md)** 第 6 节。

---

## 1. MRP 展算流程总览

### 1.1 MRP 运行触发方式

| 方式 | 触发条件 | 运行类型 |
|------|---------|---------|
| 手动触发 | 用户在 MRP 工作台点击"运行" | FULL / SELECTIVE |
| 定时任务 | Quartz 定时调度（如每日凌晨 2:00） | FULL |
| 事件驱动 | 新订单确认 / 库存异动 / 安全库存预警 | NET (净变更) |

### 1.2 MRP 展算主流程

```
                    ┌──────────────────────────┐
                    │  Step 0: 初始化与参数校验   │
                    │  · 创建 mrp_run_log 记录   │
                    │  · 加载物料/BOM/库存快照    │
                    │  · 确定计划展望期           │
                    └───────────┬──────────────┘
                                │
                    ┌───────────▼──────────────┐
                    │  Step 1: 需求收集与合并     │
                    │  · 订货合同需求 (MTO)       │
                    │  · 期货订单需求 (MTS)       │
                    │  · 安全库存补货 (SSK)       │
                    │  · 按物料+日期 合并汇总     │
                    └───────────┬──────────────┘
                                │
                    ┌───────────▼──────────────┐
                    │  Step 2: BOM 低阶码计算     │
                    │  · 遍历所有 BOM             │
                    │  · 计算每个物料的低阶码(LLC) │
                    │  · 确定展算顺序             │
                    └───────────┬──────────────┘
                                │
                    ┌───────────▼──────────────┐
                    │  Step 3: 按低阶码逐层展算   │
                    │  LLC = 0, 1, 2, ...        │
                    │  ┌──────────────────────┐  │
                    │  │ 3a. 计算毛需求        │  │
                    │  │ 3b. 扣减可用库存/供给  │  │
                    │  │ 3c. 计算净需求         │  │
                    │  │ 3d. 应用批量策略       │  │
                    │  │ 3e. 提前期偏移         │  │
                    │  │ 3f. 生成计划订单       │  │
                    │  │ 3g. BOM 展开→下层毛需求│  │
                    │  └──────────────────────┘  │
                    └───────────┬──────────────┘
                                │
                    ┌───────────▼──────────────┐
                    │  Step 4: 生成输出           │
                    │  · 制造计划订单 (MFG)       │
                    │  · 采购建议订单 (PUR)       │
                    │  · 委外加工订单 (SUB)       │
                    │  · 需求-供给钉量关系        │
                    └───────────┬──────────────┘
                                │
                    ┌───────────▼──────────────┐
                    │  Step 5: 异常与建议         │
                    │  · 提前期不足警告           │
                    │  · 产能瓶颈预警(粗能力)     │
                    │  · 物料缺失/BOM 不完整      │
                    └──────────────────────────┘
```

### 1.3 三种需求来源的合并策略

```java
/**
 * 需求合并规则：
 * 1. MTO（订货合同）：严格按合同行独立计算，不与其他需求合并，保证交期追溯
 * 2. MTS（期货订单）：同物料、交期在同一计划周期内的可合并
 * 3. SSK（安全库存）：当 可用量 < 安全库存 时自动生成补货需求
 *    补货量 = MAX(安全库存 - 可用量, 最小起订量)
 */
public class DemandMergeStrategy {
    
    // MTO 需求：独立不合并
    // MTS 需求：按 (物料 + 计划周期) 合并
    // SSK 需求：系统自动生成
}
```

## 2. MRP 核心算法详细设计

### 2.1 低阶码 (Low Level Code) 计算

低阶码决定了 MRP 的展算顺序，确保父项在子项之前被处理。

```
算法：
  1. 初始化所有物料 LLC = 0
  2. 遍历所有 BOM 关系
  3. 对于每条 BOM 关系 (父项 → 子项)：
     子项.LLC = MAX(子项.LLC, 父项.LLC + 1)
  4. 重复步骤 2-3 直到没有变化

示例：
  成品 A (LLC=0)
    ├── 半成品 B (LLC=1)
    │     ├── 原材料 C (LLC=2)
    │     └── 原材料 D (LLC=2)
    └── 半成品 E (LLC=1)
          └── 原材料 C (LLC=2)  ← C 出现在多处，取最大 LLC=2
```

### 2.2 净需求计算公式

```
对于每个物料 M, 在每个时间桶 T:

  毛需求(M, T) = Σ 独立需求(来自需求单) + Σ 相关需求(来自父项 BOM 展开)

  预计可用库存(M, T) = 
      期初库存 
    + 已确认供给(采购在途 + 在制 + 已确认计划订单)
    - 毛需求(M, T)

  净需求(M, T) = 
    IF 预计可用库存(M, T) < 安全库存(M)
    THEN 毛需求(M, T) - 期初库存 - 已确认供给 + 安全库存(M)
    ELSE 0

  计划订单量 = ApplyLotSizing(净需求(M, T), M.lot_policy)

  计划订单开始日期 = T - M.lead_time_days
```

### 2.3 批量策略 (Lot Sizing)

```
┌──────────────────────────────────────────────────────────┐
│  LFL (Lot for Lot) — 按需下单                            │
│  计划量 = 净需求                                         │
│  适用：高价值物料、MTO 模式                               │
├──────────────────────────────────────────────────────────┤
│  FOQ (Fixed Order Quantity) — 固定批量                   │
│  计划量 = ⌈净需求 / 固定批量⌉ × 固定批量                   │
│  适用：有模具/工装约束的产品                               │
├──────────────────────────────────────────────────────────┤
│  POQ (Period Order Quantity) — 固定期间                   │
│  将 N 个期间的需求合并为一个计划订单                        │
│  适用：采购周期固定的原材料                                │
├──────────────────────────────────────────────────────────┤
│  EOQ (Economic Order Quantity) — 经济批量                 │
│  EOQ = √(2DS/H)                                         │
│  D=年需求量 S=下单成本 H=持有成本                          │
│  适用：标准件、通用原材料                                  │
├──────────────────────────────────────────────────────────┤
│  附加约束：                                               │
│  · min_order_qty: 计划量 = MAX(计划量, 最小起订量)         │
│  · lot_multiple:  计划量 = ⌈计划量/倍数⌉ × 倍数           │
└──────────────────────────────────────────────────────────┘
```

### 2.4 提前期偏移

```
计划订单时间线：

  ←────── 提前期 (lead_time_days) ──────→
  |                                      |
  计划开始日     ← 安全提前期 →      需求日期
  (下单/投产)    (safety_lead_days)   (交期)

实际计算：
  计划完成日 = 需求日期 - safety_lead_days
  计划开始日 = 计划完成日 - lead_time_days

对于自制件，提前期进一步分解：
  制造提前期 = 排队时间 + 准备时间 + 加工时间 + 等待时间 + 转移时间
  
  其中加工时间可由工艺路线精确计算：
  加工时间(工序i) = setup_time(i) + run_time_per_unit(i) × 数量 / 产能
```

## 3. MRP 引擎 Java 类设计

### 3.1 核心类图

```
┌─────────────────────────┐
│     MrpEngineService    │  ← 引擎入口
│─────────────────────────│
│ + runMrp(MrpRunParam)   │
│ + cancelRun(runId)      │
│ + getRunProgress(runId) │
└────────────┬────────────┘
             │ uses
    ┌────────┼────────────────────────┐
    ▼        ▼                        ▼
┌──────────┐ ┌──────────────┐  ┌────────────────┐
│ Demand   │ │ NetRequire   │  │ PlanOrder      │
│ Collector│ │ Calculator   │  │ Generator      │
│          │ │              │  │                │
│·collectMTO│ │·calcGross()  │  │·applyLotSize() │
│·collectMTS│ │·deductStock()│  │·offsetLeadTime│
│·collectSSK│ │·calcNet()    │  │·generateOrder()│
└──────────┘ └──────────────┘  └────────────────┘
                                       │
                              ┌────────┴────────┐
                              ▼                  ▼
                       ┌────────────┐    ┌────────────┐
                       │ BomExploder│    │ Exception  │
                       │            │    │ Handler    │
                       │·explode()  │    │            │
                       │·getLLC()   │    │·leadTimeWarn│
                       │·getChildren│    │·missingBom │
                       └────────────┘    └────────────┘
```

### 3.2 关键数据结构

```java
// MRP 运行参数
public class MrpRunParam {
    private String runType;          // FULL / NET / SELECTIVE
    private Integer planHorizonDays; // 计划展望期
    private List<Long> materialIds;  // SELECTIVE 模式下的物料范围
    private List<Long> demandIds;    // SELECTIVE 模式下的需求范围
    private String runBy;            // 执行人
}

// MRP 时间桶中的物料需求/供给快照
public class MrpBucket {
    private Long materialId;
    private LocalDate bucketDate;    // 时间桶日期
    private BigDecimal grossRequirement;   // 毛需求
    private BigDecimal scheduledReceipts;  // 已确认供给
    private BigDecimal projectedAvailable; // 预计可用库存
    private BigDecimal netRequirement;     // 净需求
    private BigDecimal plannedOrderReceipt; // 计划订单到货
    private BigDecimal plannedOrderRelease; // 计划订单下达
}

// 计划订单
public class PlannedOrder {
    private Long materialId;
    private String orderType;        // MFG / PUR / SUB
    private BigDecimal quantity;
    private LocalDate startDate;
    private LocalDate endDate;
    private String demandSource;     // MTO / MTS / SSK
    private Long sourceDemandId;     // 需求追溯
    private Long parentPlanOrderId;  // 父级追溯 (BOM 展开)
    private int bomLevel;
}
```

### 3.3 MRP 展算核心伪代码

```java
public class MrpEngineService {
    
    public MrpRunResult runMrp(MrpRunParam param) {
        // Step 0: 初始化
        MrpRunLog runLog = initializeRun(param);
        MrpContext ctx = buildContext(param);  // 加载基础数据到内存
        
        try {
            // Step 1: 收集需求
            List<MrpDemand> demands = demandCollector.collect(param);
            
            // Step 2: 计算低阶码，确定处理顺序
            Map<Long, Integer> llcMap = bomExploder.calculateLLC(ctx.getAllBoms());
            List<Long> processingOrder = sortByLLC(llcMap);
            
            // Step 3: 按低阶码逐层展算
            for (Long materialId : processingOrder) {
                int llc = llcMap.get(materialId);
                
                // 3a: 获取该物料的所有毛需求 (独立需求 + 相关需求)
                List<MrpBucket> buckets = getBucketsForMaterial(materialId, demands, ctx);
                
                // 3b: 获取现有供给 (库存 + 在途 + 在制 + 已确认计划订单)
                BigDecimal availableSupply = getAvailableSupply(materialId, ctx);
                
                // 3c: 逐时间桶计算净需求
                BigDecimal runningAvailable = availableSupply;
                BigDecimal safetyStock = ctx.getSafetyStock(materialId);
                
                for (MrpBucket bucket : buckets) {
                    // 预计可用 = 前期结余 + 本期供给 - 本期毛需求
                    runningAvailable = runningAvailable
                        .add(bucket.getScheduledReceipts())
                        .subtract(bucket.getGrossRequirement());
                    
                    // 净需求 = MAX(0, 安全库存 - 预计可用)
                    BigDecimal netReq = safetyStock.subtract(runningAvailable).max(BigDecimal.ZERO);
                    
                    if (netReq.compareTo(BigDecimal.ZERO) > 0) {
                        // 3d: 应用批量策略
                        BigDecimal lotQty = lotSizer.applyLotSizing(
                            netReq, ctx.getMaterial(materialId));
                        
                        // 3e: 提前期偏移
                        LocalDate startDate = leadTimeOffset(
                            bucket.getBucketDate(), ctx.getMaterial(materialId));
                        
                        // 3f: 生成计划订单
                        PlannedOrder order = planOrderGenerator.generate(
                            materialId, lotQty, startDate, bucket.getBucketDate());
                        ctx.addPlannedOrder(order);
                        
                        // 更新可用量
                        runningAvailable = runningAvailable.add(lotQty);
                        
                        // 3g: BOM 展开 → 生成下层相关需求
                        if (order.getOrderType().equals("MFG")) {
                            List<MrpDemand> childDemands = 
                                bomExploder.explode(materialId, lotQty, startDate, ctx);
                            demands.addAll(childDemands);
                        }
                    }
                    
                    bucket.setProjectedAvailable(runningAvailable);
                }
            }
            
            // Step 4: 持久化结果
            savePlanOrders(ctx.getPlannedOrders(), runLog.getRunId());
            savePegging(ctx.getPeggingRecords(), runLog.getRunId());
            
            // Step 5: 生成异常报告
            List<MrpException> exceptions = exceptionHandler.check(ctx);
            
            completeRun(runLog, ctx);
            return new MrpRunResult(runLog, ctx.getPlannedOrders(), exceptions);
            
        } catch (Exception e) {
            failRun(runLog, e);
            throw new MrpEngineException("MRP 运行失败", e);
        }
    }
}
```

## 4. MRP 净变更模式 (Net Change)

完整 MRP 耗时较长，净变更模式只处理发生变化的部分：

```
触发净变更的事件：
  ├── 新增/修改/取消 需求单
  ├── 库存调整 (收货/发货/盘点)
  ├── 采购订单状态变更
  ├── 生产报工 (实绩与计划偏差)
  └── BOM / 工艺路线变更

净变更处理逻辑：
  1. 识别受影响的物料集合
  2. 通过 BOM 向上/向下追溯所有关联物料
  3. 仅对受影响物料重新展算
  4. 保留未受影响的已确认计划订单
```

## 5. MRP 运行性能优化

### 5.1 目标：10,000 条需求行 ≤ 30 秒

| 优化策略 | 具体方案 |
|---------|---------|
| 内存计算 | 运行前将 BOM/库存/需求一次性加载到 HashMap，避免逐条查库 |
| BOM 缓存 | BOM 结构和 LLC 缓存到 Redis，BOM 变更时失效 |
| 批量写入 | 计划订单批量 INSERT (每批 500 条)，使用 SqlBulkCopy |
| 快照隔离 | MRP 运行期间使用 WITH(NOLOCK) 读取库存，避免锁等待 |
| 异步执行 | MRP 提交后在独立线程池执行，前端通过进度接口轮询状态 |
| 分段提交 | 每处理完一个 LLC 层级，批量提交一次，避免大事务 |

### 5.2 SQL Server 2008 兼容注意事项

```
注意事项：
  1. 不使用 SEQUENCE → 改用 IDENTITY 或表模拟
  2. 不使用 STRING_AGG → 改用 FOR XML PATH 拼接字符串
  3. 不使用 OFFSET/FETCH → 改用 ROW_NUMBER() 分页
  4. 不使用 TRY_CONVERT → 使用 CASE + ISNUMERIC
  5. 不使用 FORMAT 函数 → 使用 CONVERT 格式化日期
  6. 不使用 JSON 函数 → 结构化数据使用子表存储
  7. 批量插入使用 Table-Valued Parameters (TVP) 
     或构造 INSERT INTO ... SELECT UNION ALL 语句
```

## 6. MRP 工作台功能设计

### 6.1 MRP 运行面板

```
┌─────────────────────────────────────────────────────────────┐
│  MRP 工作台                                                  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  运行参数：                                                  │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────────────┐ │
│  │运行类型   │  │计划展望期(天) │  │范围                   │ │
│  │[完整重算▼]│  │[   90      ]│  │[全部物料           ▼]│ │
│  └──────────┘  └──────────────┘  └───────────────────────┘ │
│                                                             │
│  [▶ 开始运行]  [⏸ 取消]                                     │
│                                                             │
│  运行进度：████████████░░░░░  65%  处理中: LLC=2, 已处理3500 │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│  最近运行记录                                                │
│  ┌────────┬──────┬──────┬──────┬──────┬──────┬───────────┐ │
│  │批次号   │类型  │状态  │需求数 │计划数 │采购数 │运行时间    │ │
│  ├────────┼──────┼──────┼──────┼──────┼──────┼───────────┤ │
│  │MRP24001│完整  │完成  │2350  │1580  │890   │18s        │ │
│  │MRP24002│净变更│完成  │120   │85    │42    │2s         │ │
│  └────────┴──────┴──────┴──────┴──────┴──────┴───────────┘ │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│  计划订单审核                                                │
│  ┌────────┬──────┬──────┬────┬────────┬────────┬────────┐ │
│  │订单号   │物料  │类型  │数量│开始日期 │完成日期 │状态    │ │
│  ├────────┼──────┼──────┼────┼────────┼────────┼────────┤ │
│  │PO-001  │管材A │制造  │50T │03-20   │03-25   │待确认  │ │
│  │PO-002  │钢卷B │采购  │80T │03-18   │03-22   │待确认  │ │
│  └────────┴──────┴──────┴────┴────────┴────────┴────────┘ │
│                                                             │
│  [✓ 批量确认]  [✗ 批量取消]  [🔒 锁定选中]  [📋 导出]       │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│  异常与建议                                                  │
│  ⚠ PO-003: 提前期不足，计划开始日 < 今日，建议加急采购        │
│  ⚠ PO-007: 物料 C 无有效BOM，请检查基础数据                  │
│  ℹ PO-012: 可用替代物料 D，建议启用替代方案                   │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 需求-供给追溯 (Pegging)

```
需求追溯视图：

  需求单 DEM-001 (合同订单, 客户:XXX公司)
    └─ 需求行 #1: 方管 100×50×4.0 — 20T, 交期 04-05
         │
         ├── 供给1: 库存可用 8T (仓库 A)
         ├── 供给2: 在制 5T (排产单 SCH-0042)
         └── 供给3: 计划订单 PO-001 — 7T (待确认)
                │
                └── BOM 展开:
                    ├── 钢卷 Q235B 4.0×1500 — 需 7.35T (含5%损耗)
                    │   ├── 供给: 库存可用 3T
                    │   └── 供给: 采购建议 PUR-005 — 4.35T
                    └── 镀锌液 — 需 0.2T
                        └── 供给: 库存充足
```
