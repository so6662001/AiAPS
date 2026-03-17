# AiAPS — API 接口与集成设计

> 版本：1.0 | 最后更新：2026-03-17

---

## 1. API 总览

### 1.1 RESTful API 规范

```
基础约定：
  · 基础路径: /api/v1
  · 认证方式: JWT Bearer Token (Header: Authorization)
  · 请求格式: JSON (Content-Type: application/json)
  · 响应格式: 统一封装
  · 分页参数: pageNum, pageSize (SQL Server 2008 用 ROW_NUMBER 分页)
  · 时间格式: yyyy-MM-dd HH:mm:ss
```

### 1.2 统一响应格式

```json
{
    "code": 200,
    "message": "操作成功",
    "data": { ... },
    "timestamp": "2026-03-20 14:35:22"
}

// 分页响应
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "records": [ ... ],
        "total": 1580,
        "pageNum": 1,
        "pageSize": 20
    },
    "timestamp": "2026-03-20 14:35:22"
}
```

## 2. 各模块 API 接口清单

### 2.1 基础数据模块

```
物料管理:
  GET    /api/v1/material                    # 物料列表(分页+筛选)
  GET    /api/v1/material/{id}               # 物料详情
  POST   /api/v1/material                    # 新增物料
  PUT    /api/v1/material/{id}               # 修改物料
  DELETE /api/v1/material/{id}               # 删除物料
  GET    /api/v1/material/{id}/substitutes   # 替代物料列表

BOM 管理:
  GET    /api/v1/bom                         # BOM 列表
  GET    /api/v1/bom/{id}                    # BOM 详情(含明细行)
  POST   /api/v1/bom                         # 新增 BOM
  PUT    /api/v1/bom/{id}                    # 修改 BOM
  GET    /api/v1/bom/{id}/tree               # BOM 树形展开
  GET    /api/v1/bom/{id}/where-used         # 反查(物料被哪些BOM引用)

工艺路线:
  GET    /api/v1/routing                     # 工艺路线列表
  GET    /api/v1/routing/{id}                # 工艺路线详情(含工序)
  POST   /api/v1/routing                     # 新增工艺路线
  PUT    /api/v1/routing/{id}                # 修改工艺路线

工作中心:
  GET    /api/v1/work-center                 # 工作中心列表
  GET    /api/v1/work-center/{id}            # 工作中心详情
  POST   /api/v1/work-center                 # 新增
  PUT    /api/v1/work-center/{id}            # 修改
  GET    /api/v1/work-center/{id}/capacity   # 产能日历查询
  PUT    /api/v1/work-center/{id}/capacity   # 产能日历维护

工厂日历:
  GET    /api/v1/calendar                    # 日历查询
  POST   /api/v1/calendar/generate           # 批量生成日历(按年/月)
  PUT    /api/v1/calendar/batch              # 批量修改日历
```

### 2.2 需求管理模块

```
需求管理:
  GET    /api/v1/demand                      # 需求单列表
  GET    /api/v1/demand/{id}                 # 需求单详情
  POST   /api/v1/demand                      # 新增需求单
  PUT    /api/v1/demand/{id}                 # 修改需求单
  PUT    /api/v1/demand/{id}/confirm         # 确认需求单
  PUT    /api/v1/demand/{id}/cancel          # 取消需求单
  POST   /api/v1/demand/import               # 从销售合同批量导入
  GET    /api/v1/demand/summary              # 需求汇总看板数据
```

### 2.3 库存管理模块

```
库存查询:
  GET    /api/v1/stock                       # 库存列表(支持多维度筛选)
  GET    /api/v1/stock/available             # 可用量查询
  GET    /api/v1/stock/material/{id}         # 某物料全仓库库存
  GET    /api/v1/stock/alert                 # 库存预警列表
  PUT    /api/v1/stock/safety/{id}           # 修改安全库存配置
```

### 2.4 MRP 模块

```
MRP 运行:
  POST   /api/v1/mrp/run                     # 启动 MRP 运算
  GET    /api/v1/mrp/run/{runId}/progress     # 查询运行进度
  PUT    /api/v1/mrp/run/{runId}/cancel       # 取消运行中的 MRP
  GET    /api/v1/mrp/run/history              # 运行历史

MRP 结果:
  GET    /api/v1/mrp/plan-order               # 计划订单列表(分页)
  GET    /api/v1/mrp/plan-order/{id}          # 计划订单详情
  PUT    /api/v1/mrp/plan-order/confirm       # 批量确认计划订单
  PUT    /api/v1/mrp/plan-order/cancel        # 批量取消计划订单
  PUT    /api/v1/mrp/plan-order/{id}/firm     # 锁定计划订单(不被重算覆盖)
  GET    /api/v1/mrp/purchase-suggest         # 采购建议列表
  PUT    /api/v1/mrp/purchase-suggest/confirm # 批量确认采购建议
  GET    /api/v1/mrp/pegging/{demandLineId}   # 需求-供给追溯
  GET    /api/v1/mrp/exception/{runId}        # MRP 异常信息列表

MRP 分析:
  GET    /api/v1/mrp/balance/{materialId}     # 物料供需平衡图数据
```

### 2.5 排产调度模块

```
自动排产:
  POST   /api/v1/schedule/auto                # 自动排产
  POST   /api/v1/schedule/reschedule          # 增量重排
  GET    /api/v1/schedule/auto/{taskId}/progress  # 排产进度

排产管理:
  GET    /api/v1/schedule                     # 排产任务列表
  GET    /api/v1/schedule/{id}                # 排产详情(含工序)
  PUT    /api/v1/schedule/{id}                # 修改排产任务

排产调整:
  PUT    /api/v1/schedule/move                # 拖拽移动
  POST   /api/v1/schedule/insert              # 插单
  POST   /api/v1/schedule/split               # 拆分任务
  POST   /api/v1/schedule/merge               # 合并任务
  PUT    /api/v1/schedule/lock                # 批量锁定
  PUT    /api/v1/schedule/unlock              # 批量解锁
  PUT    /api/v1/schedule/{id}/priority       # 调整优先级
  PUT    /api/v1/schedule/{id}/cancel         # 取消排产

甘特图:
  GET    /api/v1/schedule/gantt               # 甘特图数据
         ?view=WC|ORDER|MATERIAL              # 视图类型
         &wcIds=101,102                       # 工作中心筛选
         &dateFrom=2026-03-20                 # 开始日期
         &dateTo=2026-03-27                   # 结束日期
         &status=CONFIRMED,RELEASED           # 状态筛选
         &demandSource=MTO,MTS                # 需求来源筛选

产能分析:
  GET    /api/v1/schedule/wc-load             # 工作中心负荷数据
  GET    /api/v1/schedule/wc-load/heatmap     # 负荷热力图数据
  GET    /api/v1/schedule/conflicts           # 当前冲突列表

排产变更记录:
  GET    /api/v1/schedule/{id}/changelog      # 排产变更历史
```

### 2.6 报工模块

```
报工:
  POST   /api/v1/report                       # 提交报工
  GET    /api/v1/report                       # 报工记录查询
  GET    /api/v1/report/schedule/{scheduleId} # 某排产单的报工记录
  GET    /api/v1/report/progress              # 整体生产进度概览
```

### 2.7 看板与报表

```
Dashboard:
  GET    /api/v1/dashboard/overview           # 首页看板数据
  GET    /api/v1/dashboard/alerts             # 紧急事项列表
  GET    /api/v1/dashboard/wc-status          # 产线实时状态

报表:
  GET    /api/v1/report/schedule-summary      # 排产汇总报表
  GET    /api/v1/report/delivery-rate         # 交付准时率
  GET    /api/v1/report/capacity-utilization  # 产能利用率报表
  GET    /api/v1/report/setup-analysis        # 换产时间分析
```

## 3. WebSocket 推送协议

```
连接: ws://host:port/ws/aps?token={jwt_token}

消息格式:
{
    "event": "schedule.changed",
    "data": {
        "scheduleId": 5001,
        "changeType": "MOVE",
        "changedBy": "张三"
    },
    "timestamp": "2026-03-20T14:35:22"
}

事件类型:
  mrp.progress        MRP 运行进度 (百分比+当前阶段)
  mrp.completed       MRP 运行完成
  mrp.error           MRP 运行异常
  schedule.changed    排产变更通知
  schedule.conflict   排产冲突通知
  report.submitted    报工数据提交
  stock.alert         库存预警
  system.notification 系统通知
```

## 4. 与外部系统集成

### 4.1 集成接口

```
┌──────────────┐                    ┌──────────────┐
│  销售系统     │ ─── 合同/订单 ───→ │              │
│  (ERP)       │                    │              │
└──────────────┘                    │              │
                                    │              │
┌──────────────┐                    │              │
│  采购系统     │ ←── 采购建议 ───── │   AiAPS      │
│  (ERP)       │ ─── 采购进度 ───→ │              │
└──────────────┘                    │              │
                                    │              │
┌──────────────┐                    │              │
│  仓储系统     │ ─── 库存数据 ───→ │              │
│  (WMS)       │                    │              │
└──────────────┘                    │              │
                                    │              │
┌──────────────┐                    │              │
│  MES 系统     │ ←── 生产工单 ───── │              │
│  (车间执行)   │ ─── 报工数据 ───→ │              │
└──────────────┘                    └──────────────┘

集成方式:
  · 数据库直连 (同库或 Linked Server)
  · REST API 调用
  · 中间表 (定时同步)
  · 消息队列 (实时推送)
```

### 4.2 数据同步策略

| 数据 | 方向 | 频率 | 方式 |
|------|------|------|------|
| 销售合同/订单 | ERP → AiAPS | 实时/准实时 | API 推送或定时拉取 |
| 物料主数据 | ERP → AiAPS | 准实时 | 中间表 + 变更日志 |
| 库存数据 | WMS → AiAPS | 每 15 分钟 | 定时同步快照 |
| 采购在途 | ERP → AiAPS | 准实时 | 采购单状态推送 |
| 采购建议 | AiAPS → ERP | 审核后推送 | API 推送 |
| 生产工单 | AiAPS → MES | 下达后推送 | API 推送 |
| 报工数据 | MES → AiAPS | 实时 | API 推送 |

## 5. Java 后端技术架构

### 5.1 项目模块结构

```
aiaps/
├── aiaps-common/              # 公共模块
│   ├── common-core/           # 工具类、常量、异常定义
│   ├── common-redis/          # Redis 缓存封装
│   ├── common-security/       # JWT 认证与权限
│   └── common-web/            # 统一响应、全局异常处理
│
├── aiaps-api/                 # API 接口定义 (Feign 接口)
│
├── aiaps-modules/             # 业务模块
│   ├── module-base/           # 基础数据服务
│   ├── module-demand/         # 需求管理服务
│   ├── module-inventory/      # 库存管理服务
│   ├── module-mrp/            # MRP 引擎服务
│   ├── module-aps/            # 排产调度服务
│   ├── module-production/     # 生产执行与报工
│   └── module-system/         # 系统管理
│
├── aiaps-gateway/             # API 网关 (可选, 单体时用 Nginx)
│
└── aiaps-admin/               # 后台管理入口 (Spring Boot Application)
```

### 5.2 技术组件版本建议

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 8 或 11 | SQL Server 2008 JDBC 兼容 |
| Spring Boot | 2.7.x | LTS, 兼容 JDK 8 |
| MyBatis-Plus | 3.5.x | ORM 框架，配合动态 SQL |
| SQL Server JDBC | mssql-jdbc 9.4+ | 向下兼容 SQL Server 2008 |
| Redis | 6.x+ | 缓存 |
| HikariCP | 5.x | 连接池 |
| Quartz | 2.3.x | 定时任务 |
| Hutool | 5.8.x | 工具库 |
| MapStruct | 1.5.x | DTO 转换 |
| Swagger/Knife4j | 3.x | API 文档 |

### 5.3 分页查询兼容 SQL Server 2008

```java
/**
 * SQL Server 2008 不支持 OFFSET/FETCH，使用 ROW_NUMBER() 分页
 */
public class SqlServer2008Dialect {
    
    public static String buildPageSql(String originalSql, int pageNum, int pageSize) {
        int startRow = (pageNum - 1) * pageSize + 1;
        int endRow = pageNum * pageSize;
        
        return "SELECT * FROM ("
             + "SELECT ROW_NUMBER() OVER (ORDER BY (SELECT 0)) AS _row_num, _t.* "
             + "FROM (" + originalSql + ") _t"
             + ") _page "
             + "WHERE _page._row_num BETWEEN " + startRow + " AND " + endRow;
    }
}
```
