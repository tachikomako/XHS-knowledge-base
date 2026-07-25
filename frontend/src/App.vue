<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Collection, Connection, Refresh, Search, Setting } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { changeItemLifecycle, getItem, searchItems, updateItem } from './api/items'
import type { CaptureLevel, KnowledgeItem, LifecycleStatus } from './api/items'
import {
  createCategory,
  createTag,
  deleteCategory,
  deleteTag,
  fetchCategories,
  fetchTags,
  updateCategory,
  updateTag,
} from './api/metadata'
import type { Category, CategoryInput, Tag } from './api/metadata'
import KnowledgeCard from './components/KnowledgeCard.vue'
import KnowledgeDetailDrawer from './components/KnowledgeDetailDrawer.vue'
import TaxonomyDialog from './components/TaxonomyDialog.vue'
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
const categoryId = ref('')
const tagId = ref('')
const listLoading = ref(false)
const listError = ref('')
const drawerVisible = ref(false)
const detailLoading = ref(false)
const detailSaving = ref(false)
const selectedItem = ref<KnowledgeItem | null>(null)
const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const taxonomyVisible = ref(false)
const taxonomyLoading = ref(false)

let listController: AbortController | null = null
let detailController: AbortController | null = null
let metadataController: AbortController | null = null

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
  void loadMetadata()
})

onBeforeUnmount(() => {
  listController?.abort()
  detailController?.abort()
  metadataController?.abort()
})

watch([lifecycleStatus, captureLevel, categoryId, tagId], () => {
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

async function loadMetadata() {
  metadataController?.abort()
  const controller = new AbortController()
  metadataController = controller
  taxonomyLoading.value = true
  try {
    const [categoryResult, tagResult] = await Promise.all([
      fetchCategories(controller.signal),
      fetchTags(controller.signal),
    ])
    categories.value = categoryResult
    tags.value = tagResult
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') return
    ElMessage.error(error instanceof Error ? error.message : '无法加载分类与标签')
  } finally {
    if (metadataController === controller) taxonomyLoading.value = false
  }
}

function filterByTag(selectedTagId: string) {
  tagId.value = selectedTagId
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

async function addCategory(input: CategoryInput) {
  await mutateMetadata(() => createCategory(input), '分类已添加')
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
        <el-select v-model="categoryId" clearable size="large" placeholder="全部分类" aria-label="分类筛选" style="width: 170px">
          <el-option
            v-for="category in orderedCategories"
            :key="category.id"
            :label="categoryNames[category.id]"
            :value="category.id"
          />
        </el-select>
        <el-select v-model="tagId" clearable filterable size="large" placeholder="全部标签" aria-label="标签筛选" style="width: 160px">
          <el-option v-for="tag in tags" :key="tag.id" :label="`#${tag.name}`" :value="tag.id" />
        </el-select>
        <el-button size="large" :icon="Setting" @click="taxonomyVisible = true">管理</el-button>
      </div>
    </section>

    <el-alert v-if="listError" :title="listError" type="error" show-icon :closable="false">
      <template #default><el-button text @click="loadItems">重新加载</el-button></template>
    </el-alert>

    <section v-loading="listLoading" class="library-grid" :class="{ empty: !items.length }" aria-live="polite">
      <KnowledgeCard
        v-for="item in items"
        :key="item.id"
        :item="item"
        :category-name="item.categoryId ? categoryNames[item.categoryId] || null : null"
        :tag-names="tagNames"
        @open="openItem"
        @filter-tag="filterByTag"
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

    <KnowledgeDetailDrawer
      v-model="drawerVisible"
      :item="selectedItem"
      :loading="detailLoading"
      :saving="detailSaving"
      :categories="orderedCategories"
      :tags="tags"
      @save="saveDetails"
      @lifecycle="changeLifecycle"
    />

    <TaxonomyDialog
      v-model="taxonomyVisible"
      :categories="orderedCategories"
      :tags="tags"
      :loading="taxonomyLoading"
      @create-category="addCategory"
      @create-tag="addTag"
      @edit-category="editCategory"
      @edit-tag="editTag"
      @delete-category="removeCategory"
      @delete-tag="removeTag"
    />
  </main>
</template>
