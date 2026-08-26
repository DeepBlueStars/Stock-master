<template>
  <div class="main-layout">
    <!-- 顶部导航 -->
    <header class="header">
      <div class="header-left">
        <div class="logo">
          <el-icon size="28" color="var(--color-primary)"><TrendCharts /></el-icon>
          <span class="logo-text">TradeSentry</span>
        </div>
        <div class="market-status">
          <span class="status-dot" :class="marketStatus"></span>
          <span class="status-text">{{ marketStatusText }}</span>
        </div>
      </div>
      
      <div class="header-center">
        <div class="market-brief">
          <span class="index-name">上证指数</span>
          <span class="index-price" :class="getChangeClass(indexChange)">{{ indexPrice }}</span>
          <span class="index-change" :class="getChangeClass(indexChange)">{{ indexChange }}%</span>
        </div>
      </div>
      
      <div class="header-right">
        <div class="balance-info">
          <span class="balance-label">可用资金</span>
          <span class="balance-value">¥{{ formatNumber(userStore.balance.available) }}</span>
        </div>
        <el-dropdown @command="handleCommand">
          <div class="user-info">
            <el-avatar :size="32" :icon="UserFilled" />
            <span class="username">{{ userStore.userInfo?.username || '用户' }}</span>
            <el-icon><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="settings">系统设置</el-dropdown-item>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>
    
    <!-- 侧边栏 + 主内容区 -->
    <div class="layout-body">
      <!-- 侧边栏 -->
      <aside class="sidebar">
        <el-menu
          :default-active="$route.path"
          router
          class="sidebar-menu"
          background-color="transparent"
          text-color="var(--color-text-secondary)"
          active-text-color="var(--color-primary)"
        >
          <el-menu-item v-for="route in menuRoutes" :key="route.path" :index="route.path">
            <el-icon>
              <component :is="route.meta?.icon" />
            </el-icon>
            <span>{{ route.meta?.title }}</span>
          </el-menu-item>
        </el-menu>
      </aside>
      
      <!-- 主内容区 -->
      <main class="main-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, UserFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 菜单路由
const menuRoutes = computed(() => {
  const mainRoute = route.matched.find(r => r.path === '/')
  return mainRoute?.children?.filter(r => !r.meta?.hidden) || []
})

// 市场状态
const marketStatus = ref<'open' | 'close'>('open')
const marketStatusText = computed(() => marketStatus.value === 'open' ? '交易中' : '已收盘')

// 指数数据（模拟）
const indexPrice = ref('3050.32')
const indexChange = ref(0.85)

// 获取涨跌样式类
const getChangeClass = (change: number) => {
  if (change > 0) return 'text-up'
  if (change < 0) return 'text-down'
  return 'text-flat'
}

// 格式化数字
const formatNumber = (num: number) => {
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 处理用户菜单命令
const handleCommand = (command: string) => {
  switch (command) {
    case 'settings':
      router.push('/settings')
      break
    case 'password':
      // 打开修改密码弹窗
      break
    case 'logout':
      userStore.logout()
      router.push('/login')
      break
  }
}
</script>

<style scoped lang="scss">
.main-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--color-background);
}

.header {
  height: 60px;
  background-color: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 24px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.market-status {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  
  &.open {
    background-color: var(--color-success);
    box-shadow: 0 0 6px var(--color-success);
  }
  
  &.close {
    background-color: var(--color-text-muted);
  }
}

.status-text {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.header-center {
  .market-brief {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  
  .index-name {
    font-size: 14px;
    color: var(--color-text-secondary);
  }
  
  .index-price {
    font-size: 18px;
    font-weight: 600;
    font-family: 'Roboto Mono', monospace;
  }
  
  .index-change {
    font-size: 14px;
    font-family: 'Roboto Mono', monospace;
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 24px;
}

.balance-info {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  
  .balance-label {
    font-size: 12px;
    color: var(--color-text-muted);
  }
  
  .balance-value {
    font-size: 16px;
    font-weight: 600;
    color: var(--color-text-primary);
    font-family: 'Roboto Mono', monospace;
  }
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background-color 0.2s;
  
  &:hover {
    background-color: var(--color-surface-light);
  }
  
  .username {
    font-size: 14px;
    color: var(--color-text-primary);
  }
}

.layout-body {
  display: flex;
  flex: 1;
  margin-top: 60px;
}

.sidebar {
  width: 200px;
  background-color: var(--color-surface);
  border-right: 1px solid var(--color-border);
  position: fixed;
  top: 60px;
  left: 0;
  bottom: 0;
  overflow-y: auto;
}

.sidebar-menu {
  border-right: none;
  
  :deep(.el-menu-item) {
    height: 50px;
    line-height: 50px;
    
    &:hover {
      background-color: var(--color-surface-light) !important;
    }
    
    &.is-active {
      background-color: rgba(59, 130, 246, 0.1) !important;
    }
    
    .el-icon {
      font-size: 18px;
    }
  }
}

.main-content {
  flex: 1;
  margin-left: 200px;
  padding: 24px;
  background-color: var(--color-background);
  min-height: calc(100vh - 60px);
}
</style>
