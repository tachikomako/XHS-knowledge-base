<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Collection, Connection, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { changeItemLifecycle, getItem, searchItems, updateItem } from './api/items'
import type { CaptureLevel, KnowledgeItem, LifecycleStatus } from './api/items'
import KnowledgeCard from './components/KnowledgeCard.vue'
import KnowledgeDetailDrawer from './components/KnowledgeDetailDrawer.vue'
import { useBackendHealth } from './composables/useBackendHealth'

const PAGE_SIZE = 12
const { health, loading: healthLoading, error: healthError, checkHealth } = useBackendHealth()

const items = ref<KnowledgeItem[]>([])
const total = ref(0)
const page = ref(1)
const queryInput = ref('')
const appliedQuery = ref('')
const lifecycleStatus = ref<LifecycleStatus>('ACTIVE')
const captureLevel = ref<CaptureLevel | ''>('')
const listLoading = ref(false)
const listError = ref('')
const drawerVisible = ref(false)
const detailLoading = ref(false)
const detailSaving = ref(false)
const selectedItem = ref<KnowledgeItem | null>(null)

let listController: AbortController | null = null
let detailController: AbortController | null = null

const libraryLabel = computed(() => ({
  ACTIVE: '知识库',
  ARCHIVED: '归档',
  TRASHED: '回收站',
})[lifecycleStatus.value])

const emptyDescription = computed(() => {
  if (appliedQuery.value) return `没有找到与“${appliedQuery.value}”相关的内容`
  if (lifecycleStatus.value === 'TRASHED') return '回收站是空的'
  if (lifecycleStatus.value === 'ARCHIVED') return '还没有归档内容'
  return '安装扩展并剪藏第一篇小红书帖子吧'
})

onMounted(() => {
  void checkHealth()
  void loadItems()
})

onBeforeUnmount(() => {
  listController?.abort()
  detailController?.abort()
})

watch([lifecycleStatus, captureLevel], () => {
  page.value = 1
  void loadItems()
})

async function loadItems() {
  listController?.abort()
  const controller = new AbortController()
  listController = controller
  listLoading.value = true
  listError.value = ''
  try {
    const result = await searchItems({
      q: appliedQuery.value,
      lifecycleStatus: lifecycleStatus.value,
      captureLevel: captureLevel.value,
      page: page.value,
      pageSize: PAGE_SIZE,
    }, controller.signal)
    items.value = result.items
    total.value = result.total
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') return
    listError.value = error instanceof Error ? error.message : '无法加载知识库'
  } finally {
    if (listController === controller) listLoading.value = false
  }
}

function applySearch() {
  appliedQuery.value = queryInput.value.trim()
  page.value = 1
  void loadItems()
}

function clearSearch() {
  queryInput.value = ''
  appliedQuery.value = ''
  page.value = 1
  void loadItems()
}

function changePage(nextPage: number) {
  page.value = nextPage
  void loadItems()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function openItem(item: KnowledgeItem) {
  selectedItem.value = item
  drawerVisible.value = true
  detailLoading.value = true
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  try {
    selectedItem.value = await getItem(item.id, controller.signal)
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') return
    ElMessage.error(error instanceof Error ? error.message : '无法读取详情')
  } finally {
    if (detailController === controller) detailLoading.value = false
  }
}

async function saveDetails(changes: { summary: string | null; userNote: string | null }) {
  if (!selectedItem.value) return
  detailSaving.value = true
  try {
    selectedItem.value = await updateItem(selectedItem.value.id, changes)
    replaceItem(selectedItem.value)
    ElMessage.success('知识整理已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    detailSaving.value = false
  }
}

async function changeLifecycle(action: 'archive' | 'trash' | 'restore') {
  if (!selectedItem.value) return
  const copy = {
    archive: ['归档这条内容？', '归档后可在“归档”中找到。'],
    trash: ['移入回收站？', '这不会取消小红书原帖收藏。'],
    restore: ['恢复这条内容？', '它将重新出现在知识库中。'],
  }[action]

  try {
    await ElMessageBox.confirm(copy[1], copy[0], {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: action === 'trash' ? 'warning' : 'info',
    })
    await changeItemLifecycle(selectedItem.value.id, action)
    drawerVisible.value = false
    ElMessage.success(action === 'restore' ? '已恢复' : action === 'archive' ? '已归档' : '已移入回收站')
    await loadItems()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '操作失败')
    }
  }
}

function replaceItem(updated: KnowledgeItem) {
  const index = items.value.findIndex((item) => item.id === updated.id)
  if (index >= 0) items.value[index] = updated
}
</script>

<template>
  <main class="page-shell">
    <header class="site-header">
      <div class="brand">
        <div class="brand-mark"><Collection /></div>
        <div><strong>拾笺</strong><span>XHS KNOWLEDGE</span></div>
      </div>
      <button class="health-pill" :class="{ online: health }" type="button" @click="checkHealth">
        <el-icon :class="{ spinning: healthLoading }"><Refresh v-if="healthLoading" /><Connection v-else /></el-icon>
        <span>{{ health ? '本地服务已连接' : healthError || '后端未连接' }}</span>
      </button>
    </header>

    <section class="library-heading">
      <div>
        <span class="eyebrow">PERSONAL LIBRARY</span>
        <h1>{{ libraryLabel }}</h1>
        <p>收藏不是终点。把值得留下的内容变成可以搜索、理解和继续补充的知识。</p>
      </div>
      <div class="total-counter"><strong>{{ total }}</strong><span>条内容</span></div>
    </section>

    <section class="toolbar" aria-label="知识库筛选">
      <form class="search-form" @submit.prevent="applySearch">
        <el-input v-model="queryInput" size="large" clearable placeholder="搜索标题、正文、摘要或笔记" :prefix-icon="Search" @clear="clearSearch" />
        <el-button native-type="submit" size="large" type="primary">搜索</el-button>
      </form>
      <div class="filter-row">
        <el-radio-group v-model="lifecycleStatus" size="large">
          <el-radio-button value="ACTIVE">知识库</el-radio-button>
          <el-radio-button value="ARCHIVED">归档</el-radio-button>
          <el-radio-button value="TRASHED">回收站</el-radio-button>
        </el-radio-group>
        <el-select v-model="captureLevel" size="large" aria-label="内容完整度" style="width: 150px">
          <el-option label="全部内容" value="" />
          <el-option label="正文快照" value="DETAIL" />
          <el-option label="链接卡片" value="CARD" />
        </el-select>
      </div>
    </section>

    <el-alert v-if="listError" :title="listError" type="error" show-icon :closable="false">
      <template #default><el-button text @click="loadItems">重新加载</el-button></template>
    </el-alert>

    <section v-loading="listLoading" class="library-grid" :class="{ empty: !items.length }" aria-live="polite">
      <KnowledgeCard v-for="item in items" :key="item.id" :item="item" @open="openItem" />
      <el-empty v-if="!listLoading && !items.length && !listError" :description="emptyDescription" />
    </section>

    <el-pagination
      v-if="total > PAGE_SIZE"
      class="pagination"
      background
      layout="prev, pager, next"
      :current-page="page"
      :page-size="PAGE_SIZE"
      :total="total"
      @current-change="changePage"
    />

    <KnowledgeDetailDrawer
      v-model="drawerVisible"
      :item="selectedItem"
      :loading="detailLoading"
      :saving="detailSaving"
      @save="saveDetails"
      @lifecycle="changeLifecycle"
    />
  </main>
</template>
