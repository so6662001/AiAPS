# AiAPS — 钢铁行业智能排产与 MRP 系统

> AI-powered Advanced Planning & Scheduling for Steel Manufacturing

## 项目简介

AiAPS 是面向钢铁生产制造与加工行业的 MRP（物料需求计划）与排产自动化系统。覆盖管材生产、型材生产、货车车架/车梁、开平、剪切、折弯、冷轧、镀锌等业务场景，支持按订货合同 (MTO)、期货订单 (MTS)、库存备货 (Safety Stock) 三种模式驱动生产，同时支持原料驱动型排产（行情买料后反推生产）。

## 技术栈

| 层次 | 技术选型 |
|-----|---------|
| 后端 | Java / Spring Boot 2.7.18 / MyBatis-Plus 3.5.5 |
| 前端 | Vue 3 + TypeScript / Vite / Element Plus / ECharts / DHTMLX Gantt |
| 移动端 | Android 原生 (Java / Retrofit2 / ZXing) — 车间终端 |
| 数据库 | SQL Server 2008 |
| 缓存 | Redis 6.x |
| 并发目标 | ≥ 500 人同时在线 |

## 代码结构

### Java 后端 (aiaps-parent)

```
aiaps-parent/                     Maven多模块, Spring Boot 2.7
├── aiaps-common/   (5文件)       统一响应R / 分页 / 异常处理 / 常量
├── aiaps-domain/   (30文件)      实体类(对齐V3.0数据模型)
│   └── 字段名: PrdtID / PATName / PAName / ResNo / CardNo / BindNo / CardRemark 等
├── aiaps-mapper/   (22接口+24XML) MyBatis-Plus Mapper (SQL Server 2008 兼容)
├── aiaps-service/  (14文件)      业务服务
│   ├── base/       MaterialService, BomService, WorkCenterService
│   ├── demand/     DemandService (合同号+进度)
│   ├── inventory/  StockService (出入库+流水), MaterialIssueService (领料)
│   ├── mrp/        MrpEngineService, DualBomLlcCalculator, SpecCalculationEngine
│   ├── aps/        ScheduleEngineService, NestingService
│   ├── production/ ReportService (报工+成材率)
│   └── trace/      TraceabilityService (正向/反向/条码/合同追溯)
└── aiaps-web/      (12文件)      Spring Boot 启动 + 11个 REST Controller
```

### Vue3 前端 (aiaps-ui)

```
aiaps-ui/                          Vite + Vue3 + TypeScript
├── src/
│   ├── api/        6个API模块 (base/mrp/schedule/inventory/trace/report)
│   ├── assets/     SCSS 科技风主题 (暗色侧边栏 + 蓝色强调)
│   ├── layouts/    MainLayout (侧边栏+头部+内容区)
│   ├── router/     16条路由 (懒加载)
│   ├── stores/     Pinia 状态管理
│   ├── utils/      Axios 封装 (拦截器+401处理)
│   └── views/      15个页面
│       ├── dashboard/   生产指挥中心大屏
│       ├── base/        物料管理 / BOM管理 / 工作中心
│       ├── demand/      需求管理 (MTO/MTS/SSK + 合同号)
│       ├── mrp/         MRP工作台 / 计划订单 (材质+产地+重量+合同)
│       ├── schedule/    排产甘特图 / 排产列表 / 套裁方案
│       ├── inventory/   库存查询 (CardNo/ResNo/捆包) / 领料管理
│       ├── production/  报工记录 (成材率)
│       ├── trace/       追溯中心 (5种查询 + 时间线链路)
│       └── report/      合同进度 (预计完工 + 链路可视化)
```

### Android 车间终端 (aiaps-android)

```
aiaps-android/                     Android 原生 (Java)
├── 上料扫码     ZXing扫码 → 合同/材质/规格三重校验 → 确认上料
├── 生产报工     选工单 → 输入数量/重量 → 自动算成材率 → 提交
└── 入库确认     扫码 → 质检 → 过磅 → 捆包/备注 → 确认入库+打印标签
```

## 核心功能

- **MRP 展算引擎**：双轨 LLC（品类BOM + 离散BOM），以 (物料+材质+产地) 为展算维度，重量为核心计量
- **有限产能排产**：成本/效率/交期/均衡四种策略可配，模具分组优化（快速换模），多阶段排产
- **规格推算引擎**：品类BOM公式计算原料规格（方管→带钢宽度=周长展开），exp4j 求值
- **模具管理**：寿命追踪、自动拆单、换模时间矩阵（完全换模 vs 快速换模）
- **一分X套裁**：钢卷分剪优化（一维装箱算法），开平快速排产
- **排产模拟推演**：What-If 沙盒模式，甘特图叠加对比，确认后才应用
- **上料智能调单**：换料成本 vs 调单成本自动测算，推荐最优方案
- **排产向导**：16 步排产流程重构为 3 步（选订单→配方案→排产确认），系统自动配料/排产、实时反算、影响预览，点击数减少约 70%
- **合同号全链路**：贯穿需求→计划→排产→领料→报工→库存，窜料控制
- **三级追溯**：卡号(批次)→捆包号(每件)→条码(每支管)，正向/反向/扫码追溯
- **材质产地替代**：等级体系 + 客户差异化偏好 + 7级优先级匹配
- **冷板/镀锌工艺**：罩退炉台+层位管理、酸轧联合(PLTCM)、镀锌线花型
- **26个报表/看板**：进度6+效能8+物料3+看板6+综合3，含预计完工时间

## 字段命名规范

系统采用与客户实际系统一致的字段命名：

| 设计概念 | 实际字段名 | 说明 |
|---------|-----------|------|
| 物料ID | PrdtID | 物料主键 |
| 物料编号 | PrdtNo | 物料编码 |
| 物料名称 | PrdtName | 物料名称 |
| 材质 | PATName / PrdtAttsID | 材质名称 / 材质ID |
| 产地 | PAName / PAID | 产地名称 / 产地ID |
| 资源号(卷号) | ResNo | 钢卷/带钢的唯一标识 |
| 批次号(卡号) | CardNo | 一批次的唯一标识 |
| 捆包号 | BindNo | 每件货物的唯一标识 |
| 单支条码 | ItemBarcode | 每支管/板的唯一标识 |
| 备注1 | CardRemark | 批次备注 |
| 备注2 | CardRemark2 | 批次备注 |
| 入库日期 | EnterDate | 入库日期 |
| 长度 | stock_length / product_length | 批次属性(非物料维度) |
| 合同号 | contract_no | 贯穿全链路 |

## 设计文档 (23份)

详细设计文档位于 `docs/design/` 目录：

| # | 文档 | 版本 | 内容 |
|---|------|------|------|
| 01 | [system-overview](docs/design/01-system-overview.md) | V1 | 系统架构、业务背景、三种生产驱动、分层架构、500并发策略 |
| 02 | [data-model](docs/design/02-data-model.md) | **V3.0** | 全部表结构(含材质/产地/重量/合同/长度)，领料/流水/入库3张执行表 |
| 03 | [mrp-engine](docs/design/03-mrp-engine.md) | **V3.0** | 双轨LLC + (物料+材质+产地)维度展算 + 品类BOM公式推算 |
| 04 | [scheduling-engine](docs/design/04-scheduling-engine.md) | V2 | 有限产能排产算法、正向/倒排/混合策略、冲突检测与解决 |
| 05 | [frontend-design](docs/design/05-frontend-design.md) | V2 | Vue3前端设计、甘特图交互、MRP工作台、权限矩阵 |
| 06 | [api-and-integration](docs/design/06-api-and-integration.md) | V2 | RESTful API清单、WebSocket推送、外部系统集成 |
| 07 | [implementation-roadmap](docs/design/07-implementation-roadmap.md) | V2 | 6阶段实施计划、风险应对、测试策略、部署架构 |
| 08 | [steel-industry-adaptation](docs/design/08-steel-industry-adaptation.md) | V2.0 | 物料模型重构、品类BOM+公式、多阶段排产、原料驱动 |
| 09 | [mold-substitute-flow](docs/design/09-mold-substitute-flow.md) | V2.1 | 模具管理/替代料/分组排产/产出流向 |
| 10 | [reports-and-dashboards](docs/design/10-reports-and-dashboards.md) | V2 | 26个报表/看板(含预计完工时间) |
| 11 | [plan-schedule-modification](docs/design/11-plan-schedule-modification.md) | V1 | 计划/排产修改完整工作流、影响预览、版本管理、审批 |
| 12 | [replenish-swap-reprocess](docs/design/12-replenish-swap-reprocess.md) | V1 | 补料/换料/二次加工流程 |
| 13 | [grade-origin-substitute](docs/design/13-grade-origin-substitute.md) | V1 | 材质等级体系、产地分级、客户偏好、7级匹配 |
| 14 | [slitting-data-walkthrough](docs/design/14-slitting-data-walkthrough.md) | V1 | 分剪业务全链路数据走查(7层表数据) |
| 15 | [cost-simulate-smartswap](docs/design/15-cost-simulate-smartswap.md) | V1 | 排产策略引擎、What-If模拟推演、上料智能调单 |
| 16 | [execution-fullchain-walkthrough](docs/design/16-execution-fullchain-walkthrough.md) | V1 | 开平→制管两道工序完整数据走查(含领料/报工/入库) |
| 17 | [length-contract-mixcontrol](docs/design/17-length-contract-mixcontrol.md) | V1 | 长度批次化、合同号贯穿、窜料控制、页面/报表示例图 |
| 18 | [traceability-barcode](docs/design/18-traceability-barcode.md) | V1 | 三级标识(卡号→捆包→条码)、追溯关联表、追溯查询 |
| 19 | [coldroll-galvanize-process](docs/design/19-coldroll-galvanize-process.md) | V1 | 冷板/镀锌板工艺(罩退炉台层位+酸轧联合PLTCM+镀锌花型) |
| 20 | [multi-demand-nesting](docs/design/20-multi-demand-nesting.md) | V1 | 多客户需求合并套料(开平/分剪/剪切跨合同合并+二维排版) |
| 21 | [page-tracking-analytics](docs/design/21-page-tracking-analytics.md) | V1 | 页面埋点与数据分析(用户行为采集+操作路径+数据看板) |
| 22 | [scheduling-wizard](docs/design/22-scheduling-wizard.md) | V1 | 排产向导：16步→3步流程优化(系统自动配料/排产+实时反算+影响预览) |
| 23 | [agreement-privacy-compliance](docs/design/23-agreement-privacy-compliance.md) | V1 | 用户协议/隐私协议/数据使用授权/版本管理/OSS存档/合规保障 |

## 设计演进历程

### V1.0 — 基础框架 (01~07)
系统架构、数据模型、MRP引擎、排产引擎、前端设计、API设计、实施路线

### V2.0 — 钢铁行业适配 (08)
物料=品类+规格(材质/产地剥离)、品类BOM+公式推算、多阶段排产、一分X分剪、原料驱动排产

### V2.1 — 生产实务增强 (09)
模具寿命/自动拆单/换模矩阵、替代料规则、同壁厚分组排产、产出流向标注

### V3.0 — 数据模型修正 (02/03)
核心表(需求/库存/MRP/排产)全面补齐材质(PATName)、产地(PAName)、重量(weight)；
MRP引擎以(物料+材质+产地)为展算维度；新增领料/流水/入库3张执行表

### 报表与看板 (10)
进度6+效能8+物料3+看板6+综合3=26个，含预计完工时间

### 计划/排产修改 (11)
影响分析预览、10种变更场景、批量修改、版本快照/对比/回滚、撤销恢复、审批流程

### 补料/换料/二次加工 (12)
补料5场景+自动匹配来源、换料6场景+批次追溯、二次加工8场景+多阶段链路追踪

### 材质/产地替代 (13)
材质等级体系(Q235A<B<C<D)、产地质量分级(4级)、客户差异化偏好、7级优先级匹配、产出继承规则

### 排产策略/模拟/智能调单 (15)
成本vs效率策略引擎(可配权重)、What-If沙盒模拟、上料不符智能换料/调单决策(成本对比)

### 长度/合同/窜料 (17)
长度从物料剥离到批次属性、合同号贯穿10张核心表、窜料控制(策略级+排产级)

### 物料追溯/条码 (18)
三级标识(CardNo→BindNo→ItemBarcode)、品类可控单支条码、trc_trace_link追溯关联表

### 冷板/镀锌工艺 (19)
罩退(炉台bas_furnace+层位+退火制度+兼容组)、酸轧联合PLTCM(一条线不出中间卷)、镀锌线(花型/锌量)

### 数据走查验证 (14/16)
分剪2.5×1500→300+600全7层数据、开平→制管两道工序含领料/报工/入库完整流转
