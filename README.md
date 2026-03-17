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
