<script setup>
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import config from '../../config/config'

const props = defineProps({
  userId: {
    type: String,
    required: true
  }
})

const BASE_URL = config.baseUrl
const expanded = ref(false)
const loading = ref(false)
const generating = ref(false)
const latest = ref(null)
const historyList = ref([])
const dialogVisible = ref(false)
const selected = ref(null)
const detailLoading = ref(false)

const hasReport = computed(() => Boolean(latest.value && latest.value.content))
const generatedAt = computed(() => latest.value?.generatedAt || '—')
const statusTag = computed(() => (latest.value?.status === 'fallback' ? '降级' : 'AI 生成'))

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function renderMarkdown(markdown) {
  if (!markdown) {
    return ''
  }
  const lines = escapeHtml(markdown).split(/\r?\n/)
  let html = ''
  let inList = false
  const closeList = () => {
    if (inList) {
      html += '</ul>'
      inList = false
    }
  }
  for (const line of lines) {
    const text = line.trim()
    if (!text) {
      closeList()
      continue
    }
    if (/^####\s+/.test(text)) {
      closeList()
      html += `<h5>${text.replace(/^####\s+/, '')}</h5>`
      continue
    }
    if (/^###\s+/.test(text)) {
      closeList()
      html += `<h4>${text.replace(/^###\s+/, '')}</h4>`
      continue
    }
    if (/^##\s+/.test(text)) {
      closeList()
      html += `<h3>${text.replace(/^##\s+/, '')}</h3>`
      continue
    }
    if (/^#\s+/.test(text)) {
      closeList()
      html += `<h2>${text.replace(/^#\s+/, '')}</h2>`
      continue
    }
    if (/^-\s+/.test(text)) {
      if (!inList) {
        html += '<ul>'
        inList = true
      }
      html += `<li>${text.replace(/^-\s+/, '')}</li>`
      continue
    }
    closeList()
    html += `<p>${text}</p>`
  }
  closeList()
  return html
}

async function fetchLatest() {
  loading.value = true
  try {
    const response = await axios.post(`${BASE_URL}/trade-plan/summary/latest`, { userId: props.userId })
    if (response.data.state === 0) {
      latest.value = response.data.data
    } else {
      latest.value = null
    }
  } catch (error) {
    console.error('获取最新周报失败', error)
    latest.value = null
  } finally {
    loading.value = false
  }
}

async function fetchHistory() {
  try {
    const response = await axios.post(`${BASE_URL}/trade-plan/summary/list`, { userId: props.userId })
    historyList.value = response.data.state === 0 ? response.data.data || [] : []
  } catch (error) {
    console.error('获取周报历史失败', error)
    historyList.value = []
  }
}

async function openHistory() {
  dialogVisible.value = true
  await fetchHistory()
  if (historyList.value.length > 0) {
    await selectWeek(historyList.value[0].weekStart)
  } else {
    selected.value = null
  }
}

async function selectWeek(weekStart) {
  detailLoading.value = true
  try {
    const response = await axios.post(`${BASE_URL}/trade-plan/summary/detail`, {
      userId: props.userId,
      weekStart
    })
    selected.value = response.data.state === 0 ? response.data.data : null
  } catch (error) {
    console.error('获取周报详情失败', error)
    selected.value = null
  } finally {
    detailLoading.value = false
  }
}

async function generateNow() {
  generating.value = true
  try {
    const response = await axios.post(`${BASE_URL}/trade-plan/summary/generate`, { userId: props.userId })
    if (response.data.state === 0) {
      latest.value = response.data.data
      expanded.value = true
      ElMessage.success('本期周报已生成')
    } else {
      ElMessage.error(response.data.message || '周报生成失败')
    }
  } catch (error) {
    console.error('生成周报失败', error)
    ElMessage.error('生成周报失败，请稍后重试')
  } finally {
    generating.value = false
  }
}

onMounted(fetchLatest)
</script>

<template>
  <el-card shadow="hover" class="weekly-card" v-loading="loading">
    <template #header>
      <div class="weekly-header">
        <div class="weekly-title">
          <span class="weekly-name">本周周报</span>
          <el-tag v-if="hasReport" size="small" :type="statusTag === '降级' ? 'warning' : 'success'">
            {{ statusTag }}
          </el-tag>
          <span class="weekly-time">生成时间：{{ generatedAt }}</span>
        </div>
        <div class="weekly-actions">
          <el-button v-if="hasReport" size="small" @click="expanded = !expanded">
            {{ expanded ? '收起' : '展开' }}
          </el-button>
          <el-button size="small" @click="openHistory">历史</el-button>
          <el-button v-if="!hasReport" size="small" type="primary" :loading="generating" @click="generateNow">
            立即生成
          </el-button>
        </div>
      </div>
    </template>

    <el-empty v-if="!hasReport" description="暂无周报，每周六 08:00 自动生成" :image-size="80" />

    <div v-else class="weekly-body" :class="{ collapsed: !expanded }">
      <div class="markdown-body" v-html="renderMarkdown(latest.content)"></div>
    </div>

    <el-dialog v-model="dialogVisible" title="历史周报" width="900px" append-to-body>
      <div class="history-layout">
        <div class="history-list">
          <div
            v-for="item in historyList"
            :key="item.weekStart"
            class="history-item"
            :class="{ active: selected && selected.weekStart === item.weekStart }"
            @click="selectWeek(item.weekStart)"
          >
            <div class="history-week">{{ item.weekStart }} ~ {{ item.weekEnd }}</div>
            <div class="history-meta">
              <el-tag size="small" :type="item.status === 'fallback' ? 'warning' : 'success'">
                {{ item.status === 'fallback' ? '降级' : 'AI 生成' }}
              </el-tag>
              <span>{{ item.generatedAt }}</span>
            </div>
          </div>
          <el-empty v-if="historyList.length === 0" description="暂无历史周报" :image-size="60" />
        </div>
        <div class="history-content" v-loading="detailLoading">
          <div v-if="selected" class="markdown-body" v-html="renderMarkdown(selected.content)"></div>
          <el-empty v-else description="请选择左侧周报" :image-size="60" />
        </div>
      </div>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.weekly-card {
  margin-bottom: 16px;
}

.weekly-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.weekly-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.weekly-name {
  font-size: 16px;
  font-weight: 600;
}

.weekly-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.weekly-actions {
  display: flex;
  gap: 8px;
}

.weekly-body {
  max-height: none;
}

.weekly-body.collapsed {
  max-height: 240px;
  overflow: hidden;
  position: relative;
}

.weekly-body.collapsed::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 48px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0) 0%, var(--el-bg-color) 90%);
}

.markdown-body {
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-primary);
}

.markdown-body :deep(h2) {
  font-size: 18px;
  margin: 8px 0 12px;
}

.markdown-body :deep(h3) {
  font-size: 15px;
  margin: 16px 0 8px;
}

.markdown-body :deep(h4),
.markdown-body :deep(h5) {
  font-size: 13px;
  margin: 10px 0 6px;
}

.markdown-body :deep(ul) {
  padding-left: 18px;
  margin: 4px 0;
}

.markdown-body :deep(p) {
  margin: 4px 0;
}

.history-layout {
  display: flex;
  gap: 16px;
  min-height: 420px;
}

.history-list {
  width: 260px;
  border-right: 1px solid var(--el-border-color-lighter);
  padding-right: 12px;
  overflow-y: auto;
  max-height: 520px;
}

.history-item {
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 6px;
}

.history-item:hover {
  background: var(--el-fill-color-light);
}

.history-item.active {
  background: var(--el-color-primary-light-9);
}

.history-week {
  font-size: 13px;
  font-weight: 500;
}

.history-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.history-content {
  flex: 1;
  overflow-y: auto;
  max-height: 520px;
}
</style>
