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
