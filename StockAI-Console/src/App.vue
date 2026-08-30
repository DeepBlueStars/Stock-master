<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Menu as MenuIcon, Document, Setting, Bell, User } from '@element-plus/icons-vue'
import axios from 'axios'
import config from './config/config'

const BASE_URL = config.baseUrl
const USER_ID = '6928fca14f206ada1b7a9269'

const router = useRouter()
const route = useRoute()
const isDarkMode = ref(false)

// 站内提醒
const notifications = ref([])
const showNotificationDrawer = ref(false)
const loadingNotifications = ref(false)
const unreadCount = computed(() => notifications.value.filter(n => !n.read).length)

// 菜单选择事件
const handleMenuSelect = (index) => {
  router.push(index)
}

// 切换深色模式
const toggleDarkMode = () => {
  isDarkMode.value = !isDarkMode.value
  document.documentElement.classList.toggle('dark', isDarkMode.value)
}

async function fetchNotifications() {
  loadingNotifications.value = true
  try {
    const response = await axios.post(`${BASE_URL}/notification/list`, { userId: USER_ID })
    if (response.data && response.data.state === 0 && Array.isArray(response.data.data)) {
      notifications.value = response.data.data
    }
  } catch (error) {
    console.error('获取站内提醒失败:', error)
  } finally {
    loadingNotifications.value = false
  }
}

function openNotifications() {
  showNotificationDrawer.value = true
  fetchNotifications()
}

async function markOneRead(id) {
  try {
    const response = await axios.post(`${BASE_URL}/notification/read`, { userId: USER_ID, ids: [id] })
    if (response.data && response.data.state === 0) {
      const n = notifications.value.find(item => item.id === id)
      if (n) n.read = true
    }
  } catch (error) {
    console.error('标记已读失败:', error)
  }
}

async function markAllRead() {
  const ids = notifications.value.filter(n => !n.read).map(n => n.id)
  if (ids.length === 0) return
  try {
    const response = await axios.post(`${BASE_URL}/notification/read`, { userId: USER_ID, ids })
    if (response.data && response.data.state === 0) {
      notifications.value.forEach(n => { n.read = true })
    }
  } catch (error) {
    console.error('标记全部已读失败:', error)
  }
}

onMounted(fetchNotifications)
</script>

<template>
  <div class="common-layout" :class="{ 'dark': isDarkMode }">
    <!-- 顶部导航栏 -->
    <el-header class="app-header">
      <div class="header-left">
        <h1 class="app-title">StockAI</h1>
      </div>
      <div class="header-right">
        <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
          <span class="el-dropdown-link header-action" @click="openNotifications">
            <el-icon><Bell /></el-icon>
          </span>
        </el-badge>
        <el-button type="text" class="header-action" @click="toggleDarkMode">
          <el-icon>{{ isDarkMode ? 'Sunny' : 'Moon' }}</el-icon>
        </el-button>
        <el-dropdown>
          <span class="el-dropdown-link header-action user-profile">
            <el-icon><User /></el-icon>
            <span class="user-name">管理员</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item>个人中心</el-dropdown-item>
              <el-dropdown-item>设置</el-dropdown-item>
              <el-dropdown-item divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    
    <el-container class="main-container">
      <!-- 左侧菜单 -->
      <el-aside width="260px" class="app-sidebar">
        <el-menu
          :default-active="route.path"
          class="sidebar-menu"
          @select="handleMenuSelect"
          background-color="transparent"
          text-color="var(--el-text-color-primary)"
          active-text-color="#409eff"
        >
          <el-menu-item index="/">
            <el-icon><MenuIcon /></el-icon>
            <span>总览</span>
          </el-menu-item>
          <el-menu-item index="/stock">
            <el-icon><Document /></el-icon>
            <span>股票</span>
          </el-menu-item>
          <el-menu-item index="/config">
            <el-icon><Setting /></el-icon>
            <span>配置</span>
          </el-menu-item>
          <el-menu-item index="/regime">
            <el-icon><Setting /></el-icon>
            <span>温度计</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      
      <!-- 主内容区 -->
      <el-main class="app-content">
        <router-view />
      </el-main>
    </el-container>

    <!-- 站内提醒抽屉 -->
    <el-drawer v-model="showNotificationDrawer" title="站内提醒" size="420px">
      <div v-loading="loadingNotifications" class="notification-list">
        <el-empty v-if="!loadingNotifications && notifications.length === 0" description="暂无提醒" />
        <div
          v-for="n in notifications"
          :key="n.id"
          class="notification-item"
          :class="{ 'notification-unread': !n.read }"
        >
          <div class="notification-head">
            <el-tag :type="n.type === 'TAKE_PROFIT' ? 'success' : 'danger'" size="small">
              {{ n.type === 'TAKE_PROFIT' ? '止盈' : '止损' }}
            </el-tag>
            <span class="notification-stock">{{ n.stockName }}（{{ n.symbol }}）</span>
          </div>
          <div class="notification-content">{{ n.content }}</div>
          <div class="notification-foot">
            <span class="notification-time">{{ n.createdAt }}</span>
            <el-button v-if="!n.read" type="primary" link size="small" @click="markOneRead(n.id)">
              标记已读
            </el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="markAllRead" :disabled="unreadCount === 0">全部已读</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.common-layout {
  height: 100vh;
  background-color: #f8fafc;
  overflow: hidden;
  transition: all 0.3s ease;
}

.common-layout.dark {
  background-color: #0f172a;
}

/* 顶部导航栏 */
.app-header {
  background-color: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 2rem;
  height: 64px;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06);
  transition: all 0.3s ease;
}

.dark .app-header {
  background-color: #1e293b;
  border-bottom: 1px solid #334155;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.3), 0 1px 2px 0 rgba(0, 0, 0, 0.2);
}

.header-left {
  display: flex;
  align-items: center;
}

.app-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.header-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  color: #64748b;
  transition: all 0.2s ease;
  cursor: pointer;
}

.header-action:hover {
  background-color: #f1f5f9;
  color: #334155;
}

.dark .header-action {
  color: #cbd5e1;
}

.dark .header-action:hover {
  background-color: #334155;
  color: #f1f5f9;
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: auto;
  color: #334155;
}

.dark .user-profile {
  color: #f1f5f9;
}

.user-name {
  font-size: 0.875rem;
  font-weight: 500;
}

/* 主容器 */
.main-container {
  height: calc(100vh - 64px);
  overflow: hidden;
}

/* 左侧菜单 */
.app-sidebar {
  background-color: #ffffff;
  border-right: 1px solid #e2e8f0;
  box-shadow: 1px 0 3px 0 rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
}

.dark .app-sidebar {
  background-color: #1e293b;
  border-right: 1px solid #334155;
  box-shadow: 1px 0 3px 0 rgba(0, 0, 0, 0.3);
}

.sidebar-menu {
  height: 100%;
  border-right: none;
  background-color: transparent;
  padding: 1rem 0;
}

.sidebar-menu .el-menu-item {
  height: 50px;
  line-height: 50px;
  margin: 0 0.5rem;
  border-radius: 8px;
  margin-bottom: 0.25rem;
  transition: all 0.2s ease;
  font-size: 0.9375rem;
}

.sidebar-menu .el-menu-item:hover {
  background-color: #f1f5f9;
}

.dark .sidebar-menu .el-menu-item:hover {
  background-color: #334155;
}

.sidebar-menu .el-menu-item.is-active {
  background-color: #eff6ff;
  color: #3b82f6;
  font-weight: 500;
}

.dark .sidebar-menu .el-menu-item.is-active {
  background-color: #1e3a8a;
  color: #60a5fa;
}

.sidebar-menu .el-menu-item .el-icon {
  margin-right: 0.75rem;
  font-size: 1.125rem;
}

/* 主内容区 */
.app-content {
  padding: 2rem;
  background-color: #f8fafc;
  height: 100%;
  overflow-x: hidden;
  overflow-y: auto;
  box-sizing: border-box;
  transition: all 0.3s ease;
}

.dark .app-content {
  background-color: #0f172a;
}

/* 卡片样式 */
.app-content .el-card {
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06);
  transition: all 0.3s ease;
  margin-bottom: 1.5rem;
}

.dark .app-content .el-card {
  border: 1px solid #334155;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.3), 0 1px 2px 0 rgba(0, 0, 0, 0.2);
  background-color: #1e293b;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .app-sidebar {
    width: 240px;
  }
  
  .app-content {
    padding: 1.5rem;
  }
}

@media (max-width: 768px) {
  .app-header {
    padding: 0 1rem;
  }
  
  .app-title {
    font-size: 1.25rem;
  }
  
  .header-right {
    gap: 0.5rem;
  }
  
  .user-name {
    display: none;
  }
  
  .app-sidebar {
    width: 200px;
  }
  
  .app-content {
    padding: 1rem;
  }
}

@media (max-width: 480px) {
  .app-header {
    padding: 0 0.75rem;
  }
  
  .app-title {
    font-size: 1.125rem;
  }
  
  .app-content {
    padding: 0.75rem;
  }
}

.notification-list {
  min-height: 200px;
}
.notification-item {
  padding: 12px;
  border-bottom: 1px solid #e5e7eb;
  border-radius: 6px;
}
.notification-unread {
  background-color: #eff6ff;
}
.notification-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.notification-stock {
  font-weight: 600;
  color: #1f2937;
}
.notification-content {
  font-size: 13px;
  color: #374151;
  margin-bottom: 6px;
}
.notification-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.notification-time {
  font-size: 12px;
  color: #9ca3af;
}
</style>
