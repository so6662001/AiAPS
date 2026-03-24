<template>
  <el-container class="layout-container">
    <el-aside :width="sidebarCollapsed ? '64px' : '220px'" class="layout-sidebar">
      <div class="sidebar-logo">
        <img src="@/assets/vite.svg" alt="logo" class="logo-icon" />
        <span v-show="!sidebarCollapsed" class="logo-text">AiAPS</span>
      </div>
      <el-scrollbar>
        <el-menu
          :default-active="currentRoute"
          :collapse="sidebarCollapsed"
          router
          class="el-menu--dark"
          background-color="#1e293b"
          text-color="#94a3b8"
          active-text-color="#ffffff"
        >
          <el-menu-item index="/dashboard">
            <el-icon><Monitor /></el-icon>
            <template #title>首页</template>
          </el-menu-item>
          <el-sub-menu index="base">
            <template #title>
              <el-icon><Setting /></el-icon>
              <span>基础数据</span>
            </template>
            <el-menu-item index="/base/material">物料管理</el-menu-item>
            <el-menu-item index="/base/bom">BOM管理</el-menu-item>
            <el-menu-item index="/base/work-center">工作中心</el-menu-item>
          </el-sub-menu>
          <el-menu-item index="/demand">
            <el-icon><Document /></el-icon>
            <template #title>需求管理</template>
          </el-menu-item>
          <el-sub-menu index="mrp">
            <template #title>
              <el-icon><Cpu /></el-icon>
              <span>MRP</span>
            </template>
            <el-menu-item index="/mrp">MRP工作台</el-menu-item>
            <el-menu-item index="/mrp/plan-orders">计划订单</el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="schedule">
            <template #title>
              <el-icon><Calendar /></el-icon>
              <span>排产管理</span>
            </template>
            <el-menu-item index="/schedule">排产甘特图</el-menu-item>
            <el-menu-item index="/schedule/list">排产列表</el-menu-item>
            <el-menu-item index="/nesting">套裁方案</el-menu-item>
            <el-menu-item index="/nesting/multi">合并套料</el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="inventory">
            <template #title>
              <el-icon><Box /></el-icon>
              <span>库存管理</span>
            </template>
            <el-menu-item index="/inventory/stock">库存查询</el-menu-item>
            <el-menu-item index="/inventory/issue">领料管理</el-menu-item>
          </el-sub-menu>
          <el-menu-item index="/production/report">
            <el-icon><DataLine /></el-icon>
            <template #title>报工记录</template>
          </el-menu-item>
          <el-menu-item index="/trace">
            <el-icon><Search /></el-icon>
            <template #title>物料追溯</template>
          </el-menu-item>
          <el-menu-item index="/report/contract-progress">
            <el-icon><TrendCharts /></el-icon>
            <template #title>合同进度</template>
          </el-menu-item>
        </el-menu>
      </el-scrollbar>
    </el-aside>
    <el-container class="layout-main">
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="toggleSidebar">
            <Fold v-if="!sidebarCollapsed" />
            <Expand v-else />
          </el-icon>
          <span class="header-title">{{ currentTitle }}</span>
        </div>
        <div class="header-right">
          <el-badge :value="3" :max="9" class="notify-badge">
            <el-icon :size="18"><Bell /></el-icon>
          </el-badge>
          <el-dropdown trigger="click">
            <div class="user-info">
              <el-avatar :size="28" style="background: #2563eb;">{{ username.charAt(0).toUpperCase() }}</el-avatar>
              <span class="username">{{ username }}</span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="layout-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import {
  Monitor, Setting, Document, Cpu, Calendar, Box,
  DataLine, Search, TrendCharts, Fold, Expand, Bell,
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

const username = ref(localStorage.getItem('username') || '管理员')
const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  router.push('/login')
}

const sidebarCollapsed = computed(() => appStore.sidebarCollapsed)
const currentRoute = computed(() => route.path)
const currentTitle = computed(() => (route.meta.title as string) || '')

function toggleSidebar() {
  appStore.toggleSidebar()
}
</script>

<style scoped lang="scss">
.layout-container {
  height: 100vh;
}

.layout-sidebar {
  background: #1e293b;
  transition: width var(--transition-normal);
  overflow: hidden;
}

.sidebar-logo {
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.logo-icon {
  width: 28px;
  height: 28px;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 2px;
}

.layout-header {
  height: 50px;
  background: var(--bg-header);
  border-bottom: 1px solid var(--header-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: var(--text-secondary);
  transition: color var(--transition-fast);

  &:hover {
    color: var(--color-primary);
  }
}

.header-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.notify-badge {
  cursor: pointer;
}

.user-avatar {
  cursor: pointer;
  background: var(--color-primary);
}

.layout-content {
  background: var(--bg-page);
  padding: 20px;
  overflow-y: auto;
}
</style>
