<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  Grid as IconGrid,
  Aim as IconAim,
  Document as IconDocument,
  TrendCharts as IconTrend,
  Setting as IconSetting,
  Bell as IconBell,
  Moon as IconMoon,
  Sunny as IconSunny,
  User as IconUser
} from '@element-plus/icons-vue'
import axios from 'axios'
import config from './config/config'

const BASE_URL = config.baseUrl
const USER_ID = '6928fca14f206ada1b7a9269'

const route = useRoute()

const navItems = [
  { path: '/', label: '总览', icon: IconGrid },
  { path: '/decide', label: '决策台', icon: IconAim },
  { path: '/stock', label: '股票', icon: IconDocument },
  { path: '/market', label: '市场', icon: IconTrend },
  { path: '/config', label: '配置', icon: IconSetting }
]

const isActive = (path) => (path === '/' ? route.path === '/' : route.path.startsWith(path))
const pageTitle = computed(() => route.name || '总览')

/* 主题：跟着 localStorage 走，默认浅色 */
const isDarkMode = ref(localStorage.getItem('stockai-theme') === 'dark')
const applyTheme = (dark) => document.documentElement.classList.toggle('dark', dark)

const toggleDarkMode = () => {
  isDarkMode.value = !isDarkMode.value
  applyTheme(isDarkMode.value)
  localStorage.setItem('stockai-theme', isDarkMode.value ? 'dark' : 'light')
}

/* 顶栏时间：本地时钟，秒级 */
const now = ref(new Date())
let clockTimer = null

const clock = computed(() => {
  const d = now.value
  const p = (n) => String(n).padStart(2, '0')
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return {
    date: `${d.getMonth() + 1}月${d.getDate()}日 周${week}`,
    time: `${p(d.getHours())}:${p(d.getMinutes())}`
  }
})

/* 站内提醒 */
const notifications = ref([])
const showNotificationDrawer = ref(false)
const loadingNotifications = ref(false)
const unreadCount = computed(() => notifications.value.filter((n) => !n.read).length)

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
      const n = notifications.value.find((item) => item.id === id)
      if (n) n.read = true
    }
  } catch (error) {
    console.error('标记已读失败:', error)
  }
}

async function markAllRead() {
  const ids = notifications.value.filter((n) => !n.read).map((n) => n.id)
  if (ids.length === 0) return
  try {
    const response = await axios.post(`${BASE_URL}/notification/read`, { userId: USER_ID, ids })
    if (response.data && response.data.state === 0) {
      notifications.value.forEach((n) => {
        n.read = true
      })
    }
  } catch (error) {
    console.error('标记全部已读失败:', error)
  }
}

onMounted(() => {
  applyTheme(isDarkMode.value)
  fetchNotifications()
  clockTimer = setInterval(() => (now.value = new Date()), 30_000)
})

onUnmounted(() => clearInterval(clockTimer))
</script>

<template>
  <div class="shell" :class="{ dark: isDarkMode }">
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark" aria-hidden="true">
          <svg viewBox="0 0 16 16" width="16" height="16" fill="none">
            <path d="M1 11.5 5.6 6l3 3.2L15 2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
            <path d="M10.6 2H15v4.4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </span>
        <span class="brand-name">StockAI</span>
      </div>

      <div class="topbar-right">
        <div class="clock">
          <span class="clock-date">{{ clock.date }}</span>
          <span class="clock-time num">{{ clock.time }}</span>
        </div>

        <span class="divider" aria-hidden="true"></span>

        <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="badge">
          <button class="icon-btn" type="button" aria-label="站内提醒" @click="openNotifications">
            <el-icon><IconBell /></el-icon>
          </button>
        </el-badge>

        <button class="icon-btn" type="button" :aria-label="isDarkMode ? '切换到浅色' : '切换到深色'" @click="toggleDarkMode">
          <el-icon><IconSunny v-if="isDarkMode" /><IconMoon v-else /></el-icon>
        </button>

        <el-dropdown trigger="click">
          <button class="user-btn" type="button">
            <el-icon class="user-ico"><IconUser /></el-icon>
            <span>管理员</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item>个人中心</el-dropdown-item>
              <el-dropdown-item>设置</el-dropdown-item>
              <el-dropdown-item divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <div class="frame">
      <aside class="rail">
        <nav class="nav" aria-label="主导航">
          <router-link
            v-for="item in navItems"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ 'is-active': isActive(item.path) }"
          >
            <el-icon class="nav-ico"><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </router-link>
        </nav>

        <div class="rail-foot">
          <span class="rail-version">v1.0</span>
        </div>
      </aside>

      <main class="stage">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </main>
    </div>

    <el-drawer v-model="showNotificationDrawer" title="站内提醒" size="420px">
      <div v-loading="loadingNotifications" class="notification-list">
        <el-empty v-if="!loadingNotifications && notifications.length === 0" description="暂无提醒，到点会提醒你" />
        <div
          v-for="n in notifications"
          :key="n.id"
          class="notification-item"
          :class="{ 'notification-unread': !n.read }"
        >
          <div class="notification-head">
            <span class="notification-kind" :class="n.type === 'TAKE_PROFIT' ? 'is-profit' : 'is-loss'">
              {{ n.type === 'TAKE_PROFIT' ? '止盈' : '止损' }}
            </span>
            <span class="notification-stock">{{ n.stockName }}（{{ n.symbol }}）</span>
          </div>
          <div class="notification-content">{{ n.content }}</div>
          <div class="notification-foot">
            <span class="notification-time num">{{ n.createdAt }}</span>
            <el-button v-if="!n.read" link @click="markOneRead(n.id)">标记已读</el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" :disabled="unreadCount === 0" @click="markAllRead">全部已读</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.shell {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--canvas);
  color: var(--ink);
}

/* ---------- topbar ---------- */
.topbar {
  flex: none;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px 0 22px;
  border-bottom: 1px solid var(--rule);
  background: var(--canvas);
  -webkit-backdrop-filter: saturate(1.2);
  z-index: 10;
}

.brand {
  display: flex;
  align-items: center;
  gap: 9px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: var(--ink);
  color: var(--canvas);
}

.dark .brand-mark {
  background: var(--accent);
  color: #101315;
}

.brand-name {
  font-size: 15px;
  font-weight: 650;
  letter-spacing: 0.01em;
  color: var(--ink);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.clock {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-right: 8px;
  color: var(--ink-3);
  font-size: var(--t-12);
  letter-spacing: 0.02em;
}

.clock-time {
  color: var(--ink-2);
  font-size: var(--t-13);
}

.divider {
  width: 1px;
  height: 18px;
  background: var(--rule);
  margin: 0 6px;
}

.icon-btn {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: var(--radius-s);
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  transition: background-color var(--dur-1) var(--ease), color var(--dur-1) var(--ease);
}

.icon-btn:hover {
  background: color-mix(in srgb, var(--ink) 6%, transparent);
  color: var(--ink);
}

.icon-btn:active {
  transform: scale(0.94);
}

.badge :deep(.el-badge__content) {
  border: 2px solid var(--canvas);
  background: var(--danger);
  font-size: 10px;
  height: 16px;
  padding: 0 4px;
  line-height: 12px;
}

.user-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 10px 0 8px;
  border: 0;
  border-radius: var(--radius-s);
  background: transparent;
  color: var(--ink-2);
  font-size: var(--t-13);
  cursor: pointer;
  transition: background-color var(--dur-1) var(--ease);
}

.user-btn:hover {
  background: color-mix(in srgb, var(--ink) 6%, transparent);
}

.user-ico {
  color: var(--ink-3);
}

/* ---------- frame ---------- */
.frame {
  flex: 1;
  min-height: 0;
  display: flex;
}

.rail {
  flex: none;
  width: 204px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 18px 12px 14px;
  border-right: 1px solid var(--rule);
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 36px;
  padding: 0 10px 0 11px;
  border-radius: var(--radius-s);
  color: var(--ink-2);
  font-size: 13.5px;
  letter-spacing: 0.01em;
  transition: background-color var(--dur-1) var(--ease), color var(--dur-1) var(--ease);
}

.nav-ico {
  font-size: 15px;
  color: var(--ink-3);
  transition: color var(--dur-1) var(--ease);
}

.nav-item:hover {
  background: color-mix(in srgb, var(--ink) 5%, transparent);
  color: var(--ink);
}

.nav-item.is-active {
  background: var(--surface);
  color: var(--ink);
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(20, 23, 26, 0.05);
}

.dark .nav-item.is-active {
  background: var(--surface-raised);
}

.nav-item.is-active .nav-ico {
  color: var(--accent);
}

/* 当前项左侧那道 3px 记号，是这页唯一的彩色标记 */
.nav-item.is-active::before {
  content: "";
  position: absolute;
  left: -1px;
  top: 9px;
  bottom: 9px;
  width: 2px;
  border-radius: 2px;
  background: var(--accent);
}

.rail-foot {
  padding: 0 11px;
}

.rail-version {
  font-size: 11px;
  color: var(--ink-4);
  letter-spacing: 0.06em;
  font-variant-numeric: tabular-nums;
}

.stage {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

/* ---------- page transition ---------- */
.page-enter-active {
  transition: opacity 260ms var(--ease), transform 260ms var(--ease);
}

.page-leave-active {
  transition: opacity 120ms var(--ease);
}

.page-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.page-leave-to {
  opacity: 0;
}

/* ---------- drawer ---------- */
.notification-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-height: 120px;
}

.notification-item {
  padding: 14px 4px;
  border-bottom: 1px solid var(--rule);
}

.notification-item:last-child {
  border-bottom: 0;
}

.notification-unread .notification-stock {
  font-weight: 600;
}

.notification-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.notification-kind {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  letter-spacing: 0.04em;
}

.notification-kind.is-profit {
  color: var(--danger);
  background: var(--danger-wash);
}

.notification-kind.is-loss {
  color: var(--accent);
  background: var(--accent-wash);
}

.notification-stock {
  font-size: var(--t-13);
  color: var(--ink);
}

.notification-content {
  font-size: var(--t-13);
  color: var(--ink-2);
  line-height: 1.6;
}

.notification-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.notification-time {
  font-size: 11px;
  color: var(--ink-3);
}

@media (max-width: 900px) {
  .rail {
    width: 64px;
    padding: 14px 8px;
  }
  .nav-item span {
    display: none;
  }
  .nav-item {
    justify-content: center;
    padding: 0;
  }
  .clock {
    display: none;
  }
}

/* 700 以下：图标栏也太占宽，改成顶部横条，正文吃满整幅 */
@media (max-width: 700px) {
  .frame {
    flex-direction: column;
  }

  .rail {
    width: auto;
    flex-direction: row;
    justify-content: flex-start;
    padding: 8px 12px;
    border-right: 0;
    border-bottom: 1px solid var(--rule);
    overflow-x: auto;
    scrollbar-width: none;
  }

  .rail::-webkit-scrollbar {
    display: none;
  }

  .nav {
    flex-direction: row;
    gap: 4px;
  }

  .nav-item {
    justify-content: flex-start;
    gap: 7px;
    padding: 0 12px;
    white-space: nowrap;
  }

  .nav-item span {
    display: inline;
  }

  .nav-item.is-active::before {
    display: none;
  }

  .rail-foot {
    display: none;
  }

  .topbar {
    padding: 0 14px;
  }
}
</style>
