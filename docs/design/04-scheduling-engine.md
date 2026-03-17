# AiAPS — 排产调度引擎设计

> 版本：2.0 | 最后更新：2026-03-17
>
> **重要：** 多阶段排产（原料层排产→制造层排产）、一分X 分剪套裁排产、
> 原料驱动型排产等钢铁行业核心增强设计请参见 **[08-steel-industry-adaptation.md](08-steel-industry-adaptation.md)** 第 3~5 节。
>
> 模具管理与产能约束、替代料逻辑、同规格不同壁厚分组排产、产出物料流向标注等增强设计
> 请参见 **[09-mold-substitute-flow.md](09-mold-substitute-flow.md)**。

---

## 1. 排产引擎定位

MRP 引擎解决"生产什么、生产多少、何时开始"的问题，排产引擎则进一步解决"在哪条产线/机台生产、以什么顺序生产、如何避免资源冲突"的问题。

```
MRP 输出（计划订单） ──→ 排产引擎 ──→ 可执行的工序级排产计划
                                          │
                                          ├── 甘特图展示
                                          ├── 产能负荷分析
                                          └── 生产工单下达
```

## 2. 排产调度流程

### 2.1 排产主流程

```
┌─────────────────────────────────┐
│ Step 1: 加载排产输入             │
│  · 已确认的 MRP 计划订单         │
│  · 已锁定的现有排产计划          │
│  · 工作中心产能日历              │
│  · 换产时间矩阵                 │
│  · 物料工艺路线                 │
└──────────────┬──────────────────┘
               │
┌──────────────▼──────────────────┐
│ Step 2: 任务拆分与工序展开       │
│  · 计划订单 → 排产任务           │
│  · 排产任务 × 工艺路线 → 工序列表│
│  · 计算每道工序的标准加工时间     │
└──────────────┬──────────────────┘
               │
┌──────────────▼──────────────────┐
│ Step 3: 优先级排序               │
│  · 综合评分 = f(交期紧急度,       │
│    客户优先级, 需求来源, 物料价值) │
│  · 排序生成初始调度序列           │
└──────────────┬──────────────────┘
               │
┌──────────────▼──────────────────┐
│ Step 4: 有限产能排产             │
│  · 正向/倒排策略选择             │
│  · 工序逐一分配到工作中心时间槽   │
│  · 考虑换产时间                  │
│  · 考虑工序间转移/等待           │
│  · 检查并行约束                  │
└──────────────┬──────────────────┘
               │
┌──────────────▼──────────────────┐
│ Step 5: 冲突检测与解决           │
│  · 资源冲突（同一工作中心重叠）   │
│  · 交期冲突（排产完成 > 交期）    │
│  · 产能瓶颈（负荷率 > 100%）     │
│  · 自动/人工解决                 │
└──────────────┬──────────────────┘
               │
┌──────────────▼──────────────────┐
│ Step 6: 结果输出                 │
│  · 更新 aps_schedule             │
│  · 更新 aps_schedule_oper        │
│  · 更新 aps_wc_load              │
│  · 生成甘特图数据                │
│  · 生成异常报告                  │
└─────────────────────────────────┘
```

### 2.2 正向排产 vs 倒排

```
┌─────────────────────────────────────────────────────────┐
│  正向排产 (Forward Scheduling)                           │
│                                                         │
│  从"今天"或"最早可用日期"向前推                            │
│                                                         │
│  今天      工序10    工序20    工序30     完成日           │
│  ├─────────┤────────┤────────┤─────────┤                │
│  ▲                                      ▲               │
│  起点                              可能早于/晚于交期      │
│                                                         │
│  适用场景：产能紧张、尽早开工                              │
│  优点：最大化产能利用率                                   │
│  缺点：可能导致库存积压                                   │
├─────────────────────────────────────────────────────────┤
│  倒排 (Backward Scheduling)                              │
│                                                         │
│  从"交期"向回推                                          │
│                                                         │
│  开始日      工序10    工序20    工序30    交期            │
│  ├──────────┤────────┤────────┤────────┤                │
│  ▲                                      ▲               │
│  可能早于/晚于今天                     终点(锚定)         │
│                                                         │
│  适用场景：MTO 订单、交期明确                              │
│  优点：减少库存积压，JIT 思想                              │
│  缺点：可能开始日期已过期                                  │
├─────────────────────────────────────────────────────────┤
│  混合策略（推荐）                                         │
│                                                         │
│  1. MTO 订单 → 倒排（以交期为锚）                         │
│  2. 如果倒排后开始日期 < 今天 → 切换正向排产               │
│  3. MTS/SSK → 正向排产（尽量填满产能空隙）                 │
│  4. 优先级高的先排，低的填缝隙                             │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

## 3. 优先级评分模型

### 3.1 综合优先级计算

```
Priority Score = W1 × 交期紧急度 + W2 × 客户权重 + W3 × 需求类型权重 + W4 × 物料价值权重

其中：

交期紧急度 = MAX(0, 100 - (交期 - 今天) × K1)
  · 交期越近分越高，已过期的 = 100

客户权重 = 客户等级映射
  · VIP = 90, A级 = 70, B级 = 50, C级 = 30

需求类型权重:
  · MTO(订货合同) = 80   ← 有明确交期和违约风险
  · MTS(期货订单) = 50   ← 有一定灵活度
  · SSK(安全库存) = 30   ← 优先级最低

物料价值权重:
  · 高价值(吨单价>5000) = 70
  · 中等 = 50
  · 低价值 = 30

推荐权重分配: W1=0.4, W2=0.2, W3=0.25, W4=0.15
```

### 3.2 优先级可人工覆盖

```
系统计算的优先级仅作为初始排序依据。
用户可以在排产甘特图中：
  1. 手动设置排产任务的优先级 (1~100)
  2. 手动锁定某个任务的时间位置 (is_locked = true)
  3. 手动指定某个任务必须在特定工作中心执行
  
被锁定的任务在重新排产时不会被移动。
```

## 4. 有限产能排产算法

### 4.1 核心排产算法：基于优先级的列表调度

```java
/**
 * 有限产能排产算法 — 基于优先级的列表调度 (Priority-based List Scheduling)
 * 
 * 选择此算法的原因：
 * 1. 实现复杂度适中，可维护性好
 * 2. 排产结果可解释（用户能理解为什么这样排）
 * 3. 支持人工调整后增量重排
 * 4. 性能满足要求（1000个任务 < 5秒）
 */
public class FiniteCapacityScheduler {
    
    public ScheduleResult schedule(ScheduleInput input) {
        // 1. 收集所有待排工序任务
        List<OperTask> tasks = expandOperations(input.getPlanOrders());
        
        // 2. 分离锁定任务和可调度任务
        List<OperTask> lockedTasks = filterLocked(tasks);
        List<OperTask> freeTasks = filterFree(tasks);
        
        // 3. 初始化工作中心时间线（已扣除锁定任务占用的时间）
        Map<Long, WorkCenterTimeline> timelines = 
            initTimelines(input.getCapacityCalendar(), lockedTasks);
        
        // 4. 对可调度任务按优先级排序
        freeTasks.sort(Comparator.comparing(OperTask::getPriorityScore).reversed());
        
        // 5. 逐任务分配
        for (OperTask task : freeTasks) {
            // 5a. 确定该工序可选的工作中心列表
            List<Long> candidateWCs = getCandidateWorkCenters(task);
            
            // 5b. 确定前置工序约束（最早可开始时间）
            DateTime earliestStart = getEarliestStart(task);
            
            // 5c. 在每个候选工作中心找最早可用时间槽
            TimeSlot bestSlot = null;
            for (Long wcId : candidateWCs) {
                WorkCenterTimeline tl = timelines.get(wcId);
                
                // 计算换产时间
                int setupMinutes = getSetupTime(
                    tl.getLastMaterialGroup(), task.getMaterialGroup(), wcId);
                
                // 计算加工时间
                int processMinutes = calcProcessTime(task, wcId);
                
                // 找到最早可用的连续时间槽
                TimeSlot slot = tl.findEarliestSlot(
                    earliestStart, setupMinutes + processMinutes);
                
                if (bestSlot == null || slot.getStart().isBefore(bestSlot.getStart())) {
                    bestSlot = slot;
                    task.setAssignedWcId(wcId);
                }
            }
            
            // 5d. 分配时间槽
            task.setSetupStart(bestSlot.getStart());
            task.setSetupEnd(bestSlot.getStart().plusMinutes(setupMinutes));
            task.setOperStart(task.getSetupEnd());
            task.setOperEnd(bestSlot.getEnd());
            
            // 5e. 更新工作中心时间线
            timelines.get(task.getAssignedWcId()).occupy(bestSlot);
        }
        
        // 6. 冲突检测
        List<Conflict> conflicts = detectConflicts(tasks, input);
        
        // 7. 构建结果
        return new ScheduleResult(tasks, conflicts);
    }
}
```

### 4.2 工作中心时间线模型

```
工作中心 WC-001 (焊管产线1) 的时间线示例：

日期: 2026-03-20
├── SHIFT1 (08:00~16:00) ── 可用 8h
│   ├── [08:00~08:30] 换规格 (SCH-001 → SCH-002)
│   ├── [08:30~12:00] SCH-002 方管100×50 — 15T 🔒已锁定
│   ├── [12:00~12:20] 换规格
│   ├── [12:20~16:00] SCH-003 方管80×40 — 12T
│   └── 剩余可用: 0h
│
├── SHIFT2 (16:00~24:00) ── 可用 8h
│   ├── [16:00~20:00] SCH-003 续 — 8T
│   ├── [20:00~20:15] 换规格
│   ├── [20:15~23:00] SCH-005 圆管Φ89 — 10T
│   └── 剩余可用: 1h
│
└── SHIFT3 (00:00~08:00) ── 可用 8h (下一天凌晨)
    ├── [00:00~06:00] SCH-007 圆管Φ114 — 20T
    └── 剩余可用: 2h

时间线数据结构：
  WorkCenterTimeline {
      wcId: Long
      slots: TreeMap<DateTime, TimeSlot>  // 按时间排序的时间槽
      
      findEarliestSlot(after, durationMinutes): TimeSlot
      occupy(slot): void
      release(slot): void
      getLoadRate(date): double
  }
```

### 4.3 换产时间优化

钢铁行业的换产时间（换辊、换模）对排产效率影响显著。系统采用"同规格/近似规格合并"策略减少换产次数：

```
换产时间矩阵示例 (焊管产线)：

          → 方管100×50  方管80×40  圆管Φ89  圆管Φ114
方管100×50      0          20min     45min    45min
方管80×40     20min         0        45min    45min
圆管Φ89      45min        45min       0       30min
圆管Φ114     45min        45min     30min       0

优化策略：
  1. 同类型产品尽量连续排（方管→方管，圆管→圆管）
  2. 相近规格优先相邻排（Φ89 → Φ114 比 Φ89 → 方管100 换产时间短）
  3. 使用贪心算法或 TSP 近似算法优化同一工作中心内的排序
```

### 4.4 排产优化目标

```
多目标优化（按优先级排序）：

  1. 交期满足率 — 最大化按时交付的订单比例
     minimize Σ max(0, 完成日 - 交期)

  2. 换产时间最小化 — 减少非生产时间
     minimize Σ setup_time

  3. 产能利用率最大化 — 减少空闲时间
     maximize Σ loaded_hours / available_hours

  4. 在制品库存最小化 — 工序间等待时间短
     minimize Σ (工序i+1.start - 工序i.end)

实际策略：
  由于精确多目标优化计算量太大，系统采用
  "优先级列表调度 + 局部优化"的两阶段策略：
  
  阶段1: 按优先级逐任务贪心调度（保证交期高优先级）
  阶段2: 对同一工作中心内的任务序列做局部重排
         （相邻交换法减少换产时间）
```

## 5. 排产调整操作设计

### 5.1 支持的调整操作

| 操作 | 说明 | 实现方式 |
|-----|------|---------|
| **拖拽移动** | 将排产任务从一个时间位置拖到另一个 | 更新 oper_start/oper_end，自动级联后续任务 |
| **跨产线移动** | 将任务从产线 A 移到产线 B | 更新 wc_id，重新计算加工时间 |
| **插单** | 在已排满的排程中插入紧急任务 | 选定插入点，后续任务自动后移 |
| **拆分** | 将一个大任务拆分到多个时间段或多条产线 | 生成子任务，数量按比例分配 |
| **合并** | 将多个同物料任务合并为一个 | 合并数量，取最早交期 |
| **锁定/解锁** | 锁定后不参与自动重排 | 设置 is_locked 标志 |
| **调整优先级** | 人工设定优先级 | 直接修改 priority 字段 |
| **取消** | 取消排产任务 | 设置状态为 CANCELLED，释放产能 |

### 5.2 调整后的自动级联

```
用户操作：将 SCH-002 向后推迟 2 小时

系统自动处理：
  1. 更新 SCH-002 的 oper_start / oper_end (+2h)
  2. 检查 SCH-002 的后续工序是否需要级联调整
  3. 检查同一工作中心中排在 SCH-002 后面的其他任务
  4. 如果产生时间重叠 → 自动将后续任务向后推移
  5. 如果没有冲突 → 仅调整 SCH-002 自身
  6. 重新计算工作中心负荷率
  7. 记录调整日志 (aps_schedule_change_log)

级联规则：
  · 锁定的任务不被级联推移
  · 如果锁定任务与被推移任务冲突 → 报告冲突，等待人工处理
  · 级联最多影响 N 层（可配置，默认 5 层），超出报告
```

### 5.3 冲突检测

```java
public class ConflictDetector {
    
    public List<Conflict> detect(List<ScheduledOper> operations) {
        List<Conflict> conflicts = new ArrayList<>();
        
        // 1. 资源冲突：同一工作中心同一时间被多个任务占用
        Map<Long, List<ScheduledOper>> byWc = groupByWorkCenter(operations);
        for (Map.Entry<Long, List<ScheduledOper>> entry : byWc.entrySet()) {
            List<ScheduledOper> wcOps = entry.getValue();
            wcOps.sort(Comparator.comparing(ScheduledOper::getOperStart));
            for (int i = 0; i < wcOps.size() - 1; i++) {
                if (wcOps.get(i).getOperEnd().isAfter(wcOps.get(i+1).getOperStart())) {
                    conflicts.add(new Conflict(
                        ConflictType.RESOURCE_OVERLAP,
                        wcOps.get(i), wcOps.get(i+1)));
                }
            }
        }
        
        // 2. 交期冲突：排产完成时间 > 需求交期
        for (ScheduledOper op : operations) {
            if (op.isLastOper() && op.getOperEnd().isAfter(op.getDueDate())) {
                conflicts.add(new Conflict(
                    ConflictType.DUE_DATE_VIOLATION, op, null));
            }
        }
        
        // 3. 产能超载：工作中心负荷率 > 100%
        Map<String, Double> loadRates = calculateLoadRates(operations);
        for (Map.Entry<String, Double> entry : loadRates.entrySet()) {
            if (entry.getValue() > 1.0) {
                conflicts.add(new Conflict(
                    ConflictType.CAPACITY_OVERLOAD, 
                    entry.getKey(), entry.getValue()));
            }
        }
        
        // 4. 工序顺序冲突：后工序开始时间 < 前工序结束时间
        // ...
        
        return conflicts;
    }
}
```

### 5.4 冲突自动解决策略

```
┌────────────────────┬──────────────────────────────────────┐
│ 冲突类型           │ 自动解决策略                          │
├────────────────────┼──────────────────────────────────────┤
│ 资源重叠           │ 低优先级任务自动后移到最近可用时间槽   │
│                    │ 如有替代工作中心则尝试分配到替代       │
├────────────────────┼──────────────────────────────────────┤
│ 交期违反           │ 1. 尝试切换到替代工作中心             │
│                    │ 2. 尝试正向排产提前开工               │
│                    │ 3. 标记为"需人工处理"并高亮显示        │
├────────────────────┼──────────────────────────────────────┤
│ 产能超载           │ 1. 建议加班(延长可用工时)              │
│                    │ 2. 建议部分任务移至替代工作中心        │
│                    │ 3. 建议调整低优先级任务到后续日期      │
├────────────────────┼──────────────────────────────────────┤
│ 物料不齐套         │ 标记为"待物料"，排产起始时间设为       │
│                    │ 预计物料到位日期                      │
└────────────────────┴──────────────────────────────────────┘
```

## 6. 排产引擎 Java 类设计

### 6.1 核心类图

```
┌──────────────────────────┐
│  ScheduleEngineService   │ ← 引擎入口
│──────────────────────────│
│ + autoSchedule(param)    │
│ + reschedule(param)      │
│ + adjustSchedule(adj)    │
│ + insertOrder(insert)    │
│ + lockSchedule(ids)      │
│ + getGanttData(query)    │
└───────────┬──────────────┘
            │ uses
   ┌────────┼──────────────────────────────┐
   ▼        ▼                              ▼
┌──────────┐ ┌──────────────────┐  ┌────────────────┐
│ Operation│ │ FiniteCapacity   │  │  Conflict      │
│ Expander │ │ Scheduler        │  │  Detector      │
│          │ │                  │  │                │
│·expand() │ │·schedule()       │  │·detect()       │
│·calcTime │ │·findSlot()       │  │·autoResolve()  │
│          │ │·optimizeSetup()  │  │                │
└──────────┘ └──────────────────┘  └────────────────┘
                     │
            ┌────────┼────────────┐
            ▼        ▼            ▼
     ┌──────────┐ ┌────────┐ ┌────────────────┐
     │WorkCenter│ │Priority│ │ Schedule       │
     │Timeline  │ │Scorer  │ │ Adjuster       │
     │Manager   │ │        │ │                │
     │          │ │·score()│ │·move()         │
     │·init()   │ │        │ │·split()        │
     │·occupy() │ │        │ │·merge()        │
     │·release()│ │        │ │·cascadeAdjust()│
     │·findSlot│ │        │ │·insertOrder()  │
     └──────────┘ └────────┘ └────────────────┘
```

### 6.2 排产调整接口设计 (REST API)

```
# 自动排产
POST /api/aps/schedule/auto
Body: {
    "planOrderIds": [1001, 1002, 1003],  // 待排的计划订单
    "strategy": "MIXED",                  // FORWARD/BACKWARD/MIXED
    "respectLocked": true,                // 是否保留已锁定任务
    "optimizeSetup": true                 // 是否优化换产序列
}
Response: {
    "scheduleCount": 15,
    "operCount": 48,
    "conflicts": [...],
    "warnings": [...]
}

# 拖拽调整
PUT /api/aps/schedule/move
Body: {
    "scheduleId": 5001,
    "newStart": "2026-03-21T08:00:00",
    "newWcId": 101,          // 可选，跨产线时指定
    "cascadeMode": "AUTO"    // AUTO/MANUAL/NONE
}

# 插单
POST /api/aps/schedule/insert
Body: {
    "materialId": 2001,
    "qty": 20,
    "dueDate": "2026-03-22",
    "insertAfterScheduleId": 5001,  // 插入到哪个任务之后
    "wcId": 101,
    "priority": 95,
    "demandSource": "MTO",
    "sourceDocNo": "HT-2026-0089"
}

# 拆分任务
POST /api/aps/schedule/split
Body: {
    "scheduleId": 5001,
    "splitPlan": [
        {"wcId": 101, "qty": 30},
        {"wcId": 102, "qty": 20}
    ]
}

# 锁定/解锁
PUT /api/aps/schedule/lock
Body: {
    "scheduleIds": [5001, 5002],
    "locked": true,
    "lockReason": "客户确认交期不可变"
}

# 甘特图数据查询
GET /api/aps/gantt?wcIds=101,102&dateFrom=2026-03-20&dateTo=2026-03-27&view=WC

# 工作中心负荷查询
GET /api/aps/wc-load?wcIds=101,102&dateFrom=2026-03-20&dateTo=2026-03-27
```

## 7. 钢铁行业专属排产规则

### 7.1 管材排产规则

```
规则1: 同材质连续排产
  · 同一材质(如 Q235B)的产品尽量连续排，减少换材质时间

规则2: 规格从大到小排
  · 在同材质内，按外径从大到小排（或壁厚从厚到薄）
  · 大规格→小规格的换辊时间 < 小→大

规则3: 最小批量约束
  · 焊管产线最小生产批量 = 5T（低于此数量换产不经济）
  · 系统自动合并同规格小批量需求

规则4: 钢卷定尺约束
  · 一个钢卷开卷后必须连续生产完，不能中途换规格
  · 排产时需考虑钢卷重量与产品数量的匹配
```

### 7.2 开平/剪切排产规则

```
规则1: 套裁优化
  · 同一厚度的开平/剪切订单尽量合并套裁
  · 减少余料浪费

规则2: 来料到达约束
  · 开平/剪切依赖来料（钢卷到货）
  · 排产开始时间 >= 来料预计到达时间

规则3: 宽度优先
  · 先切宽的，再切窄的（余料可再利用）
```

### 7.3 车架/车梁排产规则 (离散制造)

```
规则1: 装配拉动
  · 从最终装配工序倒排，拉动前工序
  · 确保各零部件在装配前完工

规则2: 齐套检查
  · 装配前检查所有子件是否齐套
  · 不齐套的装配任务延迟到齐套日期

规则3: 焊接工位分组
  · 相同焊接夹具的产品分组排
  · 减少夹具切换时间
```

## 8. 增量重排策略

### 8.1 何时触发重排

```
自动触发：
  ├── 新 MRP 计划订单确认
  ├── 紧急插单
  ├── 设备故障/停机
  ├── 关键物料延迟
  └── 报工进度偏差超阈值(如实际 < 计划的80%)

手动触发：
  ├── 用户点击"重新排产"
  └── 用户调整参数后重排
```

### 8.2 增量重排算法

```
增量重排 vs 完全重排：

完全重排：
  · 清除所有未锁定的排产计划
  · 从头计算
  · 适用于基础数据大幅变更

增量重排（推荐日常使用）：
  1. 保留所有 is_locked = true 的任务
  2. 保留所有 oper_status IN ('RUNNING', 'COMPLETED') 的工序
  3. 仅将 oper_status = 'PLANNED' 且 is_locked = false 的任务重新排产
  4. 新加入的计划订单与未锁定任务混合重排
  5. 结果与已锁定/已执行的任务做冲突检测
```

## 9. 排产引擎性能指标

| 场景 | 任务量 | 目标耗时 |
|------|--------|---------|
| 自动排产(全量) | 500 个排产任务 × 3 道工序 | ≤ 10 秒 |
| 增量重排 | 50 个新任务 + 200 个已有任务 | ≤ 3 秒 |
| 拖拽调整 | 单个任务 + 级联检查 | ≤ 500 毫秒 |
| 插单 | 1 个任务 + 冲突检测 | ≤ 1 秒 |
| 甘特图数据查询 | 7 天 × 10 条产线 | ≤ 1 秒 |
