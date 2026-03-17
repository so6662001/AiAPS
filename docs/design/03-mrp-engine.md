# AiAPS — MRP 展算引擎设计

> **版本：3.0（最终版）** | 最后更新：2026-03-17
>
> **V3.0 重大修正：**
> 1. 低阶码(LLC)计算同时遍历离散BOM + 品类BOM
> 2. 所有核心实体(MrpDemand/MrpBucket/PlannedOrder等)全面增加材质/产地/重量
> 3. 需求合并/库存匹配/净需求计算以 (物料+材质+产地) 为维度
> 4. MRP工作台UI在物料后面展示材质与产地

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
                    ┌──────────────────────────────┐
                    │  Step 0: 初始化与参数校验       │
                    │  · 创建 mrp_run_log 记录       │
                    │  · 加载物料/离散BOM/品类BOM     │
                    │  · 加载库存(按物料+材质+产地)    │
                    │  · 确定计划展望期               │
                    └───────────┬──────────────────┘
                                │
                    ┌───────────▼──────────────────┐
                    │  Step 1: 需求收集与合并         │
                    │  · 需求维度=(物料+材质+产地)    │
                    │  · MTO: 按合同行独立(不合并)    │
                    │  · MTS: 同(物料+材质+产地+周期) │
                    │    才可合并                     │
                    │  · SSK: 按(物料+材质)检查安全库存│
                    └───────────┬──────────────────┘
                                │
                    ┌───────────▼──────────────────┐
                    │  Step 2: 双轨BOM低阶码计算      │
                    │  · 遍历离散BOM(物料级)          │
                    │  · 遍历品类BOM(品类级)          │
                    │  · 合并计算每个物料/品类的LLC    │
                    │  · 确定展算顺序                 │
                    └───────────┬──────────────────┘
                                │
                    ┌───────────▼──────────────────┐
                    │  Step 3: 按LLC逐层展算          │
                    │  · 展算维度=(物料+材质+产地)    │
                    │  · 库存匹配按(物料+材质+产地)   │
                    │  · 净需求计算按重量             │
                    │  · BOM展开区分离散/品类         │
                    │  · 品类BOM用公式推算原料规格     │
                    │  · 材质/产地向下继承            │
                    └───────────┬──────────────────┘
                                │
                    ┌───────────▼──────────────────┐
                    │  Step 4: 输出(含材质/产地/重量)  │
                    │  · 计划订单带材质+产地+重量     │
                    │  · 采购建议带材质+产地+重量     │
                    │  · 需求追溯带材质+产地          │
                    └───────────┬──────────────────┘
                                │
                    ┌───────────▼──────────────────┐
                    │  Step 5: 异常与建议             │
                    └──────────────────────────────┘
```

---

## 2. 双轨 BOM 低阶码 (LLC) 计算

### 2.1 问题：品类 BOM 如何计算低阶码

```
传统LLC只考虑离散BOM (物料→物料):
  成品 方管100×50×4.0 (LLC=0)
    └── 原料 带钢290×4.0 (LLC=1)   ← 离散BOM中有精确子项

品类BOM的问题:
  品类BOM定义的是: 方管(品类) → 带钢(品类)
  但物料主数据中方管有很多规格: 100×50×4.0, 80×40×3.0, 120×60×5.0 ...
  带钢也有很多规格: 290×4.0, 237×3.0, 365×5.0 ...

  品类BOM不关联具体物料, 那LLC怎么算?

解决方案: 品类级LLC + 物料级LLC 合并计算
```

### 2.2 双轨 LLC 计算算法

```
算法:

  Phase 1: 计算品类级LLC (从品类BOM)
  ─────────────────────────────────────────────
    1. 初始化所有品类 categoryLLC = 0
    2. 遍历所有品类BOM关系 (bas_category_bom)
    3. 对于每条关系 (父品类 → 子品类):
       子品类.categoryLLC = MAX(子品类.categoryLLC, 父品类.categoryLLC + 1)
    4. 重复直到无变化

    示例:
      RECT_PIPE 方管(categoryLLC=0)
        → STRIP 带钢(categoryLLC=1)
          → COIL 钢卷(categoryLLC=2)  (如果带钢由钢卷分剪)

  Phase 2: 计算物料级LLC (从离散BOM)
  ─────────────────────────────────────────────
    传统算法, 遍历 bas_bom_head + bas_bom_detail
    
    示例:
      车架总成(materialLLC=0)
        → 纵梁(materialLLC=1)
          → 板材16mm(materialLLC=2)

  Phase 3: 合并为最终LLC
  ─────────────────────────────────────────────
    对每个物料:
      如果该物料有离散BOM子项 → 使用 materialLLC
      如果该物料属于某品类 → 取 MAX(materialLLC, categoryLLC)
      
    例如:
      车架总成          LLC=0 (来自离散BOM)
        → 纵梁          LLC=1 (来自离散BOM)
          → 板材16mm    LLC=2 (来自离散BOM)
            → (品类BOM: PLATE → COIL)
              → 钢卷    LLC=3 (品类级LLC=2, 但板材离散LLC=2, 钢卷=2+1=3)

      方管100×50×4.0    LLC=0 (品类RECT_PIPE, categoryLLC=0)
        → (品类BOM: RECT_PIPE → STRIP)
          → 带钢290×4.0 LLC=1 (品类STRIP, categoryLLC=1)
            → (品类BOM: STRIP → COIL, 如有分剪关系)
              → 钢卷    LLC=2 (品类COIL, categoryLLC=2)
```

### 2.3 LLC 计算 Java 实现

```java
public class DualBomLlcCalculator {

    /**
     * LLC 计算结果: 可以是物料级也可以是品类级
     */
    public static class LlcEntry {
        private String type;        // MATERIAL / CATEGORY
        private Long materialId;    // type=MATERIAL 时有值
        private String categoryCode; // type=CATEGORY 时有值
        private int llcLevel;
    }
    
    /**
     * 计算双轨LLC
     */
    public Map<String, Integer> calculateDualLLC(
            List<BomHead> discreteBoms,       // 离散BOM
            List<CategoryBom> categoryBoms) { // 品类BOM
        
        // === Phase 1: 品类级LLC ===
        Map<String, Integer> categoryLlc = new HashMap<>();
        // 初始化所有品类LLC=0
        for (CategoryBom cb : categoryBoms) {
            categoryLlc.putIfAbsent(cb.getParentCategory(), 0);
            categoryLlc.putIfAbsent(cb.getChildCategory(), 0);
        }
        // 迭代计算
        boolean changed = true;
        while (changed) {
            changed = false;
            for (CategoryBom cb : categoryBoms) {
                int parentLlc = categoryLlc.get(cb.getParentCategory());
                int childLlc = categoryLlc.get(cb.getChildCategory());
                if (parentLlc + 1 > childLlc) {
                    categoryLlc.put(cb.getChildCategory(), parentLlc + 1);
                    changed = true;
                }
            }
        }
        
        // === Phase 2: 物料级LLC ===
        Map<Long, Integer> materialLlc = new HashMap<>();
        for (BomHead bom : discreteBoms) {
            materialLlc.putIfAbsent(bom.getMaterialId(), 0);
            for (BomDetail detail : bom.getDetails()) {
                materialLlc.putIfAbsent(detail.getChildMaterialId(), 0);
            }
        }
        changed = true;
        while (changed) {
            changed = false;
            for (BomHead bom : discreteBoms) {
                int parentLlc = materialLlc.get(bom.getMaterialId());
                for (BomDetail detail : bom.getDetails()) {
                    int childLlc = materialLlc.get(detail.getChildMaterialId());
                    if (parentLlc + 1 > childLlc) {
                        materialLlc.put(detail.getChildMaterialId(), parentLlc + 1);
                        changed = true;
                    }
                }
            }
        }
        
        // === Phase 3: 合并 ===
        // 每个物料的最终LLC = MAX(自身离散LLC, 所属品类的categoryLLC)
        Map<String, Integer> finalLlc = new HashMap<>();
        
        // 所有有离散BOM关系的物料
        for (Map.Entry<Long, Integer> entry : materialLlc.entrySet()) {
            Material mat = materialMapper.selectById(entry.getKey());
            int catLlc = categoryLlc.getOrDefault(mat.getCategoryCode(), 0);
            int matLlc = entry.getValue();
            String key = "M:" + entry.getKey(); // M:物料ID
            finalLlc.put(key, Math.max(matLlc, catLlc));
        }
        
        // 只在品类BOM中出现但无离散BOM的品类
        for (Map.Entry<String, Integer> entry : categoryLlc.entrySet()) {
            String key = "C:" + entry.getKey(); // C:品类编码
            finalLlc.putIfAbsent(key, entry.getValue());
        }
        
        return finalLlc;
    }
}
```

---

## 3. 核心数据结构（V3.0 含材质/产地/重量）

### 3.1 MRP 需求

```java
/**
 * MRP 需求 — 贯穿材质+产地+重量
 * 维度: (物料 + 材质 + 产地) 构成一个独立的需求流
 */
public class MrpDemand {
    private Long materialId;           // 物料ID
    private String gradeCode;          // 材质(必填) — Q235B
    private String originCode;         // 产地(可选) — 鞍钢/NULL
    private boolean gradeFlexible;     // 材质是否允许替代
    private boolean originFlexible;    // 产地是否不限
    
    private BigDecimal quantity;       // 需求数量(主单位)
    private BigDecimal weight;         // 需求重量(吨) ← 核心
    
    private LocalDate requiredDate;    // 需求日期
    private String demandSource;       // MTO/MTS/SSK
    private Long sourceDemandLineId;   // 溯源需求行ID
    private String customerCode;       // 客户编码(用于替代料客户偏好)
    
    // 品类BOM展开时的附加信息
    private String categoryCode;       // 物料品类
    private boolean fromCategoryBom;   // 是否来自品类BOM展开
    private Long parentDemandId;       // 父级需求(BOM展开追溯)
}
```

### 3.2 MRP 时间桶

```java
/**
 * MRP 时间桶 — 以 (物料+材质+产地) 为维度
 * 同一个物料、不同材质 = 不同的时间桶序列
 */
public class MrpBucket {
    // ═══ 维度键 ═══
    private Long materialId;
    private String gradeCode;          // 材质
    private String originCode;         // 产地(NULL=不限产地, 合并所有产地)
    
    private LocalDate bucketDate;      // 时间桶日期
    
    // ═══ 数量(主单位) ═══
    private BigDecimal grossRequirement;     // 毛需求
    private BigDecimal scheduledReceipts;    // 已确认供给
    private BigDecimal projectedAvailable;   // 预计可用
    private BigDecimal netRequirement;       // 净需求
    private BigDecimal plannedOrderReceipt;  // 计划到货
    private BigDecimal plannedOrderRelease;  // 计划下达
    
    // ═══ 重量(吨) — 钢铁核心 ═══
    private BigDecimal grossWeight;          // 毛需求重量
    private BigDecimal scheduledWeight;      // 已确认供给重量
    private BigDecimal projectedWeight;      // 预计可用重量
    private BigDecimal netWeight;            // 净需求重量
    private BigDecimal plannedWeight;        // 计划订单重量
    
    /**
     * 时间桶的唯一键
     * 同物料不同材质 → 不同时间桶
     */
    public String getBucketKey() {
        return materialId + "|" + gradeCode + "|" 
               + (originCode != null ? originCode : "*");
    }
}
```

### 3.3 计划订单

```java
/**
 * 计划订单 — 完整的材质+产地+重量
 */
public class PlannedOrder {
    private Long materialId;
    private String materialCode;       // 物料编码(展示用)
    private String materialName;       // 物料名称(展示用)
    private String specDesc;           // 规格描述(展示用)
    private String categoryCode;       // 品类
    
    // ═══ 材质与产地(必填) ═══
    private String gradeCode;          // 材质 — 从需求继承
    private String originCode;         // 产地 — 从需求继承(可为空=不限)
    private boolean gradeFlexible;
    private boolean originFlexible;
    
    private String orderType;          // MFG / PUR / SUB
    
    // ═══ 数量与重量 ═══
    private BigDecimal quantity;       // 计划数量(主单位)
    private BigDecimal weight;         // 计划重量(吨) ← 核心
    
    private LocalDate startDate;
    private LocalDate endDate;
    private String demandSource;       // MTO / MTS / SSK
    private Long sourceDemandId;
    private Long sourceDemandLineId;
    private String customerCode;       // 客户编码
    private Long parentPlanOrderId;
    private int bomLevel;
    
    // ═══ 品类BOM展开信息 ═══
    private boolean isCategoryBom;     // 是否品类BOM展开
    private String rawCategoryCode;    // 原料品类
    private BigDecimal rawWidthMin;    // 原料宽度下限
    private BigDecimal rawWidthMax;    // 原料宽度上限
    private BigDecimal rawThicknessMin;
    private BigDecimal rawThicknessMax;
}
```

### 3.4 需求合并策略（V3.0 修正）

```java
/**
 * 需求合并规则 — V3.0: 维度=(物料+材质+产地)
 *
 * 核心原则: 不同材质的需求绝不合并!
 * Q235B 的 50T 和 Q345B 的 30T → 必须分开计算
 */
public class DemandMergeStrategy {
    
    /**
     * 生成合并键
     * MTO: 严格不合并 → 每条需求行一个独立键
     * MTS: 按 (物料+材质+产地+计划周期) 合并
     * SSK: 按 (物料+材质) 生成(产地通常不限)
     */
    public String getMergeKey(MrpDemand demand) {
        if ("MTO".equals(demand.getDemandSource())) {
            return "MTO:" + demand.getSourceDemandLineId();
        }
        
        if ("MTS".equals(demand.getDemandSource())) {
            return "MTS:" + demand.getMaterialId() 
                 + "|" + demand.getGradeCode()
                 + "|" + (demand.getOriginCode() != null ? demand.getOriginCode() : "*")
                 + "|" + getPeriodKey(demand.getRequiredDate());
        }
        
        // SSK
        return "SSK:" + demand.getMaterialId() 
             + "|" + demand.getGradeCode();
    }
}
```

---

## 4. MRP 展算核心算法（V3.0 重写）

### 4.1 完整伪代码

```java
public class MrpEngineService {
    
    public MrpRunResult runMrp(MrpRunParam param) {
        MrpRunLog runLog = initializeRun(param);
        MrpContext ctx = buildContext(param);
        
        try {
            // ══════════════════════════════════════════
            // Step 1: 收集需求(每条需求带材质+产地+重量)
            // ══════════════════════════════════════════
            List<MrpDemand> demands = demandCollector.collect(param);
            // 需求结构示例:
            //   方管100×50×4.0 | Q235B | 鞍钢 | 50吨 | MTO | DEM-001
            //   方管100×50×4.0 | Q345B | NULL | 30吨 | MTS | DEM-005
            //   ↑ 同物料不同材质 → 两条独立需求
            
            // ══════════════════════════════════════════
            // Step 2: 双轨LLC计算
            // ══════════════════════════════════════════
            Map<String, Integer> llcMap = dualLlcCalculator.calculateDualLLC(
                ctx.getDiscreteBoms(), ctx.getCategoryBoms());
            List<String> processingOrder = sortByLLC(llcMap);
            
            // ══════════════════════════════════════════
            // Step 3: 按LLC逐层展算
            // 展算维度 = (物料 + 材质 + 产地)
            // ══════════════════════════════════════════
            
            // 3-准备: 将所有需求按(物料+材质+产地)分组
            Map<String, List<MrpDemand>> demandsByKey = groupDemands(demands);
            // key = "materialId|gradeCode|originCode"
            // 例: "1001|Q235B|鞍钢" → [需求1, 需求2]
            //     "1001|Q345B|*"    → [需求3]
            
            for (String llcKey : processingOrder) {
                // llcKey 可能是 "M:1001" (物料级) 或 "C:STRIP" (品类级)
                
                // 获取该LLC层级涉及的所有 (物料+材质+产地) 组合
                List<String> demandKeys = getDemandKeysForLLC(llcKey, demandsByKey);
                
                for (String demandKey : demandKeys) {
                    // demandKey = "1001|Q235B|鞍钢"
                    String[] parts = demandKey.split("\\|");
                    Long materialId = Long.parseLong(parts[0]);
                    String gradeCode = parts[1];
                    String originCode = "*".equals(parts[2]) ? null : parts[2];
                    
                    // 3a: 该(物料+材质+产地)的毛需求时间桶
                    List<MrpBucket> buckets = buildBuckets(
                        materialId, gradeCode, originCode, demandsByKey.get(demandKey));
                    
                    // 3b: 该(物料+材质+产地)的可用库存
                    // 精确匹配: material_id + grade_code + origin_code
                    // 如果 originCode=null(不限产地) → 合并所有产地的库存
                    BigDecimal availableWeight = getAvailableWeight(
                        materialId, gradeCode, originCode, ctx);
                    
                    // 3c: 逐时间桶计算净需求(按重量)
                    BigDecimal runningWeight = availableWeight;
                    BigDecimal safetyWeight = ctx.getSafetyStockWeight(materialId, gradeCode);
                    
                    for (MrpBucket bucket : buckets) {
                        runningWeight = runningWeight
                            .add(bucket.getScheduledWeight())
                            .subtract(bucket.getGrossWeight());
                        
                        BigDecimal netWeight = safetyWeight
                            .subtract(runningWeight).max(BigDecimal.ZERO);
                        
                        if (netWeight.compareTo(BigDecimal.ZERO) > 0) {
                            // 3d: 批量策略(按重量)
                            BigDecimal lotWeight = lotSizer.applyLotSizing(
                                netWeight, ctx.getMaterial(materialId));
                            
                            // 3e: 提前期偏移
                            LocalDate startDate = leadTimeOffset(
                                bucket.getBucketDate(), ctx.getMaterial(materialId));
                            
                            // 3f: 生成计划订单(带材质+产地+重量)
                            PlannedOrder order = new PlannedOrder();
                            order.setMaterialId(materialId);
                            order.setGradeCode(gradeCode);     // ← 材质
                            order.setOriginCode(originCode);   // ← 产地
                            order.setWeight(lotWeight);         // ← 重量
                            order.setQuantity(weightToQty(lotWeight, materialId));
                            order.setStartDate(startDate);
                            order.setEndDate(bucket.getBucketDate());
                            order.setOrderType(determineOrderType(materialId));
                            order.setDemandSource(bucket.getDemandSource());
                            // ... 其他字段
                            
                            ctx.addPlannedOrder(order);
                            runningWeight = runningWeight.add(lotWeight);
                            
                            // 3g: BOM展开 → 子层需求(带材质继承)
                            if ("MFG".equals(order.getOrderType())) {
                                List<MrpDemand> childDemands = bomExploder.explode(
                                    materialId, gradeCode, originCode,  // ← 材质产地传递
                                    lotWeight, startDate, ctx);
                                
                                // 子需求归入对应的 demandKey
                                for (MrpDemand child : childDemands) {
                                    String childKey = child.getMaterialId() + "|" 
                                        + child.getGradeCode() + "|" 
                                        + (child.getOriginCode() != null ? child.getOriginCode() : "*");
                                    demandsByKey.computeIfAbsent(childKey, k -> new ArrayList<>())
                                        .add(child);
                                }
                            }
                        }
                        
                        bucket.setProjectedWeight(runningWeight);
                    }
                }
            }
            
            // Step 4~5: 持久化 + 异常检查
            savePlanOrders(ctx.getPlannedOrders(), runLog.getRunId());
            savePegging(ctx.getPeggingRecords(), runLog.getRunId());
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

### 4.2 BOM 展开时的材质/产地继承

```java
/**
 * BOM 展开器 — 支持离散BOM + 品类BOM, 传递材质/产地
 */
public class BomExploder {

    /**
     * 展开一个计划订单的子层需求
     * @param materialId  父项物料
     * @param gradeCode   父项材质 — 向下继承
     * @param originCode  父项产地 — 向下继承
     * @param weight      父项计划重量(吨)
     */
    public List<MrpDemand> explode(Long materialId, String gradeCode, String originCode,
                                    BigDecimal weight, LocalDate startDate, MrpContext ctx) {
        
        List<MrpDemand> childDemands = new ArrayList<>();
        Material material = ctx.getMaterial(materialId);
        
        // === 优先查离散BOM ===
        BomHead discreteBom = ctx.getDefaultBom(materialId);
        if (discreteBom != null) {
            for (BomDetail detail : discreteBom.getDetails()) {
                MrpDemand child = new MrpDemand();
                child.setMaterialId(detail.getChildMaterialId());
                
                // 材质继承: 子项使用父项的材质
                // (除非子项物料有独立的材质要求)
                Material childMat = ctx.getMaterial(detail.getChildMaterialId());
                child.setGradeCode(gradeCode);  // 继承父项材质
                child.setOriginCode(originCode); // 继承父项产地
                child.setFromCategoryBom(false);
                
                // 重量计算: 父项重量 × 用量系数 × (1+损耗率) + 固定损耗
                BigDecimal childWeight = weight
                    .multiply(detail.getQtyPer())
                    .divide(discreteBom.getBaseQty(), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.ONE.add(detail.getScrapRate()))
                    .add(detail.getFixedScrapQty());
                child.setWeight(childWeight);
                child.setQuantity(weightToQty(childWeight, detail.getChildMaterialId()));
                
                child.setRequiredDate(startDate);
                childDemands.add(child);
            }
            return childDemands;
        }
        
        // === 无离散BOM → 查品类BOM ===
        List<CategoryBom> categoryBoms = ctx.getCategoryBoms(material.getCategoryCode());
        if (categoryBoms != null && !categoryBoms.isEmpty()) {
            for (CategoryBom catBom : categoryBoms) {
                SpecFormula formula = ctx.getFormula(catBom.getCalcFormulaCode());
                
                // 通过公式推算原料规格
                RawMaterialSpec rawSpec = specEngine.calculate(material, weight, formula, catBom);
                
                MrpDemand child = new MrpDemand();
                child.setFromCategoryBom(true);
                child.setCategoryCode(catBom.getChildCategory());
                
                // 材质继承: 原料材质 = 父项材质
                // (钢铁行业: 方管Q235B → 用的带钢也必须是Q235B)
                child.setGradeCode(gradeCode);
                child.setOriginCode(originCode);
                child.setGradeFlexible(false); // 原料材质通常需要一致
                child.setOriginFlexible(true);  // 原料产地通常可灵活
                
                // 原料重量: 由公式计算(含损耗)
                child.setWeight(rawSpec.getWeightPerUnit());
                child.setQuantity(rawSpec.getWeightPerUnit()); // 原料通常以吨为单位
                
                // 记录规格区间(后续原料匹配时使用)
                child.setRawWidthMin(rawSpec.getWidthMin());
                child.setRawWidthMax(rawSpec.getWidthMax());
                child.setRawThicknessMin(rawSpec.getThicknessMin());
                child.setRawThicknessMax(rawSpec.getThicknessMax());
                
                // 尝试匹配具体物料(从物料主数据中找规格在区间内的)
                Material matchedRaw = findMatchingMaterial(
                    catBom.getChildCategory(), rawSpec);
                if (matchedRaw != null) {
                    child.setMaterialId(matchedRaw.getMaterialId());
                } else {
                    // 无精确匹配 → 用品类占位, 后续原料层排产时再匹配
                    child.setMaterialId(null);
                }
                
                child.setRequiredDate(startDate);
                childDemands.add(child);
            }
        }
        
        return childDemands;
    }
}
```

### 4.3 库存匹配规则（按材质+产地）

```
库存匹配策略:

  需求: 方管100×50×4.0 Q235B 鞍钢 50T

  匹配优先级:
  ┌──────────────────────────────────────────────────────────────┐
  │ 1. 精确匹配: material_id + grade_code=Q235B + origin_code=鞍钢│
  │    → 找到 8T → 扣减                                         │
  │                                                              │
  │ 2. 产地不限时: material_id + grade_code=Q235B + 所有产地      │
  │    (当需求 origin_flexible=true 或 origin_code=NULL 时)       │
  │    → 合并所有产地的Q235B库存                                  │
  │                                                              │
  │ 3. 材质可替代时: (第2步仍不足)                                │
  │    → 查询替代料引擎, 找同系列高级材质的库存                    │
  │    → 不在MRP阶段自动替代, 而是标记"可替代"异常提醒             │
  └──────────────────────────────────────────────────────────────┘

  注意: MRP阶段的库存匹配是"净需求计算", 不是"实际分配"。
        实际的材质/产地替代在排产阶段(阶段1原料层排产)处理。
        MRP只负责计算"按精确材质+产地, 还差多少"。
```

---

## 5. 需求合并维度说明

```
V2.0(错误): 需求按 (物料+日期) 合并
  方管100×50×4.0 Q235B 50T  ┐
  方管100×50×4.0 Q345B 30T  ┤ → 错误地合并为 方管100×50×4.0  80T
  方管100×50×4.0 Q235B 20T  ┘   材质信息丢失!

V3.0(正确): 需求按 (物料+材质+产地+日期) 合并
  方管100×50×4.0 Q235B 鞍钢 50T  ┐ → Q235B鞍钢 70T (可合并, 同材质同产地)
  方管100×50×4.0 Q235B 鞍钢 20T  ┘
  方管100×50×4.0 Q345B NULL  30T → Q345B不限 30T (独立, 不同材质)
  
  库存检查也分开:
  Q235B鞍钢: 库存8T → 净需求 70-8=62T
  Q345B不限: 库存5T(唐钢)+3T(日照)=8T → 净需求 30-8=22T
```

---

## 6. MRP 净变更模式 (Net Change)

```
触发净变更的事件:
  ├── 新增/修改/取消 需求单
  ├── 库存调整 (收货/发货/盘点)
  ├── 采购订单状态变更
  ├── 生产报工 (实绩与计划偏差)
  └── BOM / 工艺路线变更

V3.0 新增: 材质/产地维度的净变更
  ├── 某材质库存变动 → 仅重算该材质相关的需求
  ├── 替代料审批通过 → 重算被替代的材质需求
  └── 客户改材质 → 重算新旧两个材质的需求
```

---

## 7. MRP 运行性能优化

| 优化策略 | 具体方案 |
|---------|---------|
| 内存计算 | 运行前将 BOM/库存/需求一次性加载到 HashMap，按(物料+材质+产地)索引 |
| BOM 缓存 | 离散BOM + 品类BOM + LLC 缓存到 Redis，变更时失效 |
| 批量写入 | 计划订单批量 INSERT (每批 500 条)，使用 SqlBulkCopy |
| 快照隔离 | MRP 运行期间使用 WITH(NOLOCK) 读取库存，避免锁等待 |
| 异步执行 | MRP 提交后在独立线程池执行，前端通过进度接口轮询状态 |
| 分段提交 | 每处理完一个 LLC 层级，批量提交一次 |

---

## 8. MRP 工作台（V3.0 含材质/产地/重量）

### 8.1 MRP 运行面板

```
┌─────────────────────────────────────────────────────────────────────┐
│  MRP 工作台                                                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  运行参数:                                                          │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────────────────────┐ │
│  │运行类型   │  │计划展望期(天) │  │范围                          │ │
│  │[完整重算▼]│  │[   90      ]│  │[全部物料                  ▼]│ │
│  └──────────┘  └──────────────┘  └──────────────────────────────┘ │
│                                                                     │
│  [▶ 开始运行]  [⏸ 取消]                                             │
│                                                                     │
│  进度: ████████████░░░░░  65%  LLC=2, 已处理3500条, 含品类BOM展开     │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│  最近运行记录                                                        │
│  ┌────────┬──────┬──────┬──────┬──────┬──────┬──────┬───────────┐ │
│  │批次号  │类型  │状态  │需求数│计划数│采购数│品类BOM│运行时间   │ │
│  ├────────┼──────┼──────┼──────┼──────┼──────┼──────┼───────────┤ │
│  │MRP26001│完整  │完成  │2350 │1580 │890  │420  │22s        │ │
│  │MRP26002│净变更│完成  │120  │85   │42   │35   │3s         │ │
│  └────────┴──────┴──────┴──────┴──────┴──────┴──────┴───────────┘ │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│  计划订单审核  [计划订单(1580)] [采购建议(890)] [异常(23)] [追溯]     │
│                                                                     │
│  筛选: 品类[全部▼] 材质[全部▼] 产地[全部▼] 类型[全部▼] 状态[全部▼]  │
│                                                                     │
│  ┌────────┬──────────────┬──────┬──────┬─────┬──────┬──────┬──────┐│
│  │订单号  │物料/规格     │材质  │产地  │重量 │类型  │完成日│状态  ││
│  ├────────┼──────────────┼──────┼──────┼─────┼──────┼──────┼──────┤│
│  │PO-001  │方管100×50×4.0│Q235B│鞍钢  │42.0T│制造  │03-25│待确认││
│  │PO-002  │方管100×50×4.0│Q345B│不限  │22.0T│制造  │03-28│待确认││
│  │PO-003  │带钢290×4.0  │Q235B│不限  │43.7T│采购  │03-22│待确认││
│  │PO-004  │带钢290×4.0  │Q345B│不限  │22.9T│采购  │03-25│待确认││
│  │PO-005  │车架A型纵梁   │Q345B│首钢  │ 2.5T│制造  │03-26│待确认││
│  │PO-006  │板材16×300    │Q345B│首钢  │ 3.0T│采购  │03-23│待确认││
│  └────────┴──────────────┴──────┴──────┴─────┴──────┴──────┴──────┘│
│                                                                     │
│  注意: 同一物料方管100×50×4.0 因材质不同(Q235B/Q345B)               │
│        分为两条独立的计划订单, 不会合并!                              │
│                                                                     │
│  [✓ 批量确认] [✗ 批量取消] [🔒 锁定] [→ 转排产] [📋 导出]           │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│  异常与建议                                                          │
│  ⚠ PO-003: 带钢290×4.0 Q235B 库存不足, 缺口28.7T, 建议采购         │
│  ⚠ PO-004: 带钢290×4.0 Q345B 无库存, 全量43.7T需采购               │
│  ℹ PO-001: Q235B 鞍钢库存不足, 首钢有15T可替代(客户偏好允许)         │
│  ⚠ PO-005: 纵梁Q345B首钢, 物料无精确离散BOM, 使用品类BOM展开        │
└─────────────────────────────────────────────────────────────────────┘
```

### 8.2 需求-供给追溯 (Pegging) — V3.0 含材质/产地

```
需求追溯视图:

  需求单 DEM-001 (合同, 客户:XX建材)
    └─ 行#1: 方管100×50×4.0 | Q235B | 鞍钢 | 50T, 交期04-05
         │
         ├── 供给1: 库存 8T (Q235B 鞍钢, 仓库A, 卷号P-088)
         ├── 供给2: 在制 12T (SCH-042, Q235B 鞍钢)
         └── 供给3: 计划订单 PO-001 — 42T Q235B 鞍钢 (待确认)
                │
                └── 品类BOM展开 (RECT_PIPE → STRIP):
                    └── 带钢 宽286~292mm 厚3.85~4.15mm Q235B — 43.7T
                        ├── 供给: 库存 15T (Q235B 鞍钢, C-001)
                        ├── 供给: 库存 0T  (Q235B 首钢) ← 可替代
                        └── 供给: 采购建议 PO-003 — 28.7T Q235B 不限产地

  需求单 DEM-005 (期货)
    └─ 行#1: 方管100×50×4.0 | Q345B | 不限 | 30T, 交期04-10
         │                    ↑ 不同材质 → 完全独立的需求/供给链
         ├── 供给1: 库存 8T (Q345B 唐钢5T + 日照3T)
         └── 供给2: 计划订单 PO-002 — 22T Q345B 不限 (待确认)
                │
                └── 品类BOM展开:
                    └── 带钢 290×4.0 Q345B — 22.9T
                        └── 供给: 采购建议 PO-004 — 22.9T Q345B
```
