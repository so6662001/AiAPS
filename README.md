# AiAPS — 钢铁行业智能排产与 MRP 系统

> AI-powered Advanced Planning & Scheduling for Steel Manufacturing

## 项目简介

AiAPS 是面向钢铁生产制造与加工行业的 MRP（物料需求计划）与排产自动化系统。覆盖管材生产、型材生产、货车车架/车梁、开平、剪切、折弯等业务场景，支持按订货合同 (MTO)、期货订单 (MTS)、库存备货 (Safety Stock) 三种模式驱动生产。

## 技术栈

| 层次 | 技术选型 |
|-----|---------|
| 后端 | Java 8+ / Spring Boot 2.7.x / MyBatis-Plus |
| 前端 | Vue 3 / Element Plus / ECharts / DHTMLX Gantt |
| 数据库 | SQL Server 2008 |
| 缓存 | Redis 6.x |
| 并发目标 | ≥ 500 人同时在线 |

## 核心功能

- **MRP 展算引擎**：自动计算物料净需求，生成制造/采购/委外计划订单
- **有限产能排产**：基于优先级的列表调度算法，自动分配工作中心与时间槽
- **可视化甘特图**：支持拖拽调整、插单、拆分、合并等交互操作
- **三种需求驱动**：MTO 合同订单 / MTS 期货订单 / 安全库存补货
- **换产时间优化**：基于规格矩阵的换产排序优化
- **冲突检测与解决**：自动检测资源冲突、交期违反、产能超载

## 设计文档

详细设计文档位于 `docs/design/` 目录：

| 文档 | 内容 |
|------|------|
| [01-system-overview.md](docs/design/01-system-overview.md) | 系统总体设计、业务背景、架构概览 |
| [02-data-model.md](docs/design/02-data-model.md) | 数据库模型设计（SQL Server 2008 兼容） |
| [03-mrp-engine.md](docs/design/03-mrp-engine.md) | MRP 展算引擎核心算法 |
| [04-scheduling-engine.md](docs/design/04-scheduling-engine.md) | 排产调度引擎设计 |
| [05-frontend-design.md](docs/design/05-frontend-design.md) | 前端交互设计（Vue 3） |
| [06-api-and-integration.md](docs/design/06-api-and-integration.md) | API 接口与系统集成 |
| [07-implementation-roadmap.md](docs/design/07-implementation-roadmap.md) | 实施路线图与开发计划 |
| **[08-steel-industry-adaptation.md](docs/design/08-steel-industry-adaptation.md)** | **钢铁行业深度适配（核心优化）** |
| **[09-mold-substitute-flow.md](docs/design/09-mold-substitute-flow.md)** | **模具管理、替代料、分组排产与产出流向（V2.1 增强）** |
| **[10-reports-and-dashboards.md](docs/design/10-reports-and-dashboards.md)** | **报表体系与管理看板（26个报表/看板）** |
| **[11-plan-schedule-modification.md](docs/design/11-plan-schedule-modification.md)** | **计划修改与排产修改完整设计** |
| **[12-replenish-swap-reprocess.md](docs/design/12-replenish-swap-reprocess.md)** | **补料、换料与二次加工流程设计** |
| **[13-grade-origin-substitute.md](docs/design/13-grade-origin-substitute.md)** | **材质与产地替代料深度设计** |
| **[14-slitting-data-walkthrough.md](docs/design/14-slitting-data-walkthrough.md)** | **分剪业务全链路数据走查实例** |
| **[15-cost-simulate-smartswap.md](docs/design/15-cost-simulate-smartswap.md)** | **排产策略引擎、模拟推演与上料智能调单** |
| **[16-execution-fullchain-walkthrough.md](docs/design/16-execution-fullchain-walkthrough.md)** | **生产执行全链路数据走查与缺失表补充** |
| **[17-length-contract-mixcontrol.md](docs/design/17-length-contract-mixcontrol.md)** | **长度批次化、合同号贯穿与窜料控制** |
| **[18-traceability-barcode.md](docs/design/18-traceability-barcode.md)** | **物料追溯体系与条码管理** |
| **[19-coldroll-galvanize-process.md](docs/design/19-coldroll-galvanize-process.md)** | **冷板与镀锌板生产工艺排产（炉台层位管理）** |

### V2.0 核心优化 (08 文档)

08 文档是对整体设计的重大优化，解决钢铁行业与传统离散 MRP 的根本性差异：

- **物料模型重构**：物料 = 品类 + 规格，材质/产地剥离为库存批次属性
- **双轨 BOM 体系**：品类 BOM（公式推算规格）+ 离散 BOM（精确展开）并存
- **规格推算引擎**：方管/圆管/型材/折弯件等各品类的原料规格计算公式
- **多阶段排产**：原料层排产（备料）→ 制造层排产（产线），管材先排带钢再排制管
- **一分X 分剪排产**：钢卷纵切套裁优化，一个母卷分切多条带钢
- **原料驱动排产**：行情好买入原料后，反向匹配可生产成品及需求

### V2.1 增强 (09 文档)

进一步解决排产引擎中的四个生产实务问题：

- **模具管理与产能约束**：模具寿命追踪、最大生产量自动拆单、换模时间矩阵、不同机组不同换模时间
- **替代料规则引擎**：材质替代(向上)、壁厚替代(厚替薄)、宽度替代，自动/审批模式
- **同规格不同壁厚分组排产**：模具兼容组内连排(快速换模)，组间完全换模，最小化换模总时间
- **产出物料流向标注**：成品入库 / 流向下一工序(显示工序名) / 直发客户，自动推断+可编辑

### 报表与看板 (10 文档)

设计了覆盖决策层/管理层/执行层的 **26个报表和看板**：

- **进度类 (6个)**：订单进度总览、排产执行跟踪、工序完工进度、交期达成率、延期预警、客户交付看板
- **效能类 (8个)**：产能利用率、OEE综合效率、换模效率分析、成材率/损耗、产量日报、模具寿命、替代料分析、计划vs实际偏差
- **物料类 (3个)**：物料齐套分析、库存周转、原料消耗追踪
- **看板类 (6个)**：生产指挥中心大屏、车间实时看板、异常预警、MRP监控、模具状态、供需平衡
- **综合类 (3个)**：经营日报(自动生成)、周报/月报、产线对比(雷达图)

### 计划修改与排产修改 (11 文档)

完整覆盖计划和排产的修改工作流：

- **修改前影响分析预览**：修改任何计划/排产前，系统先展示对交期、原料、产能、模具的全部影响，确认后才执行
- **计划修改**：改量/改期/改类型(自制↔采购↔委外)/拆分/合并/锁定，需求变更自动联动计划
- **排产修改**：10种变更场景处理方案（客户改单/紧急插单/原料延迟/设备故障/质量返工/进度偏差等）
- **批量修改**：选中多个任务统一推迟/提前/改产线/改优先级/确认下达/取消
- **版本管理**：自动快照 + 版本对比(甘特图叠加对比) + 回滚到任意历史版本
- **撤销/恢复**：Ctrl+Z/Y 支持最近30步操作回退
- **审批流程**：按修改影响程度自动判断是否需要审批（微调免审/影响交期需审批）

### 补料、换料与二次加工 (12 文档)

覆盖生产执行过程中三个高频实务流程：

- **补料**：原料用完/损耗超标/质量报废/原料缺陷/追加数量 → 系统自动推荐补料来源(库存/替代料/分剪产出/紧急采购) → 审批 → 排产联动
- **换料**：钢卷用完接续/质量问题/材质切换/规格切换/余料利用 → 记录换料时间点和新旧批次 → 产出按段分批次追溯
- **二次加工**：后处理(镀锌/涂层)/切割定尺/打包/返工修复/库存再加工/退货返修 → 自动/手动创建排产 → 多阶段加工链路追踪

### 材质与产地替代料深度设计 (13 文档)

对 09 文档替代料设计的深度增强，专解钢铁行业材质（钢号）和产地（钢厂）替代：

- **材质等级体系**：Q235A<B<C<D 向上替代层次，同系列自动判断替代方向，跨系列(Q235↔SS400)需技术确认
- **产地质量分级**：顶级/优良/良好/一般四级，产地互换组（同组免审批互换），质量评分
- **客户级差异化偏好**：每个客户独立的材质策略(严格/同系列向上/任意) + 产地策略(严格/白名单/同等级/任意)
- **7级优先级匹配**：精确匹配 → 同材质换同级产地 → 同材质换高级产地 → 材质升级同产地 → 组合替代 → 降级产地 → 跨系列
- **产出物料继承规则**：材质向上替代→产出标原始材质；产地替代→标实际产地(质保书真实性)；换料分段→分批次标记
