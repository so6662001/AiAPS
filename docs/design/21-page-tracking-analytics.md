# AiAPS — 页面埋点与用户行为分析设计

> 版本：1.0 | 最后更新：2026-03-17
>
> 目标：通过页面埋点采集用户行为数据，分析功能使用频率、用户操作习惯、
> 页面实际价值，为产品迭代和客户成功提供数据支撑。

---

## 0. 设计目标

```
三个核心问题:

  1. 哪些功能在用，哪些没人用？
     → 功能使用频率排名 → 指导产品迭代优先级

  2. 用户怎么用的？
     → 操作路径分析 → 发现使用障碍和优化机会

  3. 每个页面的实际价值是什么？
     → 页面停留时长 + 操作转化率 → 评估功能ROI
```

---

## 1. 埋点分类体系

### 1.1 三类埋点

```
┌─────────────────────────────────────────────────────────────────┐
│                       埋点分类                                   │
│                                                                 │
│  ┌─ 页面埋点 (Page View) ─────────────────────────────────┐    │
│  │  自动采集, 无需手动埋                                    │    │
│  │  · 页面进入/离开                                        │    │
│  │  · 停留时长                                             │    │
│  │  · 来源页面(从哪里跳转来的)                              │    │
│  │  · 用户角色                                             │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─ 操作埋点 (Action Track) ──────────────────────────────┐    │
│  │  关键操作手动埋                                          │    │
│  │  · 按钮点击 (如: 开始MRP运算 / 自动排产 / 确认套料)      │    │
│  │  · 表单提交 (如: 创建需求 / 提交报工 / 确认入库)         │    │
│  │  · 查询操作 (如: 追溯查询 / 库存查询)                    │    │
│  │  · 导出操作                                             │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─ 业务埋点 (Business Event) ────────────────────────────┐    │
│  │  业务关键节点自动记录                                     │    │
│  │  · MRP运算完成 (耗时/需求数/计划数)                      │    │
│  │  · 排产生成 (任务数/产线数)                              │    │
│  │  · 套料方案确认 (利用率/合并合同数)                       │    │
│  │  · 报工提交 (成材率)                                    │    │
│  │  · 追溯查询 (查询方式/耗时)                              │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 埋点命名规范

```
格式: {模块}.{页面}.{动作}

示例:
  mrp.workbench.run_start          MRP工作台-开始运算
  mrp.workbench.run_complete       MRP工作台-运算完成
  mrp.plan_orders.batch_confirm    计划订单-批量确认
  schedule.gantt.auto_schedule     排产甘特图-自动排产
  schedule.gantt.drag_move         排产甘特图-拖拽移动
  schedule.gantt.insert_order      排产甘特图-插单
  nesting.multi.optimize           合并套料-自动优化
  nesting.multi.confirm            合并套料-确认转排产
  inventory.stock.query            库存查询-执行查询
  inventory.issue.execute          领料管理-执行出库
  trace.center.scan_barcode        追溯中心-扫码查询
  trace.center.trace_contract      追溯中心-合同追溯
  production.report.submit         报工-提交报工
  receipt.confirm                  入库-确认入库
  dashboard.view                   首页-查看
  report.contract_progress.query   合同进度-查询
```

---

## 2. 数据模型

### 2.1 页面访问记录 (analytics_page_view)

```sql
CREATE TABLE analytics_page_view (
    view_id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    
    -- ═══ 用户信息 ═══
    user_id             VARCHAR(50)   NOT NULL,     -- 用户ID
    user_name           NVARCHAR(50)  NULL,         -- 用户名
    user_role           VARCHAR(30)   NULL,         -- 角色: ADMIN/PLANNER/WORKSHOP/WAREHOUSE
    
    -- ═══ 页面信息 ═══
    page_path           VARCHAR(200)  NOT NULL,     -- 页面路径: /mrp, /schedule, /trace
    page_name           NVARCHAR(50)  NULL,         -- 页面名称: MRP工作台
    page_module         VARCHAR(30)   NULL,         -- 模块: mrp/schedule/inventory/trace
    
    -- ═══ 访问信息 ═══
    enter_time          DATETIME      NOT NULL,     -- 进入时间
    leave_time          DATETIME      NULL,         -- 离开时间
    duration_seconds    INT           NULL,         -- 停留时长(秒)
    from_page           VARCHAR(200)  NULL,         -- 来源页面
    
    -- ═══ 设备信息 ═══
    device_type         VARCHAR(20)   NULL,         -- PC/ANDROID/TABLET
    screen_width        INT           NULL,
    browser             VARCHAR(50)   NULL,
    
    -- ═══ 上下文 ═══
    session_id          VARCHAR(60)   NULL,         -- 会话ID
    client_ip           VARCHAR(50)   NULL
);

CREATE INDEX IX_pv_user ON analytics_page_view(user_id, enter_time);
CREATE INDEX IX_pv_page ON analytics_page_view(page_path, enter_time);
CREATE INDEX IX_pv_time ON analytics_page_view(enter_time);
```

### 2.2 操作行为记录 (analytics_action_track)

```sql
CREATE TABLE analytics_action_track (
    track_id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    
    -- ═══ 用户 ═══
    user_id             VARCHAR(50)   NOT NULL,
    user_role           VARCHAR(30)   NULL,
    
    -- ═══ 操作信息 ═══
    event_code          VARCHAR(100)  NOT NULL,     -- 事件编码: mrp.workbench.run_start
    event_name          NVARCHAR(100) NULL,         -- 事件名称: MRP开始运算
    event_category      VARCHAR(30)   NOT NULL,     -- PAGE_VIEW/ACTION/BUSINESS
    page_path           VARCHAR(200)  NULL,         -- 所在页面
    
    -- ═══ 事件参数(灵活存储) ═══
    param1_key          VARCHAR(50)   NULL,
    param1_value        NVARCHAR(200) NULL,
    param2_key          VARCHAR(50)   NULL,
    param2_value        NVARCHAR(200) NULL,
    param3_key          VARCHAR(50)   NULL,
    param3_value        NVARCHAR(200) NULL,
    
    -- ═══ 结果 ═══
    result_status       VARCHAR(20)   NULL,         -- SUCCESS/FAIL/CANCEL
    result_duration_ms  BIGINT        NULL,         -- 操作耗时(毫秒)
    
    -- ═══ 时间 ═══
    event_time          DATETIME      NOT NULL DEFAULT GETDATE(),
    session_id          VARCHAR(60)   NULL
);

CREATE INDEX IX_at_event ON analytics_action_track(event_code, event_time);
CREATE INDEX IX_at_user ON analytics_action_track(user_id, event_time);
CREATE INDEX IX_at_time ON analytics_action_track(event_time);
CREATE INDEX IX_at_category ON analytics_action_track(event_category, event_time);
```

### 2.3 每日汇总表 (analytics_daily_summary)

```sql
-- 预聚合: 每日定时任务汇总, 加速报表查询
CREATE TABLE analytics_daily_summary (
    summary_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    summary_date        DATE          NOT NULL,
    
    -- ═══ 页面维度 ═══
    page_path           VARCHAR(200)  NOT NULL,
    page_module         VARCHAR(30)   NULL,
    
    -- ═══ 汇总指标 ═══
    pv_count            INT           NOT NULL DEFAULT 0,  -- 页面访问次数
    uv_count            INT           NOT NULL DEFAULT 0,  -- 独立访客数
    avg_duration_sec    INT           NULL,                 -- 平均停留时长
    max_duration_sec    INT           NULL,                 -- 最长停留
    bounce_count        INT           NOT NULL DEFAULT 0,  -- 跳出数(停留<5秒)
    
    -- ═══ 操作指标 ═══
    action_count        INT           NOT NULL DEFAULT 0,  -- 操作次数
    submit_count        INT           NOT NULL DEFAULT 0,  -- 提交次数
    query_count         INT           NOT NULL DEFAULT 0,  -- 查询次数
    export_count        INT           NOT NULL DEFAULT 0,  -- 导出次数
    
    CONSTRAINT UK_daily_page UNIQUE (summary_date, page_path)
);

CREATE INDEX IX_ds_date ON analytics_daily_summary(summary_date);
```

---

## 3. 埋点方案——各页面埋点清单

### 3.1 全局自动埋点 (路由级)

```
通过 Vue Router 的 beforeEach/afterEach 钩子自动采集:

  进入页面 → 记录 enter_time, page_path, from_page, user_id
  离开页面 → 计算 duration = leave_time - enter_time, 上报

所有16个路由页面自动覆盖, 无需逐页编码。
```

### 3.2 各页面操作埋点清单

```
┌──────────────────┬──────────────────────────────┬──────────────┐
│ 页面              │ 埋点事件                      │ 关键参数      │
├──────────────────┼──────────────────────────────┼──────────────┤
│ Dashboard        │ dashboard.view               │ —            │
│                  │ dashboard.alert_click        │ alertType    │
├──────────────────┼──────────────────────────────┼──────────────┤
│ 物料管理          │ base.material.create         │ categoryCode │
│                  │ base.material.edit           │ prdtId       │
│                  │ base.material.search         │ keyword      │
├──────────────────┼──────────────────────────────┼──────────────┤
│ BOM管理           │ base.bom.create              │ —            │
│                  │ base.bom.expand_tree         │ bomId        │
├──────────────────┼──────────────────────────────┼──────────────┤
│ 需求管理          │ demand.create                │ source,lines │
│                  │ demand.expand_detail         │ demandId     │
├──────────────────┼──────────────────────────────┼──────────────┤
│ MRP工作台         │ mrp.workbench.run_start      │ runType      │
│                  │ mrp.workbench.run_complete   │ duration,    │
│                  │                              │ planCount    │
│                  │ mrp.plan_orders.batch_confirm│ count        │
│                  │ mrp.plan_orders.batch_cancel │ count        │
│                  │ mrp.plan_orders.view_detail  │ planOrderId  │
├──────────────────┼──────────────────────────────┼──────────────┤
│ 计划订单          │ mrp.plan_orders.filter       │ filterFields │
│                  │ mrp.plan_orders.export       │ count        │
├──────────────────┼──────────────────────────────┼──────────────┤
│ 排产甘特图        │ schedule.gantt.auto_schedule │ strategy,    │
│                  │                              │ orderCount   │
│                  │ schedule.gantt.drag_move     │ scheduleId   │
│                  │ schedule.gantt.insert_order  │ priority     │
│                  │ schedule.gantt.lock          │ count        │
│                  │ schedule.gantt.view_switch   │ viewType     │
│                  │ schedule.gantt.simulate      │ —            │
├──────────────────┼──────────────────────────────┼──────────────┤
│ 排产列表          │ schedule.list.filter         │ filterFields │
│                  │ schedule.list.view_detail    │ scheduleId   │
│                  │ schedule.list.confirm        │ scheduleId   │
├──────────────────┼──────────────────────────────┼──────────────┤
│ 合并套料          │ nesting.multi.load_pool      │ mode         │
│                  │ nesting.multi.auto_collect   │ —            │
│                  │ nesting.multi.optimize       │ groupKey,    │
│                  │                              │ utilization  │
│                  │ nesting.multi.confirm        │ contractCount│
│                  │ nesting.multi.mode_switch    │ mode         │
├──────────────────┼──────────────────────────────┼──────────────┤
│ 库存查询          │ inventory.stock.query        │ filters      │
│                  │ inventory.stock.view_binds   │ stockId      │
│                  │ inventory.stock.export       │ count        │
├──────────────────┼──────────────────────────────┼──────────────┤
│ 领料管理          │ inventory.issue.create       │ scheduleId   │
│                  │ inventory.issue.approve      │ issueId      │
│                  │ inventory.issue.execute      │ issueId      │
├──────────────────┼──────────────────────────────┼──────────────┤
│ 报工记录          │ production.report.create     │ scheduleId,  │
│                  │                              │ yieldRate    │
│                  │ production.report.filter     │ dateRange    │
├──────────────────┼──────────────────────────────┼──────────────┤
│ 追溯中心          │ trace.center.search          │ searchMode,  │
│                  │                              │ keyword      │
│                  │ trace.center.result_expand   │ nodeType     │
├──────────────────┼──────────────────────────────┼──────────────┤
│ 合同进度          │ report.contract.query        │ contractNo   │
│                  │ report.contract.export       │ —            │
└──────────────────┴──────────────────────────────┴──────────────┘

总计: 约 45 个埋点事件
```

---

## 4. 分析报表设计

### 4.1 报表清单

```
┌──┬──────────────────┬──────────────────────────────┬────────────┐
│# │报表名称           │核心指标                       │更新频率    │
├──┼──────────────────┼──────────────────────────────┼────────────┤
│A1│功能使用热力图     │各页面PV/UV/平均停留时长        │每日        │
│A2│功能价值排名       │使用频次×停留时长×操作转化率    │每周        │
│A3│用户活跃度分析     │DAU/WAU/MAU + 留存率           │每日        │
│A4│操作路径分析       │常见页面跳转路径(桑基图)        │每周        │
│A5│功能采纳率         │各功能首次使用人数/总用户数     │每月        │
│A6│操作效率分析       │关键操作平均耗时趋势            │每日        │
│A7│异常操作告警       │频繁失败/异常停留/反复操作       │实时        │
│A8│角色使用对比       │不同角色的功能使用分布           │每月        │
└──┴──────────────────┴──────────────────────────────┴────────────┘
```

### 4.2 A1 — 功能使用热力图

```
┌──────────────────────────────────────────────────────────────────────┐
│  功能使用热力图                                    [本周▼] [导出]    │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─── 各页面使用频次 (按PV降序) ──────────────────────────────────┐ │
│  │                                                                │ │
│  │  排产甘特图     ████████████████████████████  1,250 PV  85 UV  │ │
│  │  MRP工作台      ██████████████████████        980 PV   42 UV  │ │
│  │  库存查询       █████████████████████         920 PV   68 UV  │ │
│  │  Dashboard      ████████████████████          880 PV   95 UV  │ │
│  │  报工记录       ████████████████              720 PV   35 UV  │ │
│  │  需求管理       ██████████████                650 PV   28 UV  │ │
│  │  追溯中心       ████████████                  540 PV   22 UV  │ │
│  │  合同进度       ███████████                   480 PV   30 UV  │ │
│  │  领料管理       █████████                     380 PV   18 UV  │ │
│  │  合并套料       ████████                      320 PV   12 UV  │ │
│  │  排产列表       ███████                       280 PV   25 UV  │ │
│  │  计划订单       ██████                        240 PV   15 UV  │ │
│  │  物料管理       █████                         200 PV   10 UV  │ │
│  │  BOM管理        ███                           120 PV    8 UV  │ │
│  │  工作中心       ██                             80 PV    5 UV  │ │
│  │  套裁方案       █                              40 PV    3 UV  │ │
│  │                                                                │ │
│  │  颜色: 🟥>1000  🟧500~1000  🟨200~500  🟩<200                 │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  ┌─── 平均停留时长 (秒) ──────────────────────────────────────────┐ │
│  │  排产甘特图  ████████████████  480s (8min) ← 最长, 核心页面     │ │
│  │  MRP工作台   ███████████       330s (5.5min)                    │ │
│  │  合并套料    ██████████        300s (5min) ← 操作复杂度高       │ │
│  │  合同进度    █████████         270s                              │ │
│  │  追溯中心    ████████          240s                              │ │
│  │  需求管理    ███████           210s                              │ │
│  │  库存查询    ██████            180s                              │ │
│  │  ...                                                            │ │
│  │  Dashboard   ██                60s ← 浏览型, 停留短正常          │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### 4.3 A2 — 功能价值排名

```
┌──────────────────────────────────────────────────────────────────────┐
│  功能价值评估                                      [本月▼]          │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  价值评分 = 使用频次(30%) × 停留深度(25%) × 操作转化率(25%) × 覆盖率(20%)│
│                                                                      │
│  ┌──┬──────────────┬──────┬──────┬──────┬──────┬──────┬────────────┐│
│  │# │功能          │使用次│停留  │转化率│覆盖率│价值分│价值判定    ││
│  │  │              │(PV)  │(分钟)│(%)   │(%)   │      │            ││
│  ├──┼──────────────┼──────┼──────┼──────┼──────┼──────┼────────────┤│
│  │1 │排产甘特图    │5,200 │8.0   │72%   │85%   │92.5  │★ 核心功能  ││
│  │2 │MRP工作台     │4,100 │5.5   │68%   │42%   │78.3  │★ 核心功能  ││
│  │3 │库存查询      │3,800 │3.0   │45%   │68%   │65.2  │高价值      ││
│  │4 │报工记录      │3,000 │2.5   │82%   │35%   │62.8  │高价值      ││
│  │5 │追溯中心      │2,200 │4.0   │55%   │22%   │48.5  │中等价值    ││
│  │6 │合并套料      │1,300 │5.0   │78%   │12%   │45.2  │高潜力      ││
│  │  │              │      │      │      │      │      │(覆盖率低)  ││
│  │7 │合同进度      │2,000 │4.5   │30%   │30%   │40.1  │中等价值    ││
│  │..│...           │...   │...   │...   │...   │...   │            ││
│  │15│套裁方案      │160   │1.5   │20%   │3%    │8.2   │低价值      ││
│  │  │              │      │      │      │      │      │(考虑合并)  ││
│  └──┴──────────────┴──────┴──────┴──────┴──────┴──────┴────────────┘│
│                                                                      │
│  解读:                                                               │
│  · 排产甘特图+MRP工作台 = 系统核心, 持续优化                         │
│  · 合并套料: 价值高但覆盖率低 → 需加强推广和培训                     │
│  · 套裁方案: 价值低 → 考虑合并到合并套料页面中                       │
│  · 工作中心/BOM管理: 配置类, 使用少属正常                            │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### 4.4 A3 — 用户活跃度

```
┌──────────────────────────────────────────────────────────────────────┐
│  用户活跃度分析                                                      │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─── 活跃用户趋势 (最近30天) ───────────────────────────────────┐ │
│  │  50 ┤                                                          │ │
│  │  40 ┤     ●──●──●──●     ●──●──●──●     ●──●  DAU             │ │
│  │  30 ┤  ●──        ──●──●──         ──●──                       │ │
│  │  20 ┤                                                          │ │
│  │     └──┬──────────┬──────────┬──────────┬                      │ │
│  │       W1         W2         W3         W4                      │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  DAU: 35  |  WAU: 68  |  MAU: 95  |  DAU/MAU: 36.8%                │
│  7日留存: 82%  |  30日留存: 75%                                      │
│                                                                      │
│  ┌─── 按角色分布 ──────────────────────────────────────────────┐   │
│  │  计划员    ████████████████████  42% (40人)                  │   │
│  │  车间主任  ██████████████        28% (27人)                  │   │
│  │  仓库管理  ████████              18% (17人)                  │   │
│  │  管理层    █████                 12% (11人)                  │   │
│  └──────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
```

### 4.5 A4 — 操作路径分析

```
常见操作路径 (Top 5):

  路径1 (32%用户): Dashboard → MRP工作台 → 计划订单 → 排产甘特图
  路径2 (25%用户): Dashboard → 排产甘特图 → 排产列表
  路径3 (18%用户): Dashboard → 库存查询 → 领料管理
  路径4 (12%用户): 需求管理 → MRP工作台 → 合并套料
  路径5 ( 8%用户): 追溯中心 (直接进入, 独立使用)

发现:
  · 路径1是"计划员日常流程" → 优化这条路径的流畅度
  · 追溯中心多为独立使用 → 考虑做成快捷入口/独立APP
  · 合并套料入口深 → 考虑在MRP工作台加快捷跳转
```

### 4.6 A6 — 操作效率分析

```
┌──────────────────────────────────────────────────────────────────────┐
│  关键操作效率趋势                                                     │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌────────────────────┬──────┬──────┬──────┬──────┬────────────┐    │
│  │操作                │本周  │上周  │趋势  │目标  │达标        │    │
│  ├────────────────────┼──────┼──────┼──────┼──────┼────────────┤    │
│  │MRP完整运算         │22s   │25s   │↓改善 │30s   │✅          │    │
│  │自动排产(50单)      │8s    │9s    │↓改善 │10s   │✅          │    │
│  │合并套料优化        │3s    │3s    │→持平 │5s    │✅          │    │
│  │追溯查询(扫码)      │1.2s  │1.5s  │↓改善 │2s    │✅          │    │
│  │库存查询            │0.8s  │0.9s  │↓改善 │1s    │✅          │    │
│  │报工提交            │5s    │5s    │→持平 │10s   │✅          │    │
│  │排产拖拽调整        │0.5s  │0.5s  │→持平 │1s    │✅          │    │
│  │入库确认            │8s    │12s   │↓改善 │15s   │✅          │    │
│  └────────────────────┴──────┴──────┴──────┴──────┴────────────┘    │
│                                                                      │
│  ⚠ 需关注: 无                                                        │
│  ✅ 所有操作均在目标范围内                                            │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 5. 前端埋点实现方案

### 5.1 技术方案

```
方案: Vue3 插件 + 路由守卫 + 指令

  ┌─────────────────────────────────────────────────────────────┐
  │                                                             │
  │  1. 路由守卫 (自动采集页面访问)                               │
  │     router.beforeEach → 记录上一页离开时间, 计算停留          │
  │     router.afterEach  → 记录新页面进入时间                    │
  │                                                             │
  │  2. 全局方法 $track(eventCode, params)                       │
  │     在需要埋点的按钮/操作处手动调用                           │
  │     例: $track('mrp.workbench.run_start', { runType })       │
  │                                                             │
  │  3. 数据上报策略                                             │
  │     · 页面访问: 离开页面时上报(含停留时长)                    │
  │     · 操作事件: 立即上报                                     │
  │     · 批量上报: 小事件攒5条一起发(减少请求)                   │
  │     · 离线缓存: 网络断开时存localStorage, 恢复后补报          │
  │                                                             │
  │  4. 不影响业务性能                                           │
  │     · 上报使用 navigator.sendBeacon (不阻塞页面)              │
  │     · 或 setTimeout 异步发送                                 │
  │     · 后端接收用独立线程池, 不影响业务接口                    │
  │                                                             │
  └─────────────────────────────────────────────────────────────┘
```

### 5.2 后端接口

```
# ═══ 数据采集 (前端上报) ═══
POST /api/v1/analytics/page-view          # 上报页面访问
POST /api/v1/analytics/action             # 上报操作事件
POST /api/v1/analytics/batch              # 批量上报

# ═══ 分析报表 (管理后台查看) ═══
GET  /api/v1/analytics/page-heat          # A1 功能使用热力图
GET  /api/v1/analytics/feature-value      # A2 功能价值排名
GET  /api/v1/analytics/user-activity      # A3 用户活跃度
GET  /api/v1/analytics/path-analysis      # A4 操作路径分析
GET  /api/v1/analytics/adoption-rate      # A5 功能采纳率
GET  /api/v1/analytics/efficiency         # A6 操作效率分析
GET  /api/v1/analytics/alerts             # A7 异常操作告警
GET  /api/v1/analytics/role-usage         # A8 角色使用对比

# 通用参数: dateFrom, dateTo, pageModule, userRole
```

---

## 6. 性能监控与企业微信告警

### 6.1 性能监控指标

```
监控三类性能问题:

  ┌─────────────────────────────────────────────────────────────────┐
  │  类型1: 页面加载慢                                               │
  │                                                                 │
  │  采集: 前端 Performance API                                      │
  │  指标:                                                          │
  │    · 首屏加载时间 (FCP, First Contentful Paint)                  │
  │    · 页面完全加载时间 (Load)                                     │
  │    · 最大内容渲染时间 (LCP, Largest Contentful Paint)             │
  │  阈值:                                                          │
  │    · FCP > 3秒 → 黄色预警                                       │
  │    · FCP > 5秒 → 红色告警                                       │
  │    · LCP > 5秒 → 黄色预警                                       │
  │    · LCP > 8秒 → 红色告警                                       │
  ├─────────────────────────────────────────────────────────────────┤
  │  类型2: API接口慢                                                │
  │                                                                 │
  │  采集: Axios 拦截器记录请求耗时                                   │
  │  指标:                                                          │
  │    · 接口响应时间 (从请求发出到响应返回)                           │
  │  阈值(按接口类型差异化):                                         │
  │    · 普通查询接口 > 2秒 → 预警, > 5秒 → 告警                    │
  │    · MRP运算接口  > 60秒 → 预警, > 120秒 → 告警                 │
  │    · 排产接口     > 15秒 → 预警, > 30秒 → 告警                  │
  │    · 追溯查询     > 3秒  → 预警, > 10秒 → 告警                  │
  │    · 接口错误率   > 5%   → 预警, > 10% → 告警                   │
  ├─────────────────────────────────────────────────────────────────┤
  │  类型3: 页面交互卡顿                                             │
  │                                                                 │
  │  采集: Long Task API / FID (First Input Delay)                   │
  │  指标:                                                          │
  │    · 长任务 (>50ms的JS执行)                                      │
  │    · 首次输入延迟                                                │
  │  阈值:                                                          │
  │    · 单页面长任务 > 10次/分钟 → 预警                              │
  │    · FID > 300ms → 告警                                         │
  └─────────────────────────────────────────────────────────────────┘
```

### 6.2 性能数据表 (analytics_performance)

```sql
CREATE TABLE analytics_performance (
    perf_id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    
    -- ═══ 来源 ═══
    source_type         VARCHAR(10)   NOT NULL,     -- PAGE=页面 API=接口
    user_id             VARCHAR(50)   NULL,
    session_id          VARCHAR(60)   NULL,
    
    -- ═══ 页面性能 (source_type=PAGE) ═══
    page_path           VARCHAR(200)  NULL,
    fcp_ms              INT           NULL,         -- 首屏加载(ms)
    lcp_ms              INT           NULL,         -- 最大内容渲染(ms)
    fid_ms              INT           NULL,         -- 首次输入延迟(ms)
    load_ms             INT           NULL,         -- 完全加载(ms)
    long_task_count     INT           NULL,         -- 长任务数
    
    -- ═══ API性能 (source_type=API) ═══
    api_path            VARCHAR(200)  NULL,         -- 接口路径
    api_method          VARCHAR(10)   NULL,         -- GET/POST/PUT
    response_ms         INT           NULL,         -- 响应时间(ms)
    http_status         INT           NULL,         -- HTTP状态码
    is_error            BIT           NOT NULL DEFAULT 0,
    error_message       NVARCHAR(500) NULL,
    
    -- ═══ 告警 ═══
    alert_level         VARCHAR(10)   NULL,         -- NORMAL/WARNING/CRITICAL
    alert_sent          BIT           NOT NULL DEFAULT 0,  -- 是否已发送告警
    
    -- ═══ 时间 ═══
    record_time         DATETIME      NOT NULL DEFAULT GETDATE()
);

CREATE INDEX IX_perf_time ON analytics_performance(record_time);
CREATE INDEX IX_perf_page ON analytics_performance(page_path, record_time);
CREATE INDEX IX_perf_api ON analytics_performance(api_path, record_time);
CREATE INDEX IX_perf_alert ON analytics_performance(alert_level, alert_sent);
```

### 6.3 告警规则配置表 (analytics_alert_rule)

```sql
CREATE TABLE analytics_alert_rule (
    rule_id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    rule_name           NVARCHAR(100) NOT NULL,     -- 规则名称
    rule_type           VARCHAR(20)   NOT NULL,     -- PAGE_LOAD/API_SLOW/API_ERROR/INTERACTION
    
    -- ═══ 匹配条件 ═══
    match_path          VARCHAR(200)  NULL,         -- 匹配路径(NULL=全部)
    match_method        VARCHAR(10)   NULL,         -- 匹配方法
    
    -- ═══ 阈值 ═══
    warning_threshold   INT           NOT NULL,     -- 预警阈值(ms或次数或百分比×100)
    critical_threshold  INT           NOT NULL,     -- 告警阈值
    threshold_unit      VARCHAR(10)   NOT NULL,     -- MS=毫秒 COUNT=次数 PCT=百分比
    
    -- ═══ 触发条件 ═══
    window_minutes      INT           NOT NULL DEFAULT 5,   -- 统计窗口(分钟)
    min_sample_count    INT           NOT NULL DEFAULT 3,   -- 最小样本数(避免误报)
    
    -- ═══ 通知 ═══
    notify_wecom        BIT           NOT NULL DEFAULT 1,   -- 是否推送企业微信
    notify_webhook_url  NVARCHAR(500) NULL,                 -- 企业微信Webhook地址
    notify_interval_min INT           NOT NULL DEFAULT 30,  -- 最小通知间隔(分钟, 防刷)
    last_notify_time    DATETIME      NULL,                 -- 上次通知时间
    
    is_active           BIT           NOT NULL DEFAULT 1
);

-- 预置规则示例:
-- 普通页面加载 FCP>3000ms预警 FCP>5000ms告警
-- MRP接口 响应>60000ms预警 >120000ms告警
-- 排产接口 响应>15000ms预警 >30000ms告警
-- 追溯查询 响应>3000ms预警 >10000ms告警
-- 全局API错误率 >5%预警 >10%告警
```

### 6.4 企业微信Webhook推送

```
企业微信机器人配置:

  1. 在企业微信群中添加机器人 → 获取 Webhook URL
     格式: https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxxxx

  2. 系统配置:
     analytics_alert_rule.notify_webhook_url = 上述URL
     或全局配置: sys_config.wecom_perf_webhook = URL

  3. 推送消息格式:
```

```
┌──────────────────────────────────────────────────────────────┐
│  企业微信告警消息格式                                          │
│                                                              │
│  🔴 AiAPS 性能告警                                           │
│  ──────────────────                                          │
│  告警级别: 严重 (CRITICAL)                                    │
│  告警类型: API接口响应超时                                     │
│  接口: POST /api/v1/mrp/run                                  │
│  响应时间: 135秒 (阈值: 120秒)                                │
│  影响用户: 张三 (计划员)                                      │
│  发生时间: 2026-03-17 14:35:22                                │
│  最近5分钟: 3次超时 / 5次请求                                  │
│  ──────────────────                                          │
│  建议: 检查MRP运算数据量或数据库性能                           │
│                                                              │
│                                                              │
│  🟡 AiAPS 性能预警                                           │
│  ──────────────────                                          │
│  告警级别: 预警 (WARNING)                                     │
│  告警类型: 页面加载缓慢                                       │
│  页面: /schedule (排产甘特图)                                  │
│  首屏加载: 4.2秒 (阈值: 3秒)                                  │
│  影响用户: 5人 (最近5分钟)                                     │
│  发生时间: 2026-03-17 14:30:15                                │
│  ──────────────────                                          │
│  建议: 检查甘特图数据量或前端渲染性能                          │
│                                                              │
│                                                              │
│  🔴 AiAPS 错误率告警                                          │
│  ──────────────────                                          │
│  告警级别: 严重 (CRITICAL)                                    │
│  告警类型: API错误率异常                                       │
│  接口: GET /api/v1/stock                                     │
│  错误率: 12.5% (阈值: 10%)                                   │
│  最近5分钟: 5次错误 / 40次请求                                 │
│  错误信息: "Connection refused" ×3, "Timeout" ×2              │
│  发生时间: 2026-03-17 15:02:33                                │
│  ──────────────────                                          │
│  建议: 检查数据库连接或网络状态                                │
└──────────────────────────────────────────────────────────────┘
```

### 6.5 告警处理流程

```
┌─────────────────────────────────────────────────────────────────┐
│  性能告警处理流程                                                │
│                                                                 │
│  前端/后端 采集性能数据                                          │
│       │                                                         │
│       ▼                                                         │
│  上报到 analytics_performance 表                                 │
│       │                                                         │
│       ▼                                                         │
│  告警检测服务 (每分钟执行一次)                                    │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  1. 读取所有 is_active=true 的告警规则                    │    │
│  │  2. 对每条规则:                                          │    │
│  │     a. 查询最近 window_minutes 内的性能数据               │    │
│  │     b. 计算: 平均值 / 最大值 / 错误率                     │    │
│  │     c. 对比阈值: > critical → CRITICAL                   │    │
│  │                  > warning → WARNING                     │    │
│  │                  否则 → NORMAL                           │    │
│  │  3. 如果触发告警:                                        │    │
│  │     a. 检查 last_notify_time (防止频繁发送)               │    │
│  │     b. 距上次通知 > notify_interval_min → 发送            │    │
│  │     c. 调用企业微信 Webhook 推送                          │    │
│  │     d. 更新 last_notify_time                             │    │
│  │     e. 记录告警历史                                      │    │
│  └─────────────────────────────────────────────────────────┘    │
│       │                                                         │
│       ▼                                                         │
│  企业微信群 接收告警                                              │
│  ├── 研发团队群: 接收页面加载+交互卡顿告警                       │
│  ├── 运维团队群: 接收API超时+错误率告警                          │
│  └── 产品团队群: 接收每日性能汇总报告                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 6.6 企业微信Webhook调用方式

```java
/**
 * 企业微信机器人推送服务
 */
public class WeComNotifyService {
    
    /**
     * 发送Markdown格式的告警消息到企业微信群
     * 
     * @param webhookUrl  机器人Webhook地址
     * @param content     Markdown内容
     */
    public void sendAlert(String webhookUrl, String title, String content) {
        // POST请求体:
        // {
        //   "msgtype": "markdown",
        //   "markdown": {
        //     "content": "## 🔴 AiAPS 性能告警\n> 告警级别: **严重**\n..."
        //   }
        // }
        
        Map<String, Object> body = new HashMap<>();
        body.put("msgtype", "markdown");
        
        Map<String, String> markdown = new HashMap<>();
        markdown.put("content", "## " + title + "\n" + content);
        body.put("markdown", markdown);
        
        // 使用 RestTemplate 或 OkHttp 发送POST
        restTemplate.postForEntity(webhookUrl, body, String.class);
    }
    
    /**
     * 构建告警消息内容
     */
    public String buildAlertContent(String alertLevel, String alertType,
                                     String target, String currentValue,
                                     String threshold, String suggestion) {
        StringBuilder sb = new StringBuilder();
        sb.append("> 告警级别: **").append(alertLevel).append("**\n");
        sb.append("> 告警类型: ").append(alertType).append("\n");
        sb.append("> 目标: `").append(target).append("`\n");
        sb.append("> 当前值: **").append(currentValue).append("**");
        sb.append(" (阈值: ").append(threshold).append(")\n");
        sb.append("> 时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
        sb.append("> 建议: ").append(suggestion);
        return sb.toString();
    }
}
```

### 6.7 性能告警报表

```
┌──────────────────────────────────────────────────────────────────────┐
│  性能监控中心                                      [今日] [本周]     │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─── 实时状态 ──────────────────────────────────────────────────┐ │
│  │  页面健康度: 🟢 98.5%   API健康度: 🟡 95.2%   告警: 2条      │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  ┌─── 页面加载性能 (P95) ─────────────────────────────────────┐   │
│  │                                                              │   │
│  │  页面            FCP(ms)    LCP(ms)    状态                  │   │
│  │  ─────────────────────────────────────────────               │   │
│  │  Dashboard       450        800        🟢 正常               │   │
│  │  排产甘特图      1,200      2,500      🟢 正常               │   │
│  │  MRP工作台       800        1,500      🟢 正常               │   │
│  │  库存查询        600        1,200      🟢 正常               │   │
│  │  合并套料        1,500      3,200      🟡 偏慢               │   │
│  │  追溯中心        900        1,800      🟢 正常               │   │
│  │                                                              │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌─── API响应时间 (P95, 最近1小时) ───────────────────────────┐   │
│  │                                                              │   │
│  │  接口                     P95(ms)   错误率   状态            │   │
│  │  ──────────────────────────────────────────────              │   │
│  │  POST /v1/mrp/run         22,000    0%       🟢 正常        │   │
│  │  POST /v1/schedule/auto    8,500    0%       🟢 正常        │   │
│  │  GET  /v1/schedule/gantt   1,200    0%       🟢 正常        │   │
│  │  GET  /v1/stock            800      0.5%     🟢 正常        │   │
│  │  GET  /v1/trace/barcode    1,100    0%       🟢 正常        │   │
│  │  POST /v1/nesting/multi    3,200    2%       🟡 偏慢        │   │
│  │                                                              │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌─── 最近告警 ──────────────────────────────────────────────┐    │
│  │  🔴 14:35 POST /v1/mrp/run 响应135s (阈值120s) → 已推送     │    │
│  │  🟡 14:30 /schedule 首屏4.2s (阈值3s) → 已推送              │    │
│  │  🟢 12:00 全部正常                                          │    │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### 6.8 每日性能汇总推送 (企业微信)

```
每日 08:30 自动推送到产品团队群:

  ┌──────────────────────────────────────────────────────────┐
  │  📊 AiAPS 性能日报 (2026-03-17)                          │
  │  ──────────────────────────                              │
  │  页面健康度: **98.5%** (目标≥95%) ✅                      │
  │  API健康度:  **95.2%** (目标≥99%) ⚠                      │
  │                                                          │
  │  **页面加载 Top3 慢:**                                    │
  │  1. 合并套料 FCP=1.5s LCP=3.2s                           │
  │  2. 排产甘特图 FCP=1.2s LCP=2.5s                         │
  │  3. 追溯中心 FCP=0.9s LCP=1.8s                           │
  │                                                          │
  │  **API响应 Top3 慢:**                                     │
  │  1. POST /v1/mrp/run P95=22s                             │
  │  2. POST /v1/schedule/auto P95=8.5s                      │
  │  3. POST /v1/nesting/multi P95=3.2s                      │
  │                                                          │
  │  **告警统计:**                                            │
  │  严重(CRITICAL): 1次  预警(WARNING): 3次                  │
  │                                                          │
  │  **用户活跃:**                                            │
  │  DAU=42  页面PV=3,250  操作次数=1,850                    │
  └──────────────────────────────────────────────────────────┘
```

---

## 7. 数据隐私与合规

```
数据采集原则:

  ✅ 采集: 用户ID、角色、页面路径、操作类型、耗时、设备类型
  ❌ 不采集: 密码、业务数据内容(合同金额/客户信息/物料成本等)
  ❌ 不采集: 精确地理位置、个人隐私信息

  数据保留: 
  · 明细数据保留 90 天(可配置)
  · 汇总数据保留 2 年
  · 超期自动清理(定时任务)

  可关闭:
  · 系统参数: analytics.enabled = true/false
  · 管理员可随时关闭埋点采集
```

---

## 7. 分析页面UI设计

```
┌──────────────────────────────────────────────────────────────────────┐
│  数据分析中心                                                        │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Tab: [使用热力图] [功能价值] [用户活跃] [操作路径] [操作效率]         │
│                                                                      │
│  日期范围: [2026-03-01] ~ [2026-03-17]  [今日] [本周] [本月]         │
│                                                                      │
│  ┌─── 顶部4卡片 ──────────────────────────────────────────────────┐│
│  │  今日PV    今日UV    平均停留    活跃功能数                      ││
│  │  3,250     95       4.2min     14/16                           ││
│  └────────────────────────────────────────────────────────────────┘│
│                                                                      │
│  ┌─── 主体: 当前Tab对应的图表/表格 ──────────────────────────────┐ │
│  │  (根据选中Tab展示对应的A1~A8报表内容)                          │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 8. 与现有设计的关系

```
本文档(21)是独立的新增功能, 不修改现有设计:

  前端新增:
    · src/utils/tracker.ts — 埋点采集工具
    · src/views/analytics/AnalyticsDashboard.vue — 分析中心页面
    · router 新增 /analytics 路由
    · MainLayout 侧边栏新增"数据分析"菜单项

  后端新增:
    · domain: AnalyticsPageView, AnalyticsActionTrack, AnalyticsDailySummary
    · service: AnalyticsCollectService (采集) + AnalyticsQueryService (查询)
    · controller: AnalyticsController (采集+查询)
    · 定时任务: 每日汇总聚合

  不影响:
    · 现有业务逻辑不变
    · 现有API不变
    · 现有表结构不变
    · 埋点数据独立存储, 独立表
```
