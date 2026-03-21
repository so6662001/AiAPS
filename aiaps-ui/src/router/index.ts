import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/LoginView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'base/material',
        name: 'MaterialList',
        component: () => import('@/views/base/MaterialListView.vue'),
        meta: { title: '物料管理' },
      },
      {
        path: 'base/bom',
        name: 'BomList',
        component: () => import('@/views/base/BomListView.vue'),
        meta: { title: 'BOM管理' },
      },
      {
        path: 'base/work-center',
        name: 'WorkCenterList',
        component: () => import('@/views/base/WorkCenterListView.vue'),
        meta: { title: '工作中心' },
      },
      {
        path: 'demand',
        name: 'DemandList',
        component: () => import('@/views/demand/DemandListView.vue'),
        meta: { title: '需求管理' },
      },
      {
        path: 'mrp',
        name: 'MrpWorkbench',
        component: () => import('@/views/mrp/MrpWorkbenchView.vue'),
        meta: { title: 'MRP工作台' },
      },
      {
        path: 'mrp/plan-orders',
        name: 'PlanOrderList',
        component: () => import('@/views/mrp/PlanOrderListView.vue'),
        meta: { title: '计划订单' },
      },
      {
        path: 'schedule',
        name: 'ScheduleGantt',
        component: () => import('@/views/schedule/ScheduleGanttView.vue'),
        meta: { title: '排产甘特图' },
      },
      {
        path: 'schedule/list',
        name: 'ScheduleList',
        component: () => import('@/views/schedule/ScheduleListView.vue'),
        meta: { title: '排产列表' },
      },
      {
        path: 'nesting',
        name: 'NestingList',
        component: () => import('@/views/nesting/NestingListView.vue'),
        meta: { title: '套裁方案' },
      },
      {
        path: 'inventory/stock',
        name: 'StockList',
        component: () => import('@/views/inventory/StockListView.vue'),
        meta: { title: '库存查询' },
      },
      {
        path: 'inventory/issue',
        name: 'MaterialIssueList',
        component: () => import('@/views/inventory/MaterialIssueListView.vue'),
        meta: { title: '领料管理' },
      },
      {
        path: 'production/report',
        name: 'ReportList',
        component: () => import('@/views/production/ReportListView.vue'),
        meta: { title: '报工记录' },
      },
      {
        path: 'trace',
        name: 'TraceCenter',
        component: () => import('@/views/trace/TraceCenterView.vue'),
        meta: { title: '物料追溯' },
      },
      {
        path: 'report/contract-progress',
        name: 'ContractProgress',
        component: () => import('@/views/report/ContractProgressView.vue'),
        meta: { title: '合同进度' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/error/NotFoundView.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth !== false && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
