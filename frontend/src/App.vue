<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { CircleClose, Collection, Connection, Refresh, Search, Setting } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelAiTask, clearItems, deleteItem, getAiTask, getItem, organizeItem, organizePendingAi, organizeSelectedAi, searchItems, updateItem } from './api/items'
import type { AiOrganizeTask, KnowledgeItem } from './api/items'
import { aiSettingsButtonText, canStartAiAction } from './aiSettingsUi'
import { aiTaskProgressText, isAiActionDisabled, isAiActionLoading } from './aiTaskUi'
import type { AiAction } from './aiTaskUi'
import {
  confirmCategorySuggestions,
  createCategory,
  createTag,
  deleteCategory,
  deleteTag,
  fetchCategories,
  fetchSourceTags,
  fetchTags,
  generateCategorySuggestions,
  mergeTag as mergeTagApi,
  updateCategory,
  updateTag,
} from './api/metadata'
import type { Category, CategoryInput, CategorySuggestion, SourceTag, Tag } from './api/metadata'
import { clearAiCredentials, fetchLatestSyncRun, fetchSettings, testAiConnection, updateAiSettings } from './api/settings'
import type { AiSettingsUpdate } from './api/settings'
import type { SettingsResponse, SyncRunResponse } from './api/settings'
import KnowledgeCard from './components/KnowledgeCard.vue'
import KnowledgeDetailDrawer from './components/KnowledgeDetailDrawer.vue'
import SettingsDialog from './components/SettingsDialog.vue'
import TaxonomyDialog from './components/TaxonomyDialog.vue'
import { useBackendHealth } from './composables/useBackendHealth'

const PAGE_SIZE = 12
const { health, loading: healthLoading, error: healthError, checkHealth } = useBackendHealth()

const items = ref<KnowledgeItem[]>([])
const total = ref(0)
const page = ref(1)
const queryInput = ref('')
const appliedQuery = ref('')
const categoryId = ref('')
const tagId = ref('')
const sourceScope = ref<'ALL' | 'FAVORITE'>('ALL')
const listLoading = ref(false)
const listError = ref('')
const drawerVisible = ref(false)
const detailLoading = ref(false)
const detailSaving = ref(false)
const selectedItem = ref<KnowledgeItem | null>(null)
const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const sourceTags = ref<SourceTag[]>([])
const categorySuggestions = ref<CategorySuggestion[]>([])
const taxonomyVisible = ref(false)
const taxonomyLoading = ref(false)
const taxonomySuggesting = ref(false)
const settingsVisible = ref(false)
const settingsLoading = ref(false)
const settingsSaving = ref(false)
const aiTesting = ref(false)
const activeAiAction = ref<AiAction>(null)
const aiCancelling = ref(false)
const aiTask = ref<AiOrganizeTask | null>(null)
const selectedIds = ref<string[]>([])
const settings = ref<SettingsResponse | null>(null)
const latestSyncRun = ref<SyncRunResponse | null>(null)

let listController: AbortController | null = null
let detailController: AbortController | null = null
let metadataController: AbortController | null = null
let settingsController: AbortController | null = null
let aiPollController: AbortController | null = null
let aiPollTimer: number | null = null

const tagNames = computed(() => Object.fromEntries(tags.value.map((tag) => [tag.id, tag.name])))
const orderedCategories = computed(() => {
  const roots = categories.value.filter((category) => !category.parentId)
  const ordered = roots.flatMap((root) => [
    root,
    ...categories.value.filter((category) => category.parentId === root.id),
  ])
  return [...ordered, ...categories.value.filter((category) => !ordered.some((entry) => entry.id === category.id))]
})
const categoryNames = computed(() => Object.fromEntries(categories.value.map((category) => [
  category.id,
  category.parentId
    ? `${categories.value.find((parent) => parent.id === category.parentId)?.name || ''} / ${category.name}`
    : category.name,
])))
const categoryTree = computed(() => orderedCategories.value
  .filter((category) => !category.parentId)
  .map((category) => ({
    ...category,
    children: orderedCategories.value.filter((child) => child.parentId === category.id),
  })))

const emptyDescription = computed(() => {
  if (appliedQuery.value) return `没有找到与“${appliedQuery.value}”相关的内容`
  return '安装扩展并剪藏第一篇小红书帖子吧'
})

onMounted(() => {
  void checkHealth()
  void loadItems()
  void loadMetadata()
  void loadSettings()
})

onBeforeUnmount(() => {
  listController?.abort()
  detailController?.abort()
  metadataController?.abort()
  settingsController?.abort()
  aiPollController?.abort()
  if (aiPollTimer !== null) window.clearTimeout(aiPollTimer)
})

watch([categoryId, tagId, sourceScope], () => {
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
      categoryId: categoryId.value,
      tagId: tagId.value,
      sourceScope: sourceScope.value,
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

async function loadMetadata() {
  metadataController?.abort()
  const controller = new AbortController()
  metadataController = controller
  taxonomyLoading.value = true
  try {
    const [categoryResult, tagResult, sourceTagResult] = await Promise.all([
      fetchCategories(controller.signal),
      fetchTags(controller.signal),
      fetchSourceTags(controller.signal),
    ])
    categories.value = categoryResult
    tags.value = tagResult
    sourceTags.value = sourceTagResult
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') return
    ElMessage.error(error instanceof Error ? error.message : '无法加载分类与标签')
  } finally {
    if (metadataController === controller) taxonomyLoading.value = false
  }
}

async function loadSettings() {
  settingsController?.abort()
  const controller = new AbortController()
  settingsController = controller
  settingsLoading.value = true
  try {
    const [settingsResult, syncRunResult] = await Promise.all([
      fetchSettings(controller.signal),
      fetchLatestSyncRun(controller.signal),
    ])
    settings.value = settingsResult
    latestSyncRun.value = syncRunResult
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') return
    ElMessage.error(error instanceof Error ? error.message : '无法加载设置')
  } finally {
    if (settingsController === controller) settingsLoading.value = false
  }
}

async function saveAiSettings(input: AiSettingsUpdate) {
  settingsSaving.value = true
  try {
    settings.value = await updateAiSettings(input)
    try {
      const result = await testAiConnection()
      if (result.success) {
        ElMessage.success(`Qwen 连接成功：${result.model}`)
      } else {
        ElMessage.warning(result.message)
      }
    } finally {
      await loadSettings()
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存设置失败')
  } finally {
    settingsSaving.value = false
  }
}

async function testAi() {
  aiTesting.value = true
  try {
    const result = await testAiConnection()
    if (result.success) {
      await loadSettings()
      ElMessage.success(`Qwen 连接成功：${result.model}`)
    } else {
      ElMessage.warning(result.message)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '测试 Qwen 连接失败')
  } finally {
    aiTesting.value = false
  }
}

async function clearAiKey() {
  try {
    await ElMessageBox.confirm('清除后需要重新填写 API Key 才能使用 Qwen。', '清除 API Key？', {
      confirmButtonText: '清除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger',
    })
    settingsSaving.value = true
    settings.value = await clearAiCredentials()
    await loadSettings()
    ElMessage.success('API Key 已清除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '清除 API Key 失败')
    }
  } finally {
    settingsSaving.value = false
  }
}

function openAiSettings() {
  settingsVisible.value = true
  void loadSettings()
}

async function ensureAiConfigured() {
  if (!settings.value) await loadSettings()
  if (canStartAiAction(settings.value)) return true
  try {
    await ElMessageBox.confirm('尚未配置 Qwen API，请先完成 AI 设置', '需要 AI 设置', {
      confirmButtonText: '前往 AI 设置',
      cancelButtonText: '取消',
      type: 'warning',
    })
    openAiSettings()
  } catch {
    // User cancelled the prompt; no AI task should be created.
  }
  return false
}

async function organizePending() {
  if (activeAiAction.value) return
  if (!await ensureAiConfigured()) return
  activeAiAction.value = 'ALL_PENDING'
  try {
    await watchAiTask(await organizePendingAi())
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '整理待处理内容失败')
    activeAiAction.value = null
  }
}

async function organizeSelected() {
  if (activeAiAction.value) return
  if (!await ensureAiConfigured()) return
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先选择帖子')
    return
  }
  activeAiAction.value = 'SELECTED'
  try {
    await watchAiTask(await organizeSelectedAi(selectedIds.value))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI 分类失败')
    activeAiAction.value = null
  }
}

async function watchAiTask(task: AiOrganizeTask) {
  aiTask.value = task
  if (task.status === 'REJECTED' || !task.id) {
    ElMessage.warning(task.message || '没有可分类内容')
    activeAiAction.value = null
    return
  }
  ElMessage.success('AI 分类任务已创建')
  pollAiTask(task.id)
}

async function cancelActiveAiTask() {
  if (!aiTask.value?.id || !activeAiAction.value) return
  aiCancelling.value = true
  try {
    aiPollController?.abort()
    if (aiPollTimer !== null) window.clearTimeout(aiPollTimer)
    aiTask.value = await cancelAiTask(aiTask.value.id)
    activeAiAction.value = null
    selectedIds.value = []
    await Promise.all([loadItems(), loadMetadata(), loadSettings()])
    ElMessage.warning(aiTask.value.message || 'AI 分类已中断')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '中断 AI 分类失败')
  } finally {
    aiCancelling.value = false
  }
}

function pollAiTask(id: string) {
  if (aiPollTimer !== null) window.clearTimeout(aiPollTimer)
  aiPollTimer = window.setTimeout(async () => {
    aiPollController?.abort()
    const controller = new AbortController()
    const timeout = window.setTimeout(() => controller.abort(), 10000)
    aiPollController = controller
    try {
      const task = await getAiTask(id, controller.signal)
      aiTask.value = task
      if (['COMPLETED', 'COMPLETED_WITH_ERRORS', 'REJECTED', 'CANCELLED'].includes(task.status)) {
        activeAiAction.value = null
        selectedIds.value = []
        await Promise.all([loadItems(), loadMetadata(), loadSettings()])
        ElMessage[task.failed > 0 || task.status === 'CANCELLED' ? 'warning' : 'success'](task.message || 'AI 分类完成')
        return
      }
      pollAiTask(id)
    } catch (error) {
      if (aiCancelling.value) return
      activeAiAction.value = null
      ElMessage.error(error instanceof Error ? error.message : '读取 AI 任务进度失败')
    } finally {
      window.clearTimeout(timeout)
      if (aiPollController === controller) aiPollController = null
    }
  }, 1200)
}

function toggleSelect(item: KnowledgeItem) {
  selectedIds.value = selectedIds.value.includes(item.id)
    ? selectedIds.value.filter((id) => id !== item.id)
    : [...selectedIds.value, item.id]
}

function filterByTag(selectedTagId: string) {
  tagId.value = selectedTagId
}

function selectCategory(selectedCategoryId: string) {
  categoryId.value = selectedCategoryId
}

function selectSource(nextSourceScope: 'ALL' | 'FAVORITE') {
  sourceScope.value = nextSourceScope
  categoryId.value = ''
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

async function saveDetails(changes: {
  summary: string | null
  userNote: string | null
  categoryId: string | null
  tagIds: string[]
}) {
  if (!selectedItem.value) return
  detailSaving.value = true
  try {
    selectedItem.value = await updateItem(selectedItem.value.id, changes)
    replaceItem(selectedItem.value)
    ElMessage.success('知识整理已保存')
    await Promise.all([loadMetadata(), loadItems()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    detailSaving.value = false
  }
}

async function organizeSelectedItem() {
  if (activeAiAction.value) return
  if (!selectedItem.value) return
  if (!await ensureAiConfigured()) return
  activeAiAction.value = 'CURRENT'
  try {
    selectedItem.value = await organizeItem(selectedItem.value.id)
    replaceItem(selectedItem.value)
    ElMessage.success('AI 整理已完成')
    await Promise.all([loadMetadata(), loadItems(), loadSettings()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI 整理失败')
  } finally {
    activeAiAction.value = null
  }
}

async function addCategory(input: CategoryInput) {
  await mutateMetadata(() => createCategory(input), '分类已添加')
}

async function generateSuggestions() {
  taxonomySuggesting.value = true
  try {
    const result = await generateCategorySuggestions()
    categorySuggestions.value = result.suggestions
    sourceTags.value = result.sourceTags
    ElMessage.success(`已生成 ${result.suggestions.length} 个分类建议`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '生成分类建议失败')
  } finally {
    taxonomySuggesting.value = false
  }
}

async function confirmSuggestions(suggestions: CategorySuggestion[]) {
  const updated = await mutateMetadata(() => confirmCategorySuggestions(suggestions), '分类建议已创建')
  if (updated) categorySuggestions.value = []
}

async function addTag(name: string) {
  await mutateMetadata(() => createTag(name), '标签已添加')
}

async function editCategory(category: Category) {
  try {
    const result = await ElMessageBox.prompt('请输入新的分类名称', '重命名分类', {
      inputValue: category.name,
      inputPattern: /\S/u,
      inputErrorMessage: '分类名称不能为空',
      confirmButtonText: '保存',
      cancelButtonText: '取消',
    })
    await mutateMetadata(() => updateCategory(category.id, {
      name: result.value,
      parentId: category.parentId,
      sortOrder: category.sortOrder,
    }), '分类已更新')
  } catch (error) {
    handleDialogError(error)
  }
}

async function moveCategory(category: Category, direction: -1 | 1) {
  const siblings = orderedCategories.value.filter((candidate) => candidate.parentId === category.parentId)
  const index = siblings.findIndex((candidate) => candidate.id === category.id)
  const target = siblings[index + direction]
  if (!target) return
  const reordered = [...siblings]
  reordered.splice(index, 1)
  reordered.splice(index + direction, 0, category)
  await mutateMetadata(() => Promise.all(reordered.map((entry, nextIndex) => updateCategory(entry.id, {
    name: entry.name,
    parentId: entry.parentId,
    sortOrder: nextIndex * 10,
  }))), '分类排序已更新')
}

async function editTag(tag: Tag) {
  try {
    const result = await ElMessageBox.prompt('请输入新的标签名称', '重命名标签', {
      inputValue: tag.name,
      inputPattern: /\S/u,
      inputErrorMessage: '标签名称不能为空',
      confirmButtonText: '保存',
      cancelButtonText: '取消',
    })
    await mutateMetadata(() => updateTag(tag.id, result.value), '标签已更新')
  } catch (error) {
    handleDialogError(error)
  }
}

async function mergeTag(sourceTag: Tag, targetTagId: string) {
  const targetTag = tags.value.find((tag) => tag.id === targetTagId)
  if (!targetTag) return
  try {
    await ElMessageBox.confirm(`这会把 #${sourceTag.name} 的帖子关联合并到 #${targetTag.name}，并删除源标签。`, '合并标签？', {
      confirmButtonText: '合并',
      cancelButtonText: '取消',
      type: 'warning',
    })
    const merged = await mutateMetadata(() => mergeTagApi(sourceTag.id, targetTagId), '标签已合并')
    if (merged && tagId.value === sourceTag.id) tagId.value = targetTagId
  } catch (error) {
    handleDialogError(error)
  }
}

async function removeCategory(category: Category) {
  try {
    await ElMessageBox.confirm('只有没有子分类和关联帖子的分类才能删除。', `删除“${category.name}”？`, {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    const deleted = await mutateMetadata(() => deleteCategory(category.id), '分类已删除')
    if (deleted && categoryId.value === category.id) categoryId.value = ''
  } catch (error) {
    handleDialogError(error)
  }
}

async function removeTag(tag: Tag) {
  try {
    await ElMessageBox.confirm('删除标签会解除帖子关联，但不会删除帖子。', `删除 #${tag.name}？`, {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    const deleted = await mutateMetadata(() => deleteTag(tag.id), '标签已删除')
    if (deleted && tagId.value === tag.id) tagId.value = ''
  } catch (error) {
    handleDialogError(error)
  }
}

async function mutateMetadata(operation: () => Promise<unknown>, successMessage: string) {
  taxonomyLoading.value = true
  try {
    await operation()
    ElMessage.success(successMessage)
    await Promise.all([loadMetadata(), loadItems()])
    return true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
    return false
  } finally {
    taxonomyLoading.value = false
  }
}

function handleDialogError(error: unknown) {
  if (error !== 'cancel' && error !== 'close') {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

async function deleteSelectedItem() {
  if (!selectedItem.value) return
  try {
    await ElMessageBox.confirm('删除后数据库中会直接移除；如果它仍在小红书收藏中，下次手动同步可重新创建。', '删除这条内容？', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteItem(selectedItem.value.id)
    drawerVisible.value = false
    selectedItem.value = null
    ElMessage.success('已删除')
    await Promise.all([loadItems(), loadMetadata()])
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '操作失败')
    }
  }
}

async function clearLibrary() {
  try {
    const result = await ElMessageBox.prompt('此操作会物理删除所有知识库内容，并清除内容关联、来源关联和 AI 建议。分类、标签、设置和同步记录会保留。请输入“清空知识库”继续。', '清空知识库？', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning',
      inputPattern: /^清空知识库$/u,
      inputErrorMessage: '请输入 清空知识库',
      confirmButtonClass: 'el-button--danger',
    })
    const response = await clearItems(result.value)
    drawerVisible.value = false
    selectedItem.value = null
    page.value = 1
    ElMessage.success(`已清空 ${response.deletedItems} 条内容`)
    await Promise.all([loadItems(), loadMetadata(), loadSettings()])
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
      <div class="header-actions">
        <button class="health-pill" :class="{ online: health }" type="button" @click="checkHealth">
          <el-icon :class="{ spinning: healthLoading }"><Refresh v-if="healthLoading" /><Connection v-else /></el-icon>
          <span>{{ health ? '本地服务已连接' : healthError || '后端未连接' }}</span>
        </button>
      </div>
    </header>

    <section class="library-heading">
      <div>
        <span class="eyebrow">PERSONAL LIBRARY</span>
        <h1>知识库</h1>
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
        <el-select v-model="tagId" clearable filterable size="large" placeholder="全部标签" aria-label="标签筛选" style="width: 160px">
          <el-option v-for="tag in tags" :key="tag.id" :label="`#${tag.name}`" :value="tag.id" />
        </el-select>
        <el-button size="large" :icon="Setting" @click="taxonomyVisible = true">管理</el-button>
        <el-button size="large" type="danger" plain @click="clearLibrary">清空知识库</el-button>
      </div>
      <div class="filter-row ai-actions">
        <span>AI 分类</span>
        <el-button size="large" :type="settings?.aiConfigured ? 'success' : 'warning'" plain class="ai-settings-entry" @click="openAiSettings">
          {{ aiSettingsButtonText(settings) }}
        </el-button>
        <el-button size="large" plain :loading="isAiActionLoading(activeAiAction, 'SELECTED')" :disabled="isAiActionDisabled(activeAiAction, 'SELECTED')" @click="organizeSelected">分类所选帖子</el-button>
        <el-button size="large" type="primary" plain :loading="isAiActionLoading(activeAiAction, 'ALL_PENDING')" :disabled="isAiActionDisabled(activeAiAction, 'ALL_PENDING')" @click="organizePending">分类全部待分类帖子</el-button>
        <el-button size="large" plain type="danger" :icon="CircleClose" :loading="aiCancelling" :disabled="!aiTask?.id || !activeAiAction || activeAiAction === 'CURRENT'" @click="cancelActiveAiTask">中断分类</el-button>
        <span v-if="aiTask" class="ai-progress">{{ aiTaskProgressText(aiTask) }}</span>
      </div>
    </section>

    <el-alert v-if="listError" :title="listError" type="error" show-icon :closable="false">
      <template #default><el-button text @click="loadItems">重新加载</el-button></template>
    </el-alert>

    <section class="library-body">
      <aside class="category-sidebar" aria-label="分类树">
        <div class="source-scope-nav">
          <button type="button" :class="{ active: sourceScope === 'ALL' && categoryId !== '__pending__' }" @click="selectSource('ALL')">所有内容</button>
          <button type="button" :class="{ active: sourceScope === 'FAVORITE' && categoryId !== '__pending__' }" @click="selectSource('FAVORITE')">我的收藏</button>
          <button type="button" :class="{ active: categoryId === '__pending__' }" @click="selectCategory('__pending__')">待整理</button>
        </div>
        <div v-for="category in categoryTree" :key="category.id" class="category-branch">
          <button type="button" :class="{ active: categoryId === category.id }" @click="selectCategory(category.id)">
            <span>{{ category.name }}</span><small>{{ category.itemCount }}</small>
          </button>
          <button
            v-for="child in category.children"
            :key="child.id"
            type="button"
            class="child"
            :class="{ active: categoryId === child.id }"
            @click="selectCategory(child.id)"
          >
            <span>{{ child.name }}</span><small>{{ child.itemCount }}</small>
          </button>
        </div>
      </aside>

      <div class="library-results">
        <section v-loading="listLoading" class="library-grid" :class="{ empty: !items.length }" aria-live="polite">
          <KnowledgeCard
            v-for="item in items"
            :key="item.id"
            :item="item"
            :selected="selectedIds.includes(item.id)"
            :category-name="item.categoryId ? categoryNames[item.categoryId] || null : null"
            :tag-names="tagNames"
            @open="openItem"
            @filter-tag="filterByTag"
            @toggle-select="toggleSelect"
          />
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
      </div>
    </section>

    <KnowledgeDetailDrawer
      v-model="drawerVisible"
      :item="selectedItem"
      :loading="detailLoading"
      :saving="detailSaving"
      :organizing="isAiActionLoading(activeAiAction, 'CURRENT')"
      :organize-disabled="isAiActionDisabled(activeAiAction, 'CURRENT')"
      :categories="orderedCategories"
      :tags="tags"
      @save="saveDetails"
      @organize="organizeSelectedItem"
      @delete="deleteSelectedItem"
    />

    <TaxonomyDialog
      v-model="taxonomyVisible"
      :categories="orderedCategories"
      :tags="tags"
      :source-tags="sourceTags"
      :suggestions="categorySuggestions"
      :loading="taxonomyLoading"
      :suggesting="taxonomySuggesting"
      @create-category="addCategory"
      @generate-suggestions="generateSuggestions"
      @confirm-suggestions="confirmSuggestions"
      @create-tag="addTag"
      @edit-category="editCategory"
      @edit-tag="editTag"
      @move-category="moveCategory"
      @merge-tag="mergeTag"
      @delete-category="removeCategory"
      @delete-tag="removeTag"
    />

    <SettingsDialog
      v-model="settingsVisible"
      :settings="settings"
      :latest-sync-run="latestSyncRun"
      :loading="settingsLoading"
      :saving="settingsSaving"
      :testing-ai="aiTesting"
      :active-ai-action="activeAiAction"
      :ai-cancelling="aiCancelling"
      :ai-task="aiTask"
      @reload="loadSettings"
      @save-ai="saveAiSettings"
      @test-ai="testAi"
      @clear-ai-key="clearAiKey"
      @organize-pending="organizePending"
      @cancel-ai-task="cancelActiveAiTask"
    />
  </main>
</template>
