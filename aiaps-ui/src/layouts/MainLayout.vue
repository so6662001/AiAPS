<template>
  <div class="main-layout">
    <aside class="sidebar" :class="{ 'is-collapsed': appStore.sidebarCollapsed }">
      <div class="sidebar-logo">
        <span class="logo-text">AiAPS</span>
        <span v-if="!appStore.sidebarCollapsed" class="logo-sub">智能排产系统</span>
      </div>

      <el-menu
        :default-active="currentRoute"
        :collapse="appStore.sidebarCollapsed"
        :collapse-transition="false"
        router
        background-color="#1e293b"
        text-color="#94a3b8"
        active-text-color="#ffffff"
        class="sidebar-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>首页</template>
        </el-menu-item>

        <el-sub-menu index="base">
          <template #title>
            <el-icon><Box /></el-icon>
            <span>基础数据</span>
          </template>
          <el-menu-item index="/base/material">物料管理</el-menu-item>
          <el-menu-item index="/base/bom">BOM管理</el-menu-item>
          <el-menu-item index="/base/work-center">工作中心</el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/demand">
          <el-icon><List /></el-icon>
          <template #title>需求管理</template>
        </el-menu-item>

        <el-sub-menu index="mrp">
          <template #title>
            <el-icon><SetUp /></el-icon>
            <span>MRP计划</span>
          </template>
          <el-menu-item index="/mrp">MRP工作台</el-menu-item>
          <el-menu-item index="/mrp/plan-orders">计划订单</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="schedule">
          <template #title>
            <el-icon><Calendar /></el-icon>
            <span>排产调度</span>
          </template>
          <el-menu-item index="/schedule">排产甘特图</el-menu-item>
          <el-menu-item index="/schedule/list">排产列表</el-menu-item>
          <el-menu-item index="/nesting">套裁方案</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="inventory">
          <template #title>
            <el-icon><Files /></el-icon>
            <span>库存管理</span>
          </template>
          <el-menu-item index="/inventory/stock">库存查询</el-menu-item>
          <el-menu-item index="/inventory/issue">领料管理</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="production">
          <template #title>
            <el-icon><Monitor /></el-icon>
            <span>生产执行</span>
          </template>
          <el-menu-item index="/production/report">报工记录</el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/trace">
          <el-icon><Search /></el-icon>
          <template #title>物料追溯</template>
        </el-menu-item>

        <el-sub-menu index="report-center">
          <template #title>
            <el-icon><DataAnalysis /></el-icon>
            <span>报表中心</span>
          </template>
          <el-menu-item index="/report/contract-progress">合同进度</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </aside>

    <div class="main-container">
      <header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="appStore.toggleSidebar">
            <Fold v-if="!appStore.sidebarCollapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item
              v-for="item in breadcrumbs"
              :key="item.path"
              :to="item.path"
            >
              {{ item.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-badge :value="3" :max="99" class="notification-badge">
            <el-icon class="header-icon"><Bell /></el-icon>
          </el-badge>
          <el-dropdown trigger="click">
            <span class="user-info">
              <el-avatar :size="28" class="user-avatar">A</el-avatar>
              <span class="user-name">管理员</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item>个人设置</el-dropdown-item>
                <el-dropdown-item divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const appStore = useAppStore()

const currentRoute = computed(() => route.path)

const breadcrumbs = computed(() => {
  const matched = route.matched.filter((r) => r.meta?.title)
  return matched.map((r) => ({
    path: r.path,
    title: r.meta.title as string,
  }))
})
</script>

<style scoped lang="scss">
.main-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  width: var(--sidebar-width);
  background: var(--bg-sidebar);
  display: flex;
  flex-direction: column;
  transition: width var(--transition-normal);
  flex-shrink: 0;
  overflow: hidden;

  &.is-collapsed {
    width: var(--sidebar-collapsed-width);

    .logo-text {
      font-size: 16px;
    }
  }
}

.sidebar-logo {
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  padding: 24px 0 16px;
  box-sizing: content-box;
  flex-shrink: 0;
}

.logo-text {
  font-size: 22px;
  font-weight: 700;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: 2px;
  transition: font-size var(--transition-normal);
}

.logo-sub {
  font-size: 11px;
  color: #64748b;
  margin-top: 4px;
  letter-spacing: 2px;
}

.sidebar-menu {
  flex: 1;
  overflow-y: auto;
  border-right: none;

  &::-webkit-scrollbar {
    width: 0;
  }
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.header {
  height: var(--header-height);
  background: var(--bg-header);
  border-bottom: 1px solid var(--header-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  font-size: 18px;
  cursor: pointer;
  color: var(--text-secondary);
  transition: color var(--transition-fast);

  &:hover {
    color: var(--color-primary);
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.header-icon {
  font-size: 18px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: color var(--transition-fast);

  &:hover {
    color: var(--color-primary);
  }
}

.notification-badge {
  line-height: 1;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--text-primary);

  .el-icon {
    font-size: 12px;
    color: var(--text-secondary);
  }
}

.user-avatar {
  background: var(--color-primary);
  font-size: 12px;
}

.user-name {
  font-size: 13px;
  font-weight: 500;
}

.content {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  background: var(--bg-page);
}
</style>
