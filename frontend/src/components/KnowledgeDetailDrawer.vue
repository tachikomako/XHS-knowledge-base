<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Delete, Link, MagicStick } from '@element-plus/icons-vue'
import type { KnowledgeItem } from '../api/items'
import type { Category, Tag } from '../api/metadata'

const props = defineProps<{
  modelValue: boolean
  item: KnowledgeItem | null
  loading: boolean
  saving: boolean
  organizing: boolean
  categories: Category[]
  tags: Tag[]
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  save: [changes: {
    summary: string | null
    userNote: string | null
    categoryId: string | null
    tagIds: string[]
  }]
  delete: []
  organize: []
}>()

const summary = ref('')
const userNote = ref('')
const categoryId = ref<string | null>(null)
const tagIds = ref<string[]>([])

watch(() => props.item, (item) => {
  summary.value = item?.summary || ''
  userNote.value = item?.userNote || ''
  categoryId.value = item?.categoryId || null
  tagIds.value = item?.tagIds ? [...item.tagIds] : []
}, { immediate: true })

const drawerVisible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})
function save() {
  emit('save', {
    summary: summary.value.trim() || null,
    userNote: userNote.value.trim() || null,
    categoryId: categoryId.value,
    tagIds: tagIds.value,
  })
}

function formatDate(value: string | null) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function contentStatusLabel(status: string) {
  return {
    DISCOVERED: '正文待获取',
    FETCHING: '正文获取中',
    COMPLETED: '正文已完成',
    FAILED: '正文获取失败',
  }[status] || status
}

function aiStatusLabel(status: string) {
  return {
    NOT_REQUESTED: 'AI 待处理',
    PENDING: 'AI 整理中',
    SUCCESS: 'AI 已完成',
    FAILED: 'AI 失败',
  }[status] || status
}
</script>

<template>
  <el-drawer v-model="drawerVisible" size="min(620px, 100%)" :with-header="false" class="detail-drawer">
    <div v-if="loading" class="drawer-loading"><el-skeleton :rows="8" animated /></div>
    <article v-else-if="item" class="detail-content">
      <div class="detail-topline">
        <span>小红书</span>
        <el-button text @click="drawerVisible = false">关闭</el-button>
      </div>

      <h1>{{ item.title }}</h1>
      <div class="detail-byline">
        <span>{{ item.author || '未知作者' }}</span>
        <span>更新于 {{ formatDate(item.updatedAt) }}</span>
      </div>

      <section class="detail-section">
        <div class="section-heading"><h2>正文</h2><span>{{ contentStatusLabel(item.contentStatus) }}</span></div>
        <p v-if="item.contentStatus === 'FAILED' && item.contentLastError" class="status-error">{{ item.contentLastError }}</p>
        <p class="source-content">{{ item.content || '当前记录暂未采集正文。' }}</p>
        <a :href="item.originalUrl" target="_blank" rel="noopener noreferrer" class="source-link">
          <el-icon><Link /></el-icon>查看小红书原帖
        </a>
      </section>

      <section class="detail-section editor-section">
        <div class="section-heading"><h2>知识整理</h2><span>{{ aiStatusLabel(item.aiStatus) }}</span></div>
        <div class="metadata-fields">
          <label>分类
            <el-select v-model="categoryId" clearable placeholder="未分类">
              <el-option
                v-for="category in categories"
                :key="category.id"
                :label="category.parentId ? `　${category.name}` : category.name"
                :value="category.id"
              />
            </el-select>
          </label>
          <label>标签
            <el-select v-model="tagIds" multiple :multiple-limit="20" collapse-tags collapse-tags-tooltip placeholder="选择标签">
              <el-option v-for="tag in tags" :key="tag.id" :label="`#${tag.name}`" :value="tag.id" />
            </el-select>
          </label>
        </div>
        <label>摘要<el-input v-model="summary" type="textarea" :rows="3" maxlength="2000" show-word-limit /></label>
        <label>我的笔记<el-input v-model="userNote" type="textarea" :rows="6" maxlength="20000" show-word-limit /></label>
        <div class="editor-actions">
          <el-button type="primary" :loading="saving" @click="save">保存整理</el-button>
          <el-button :icon="MagicStick" :loading="organizing" @click="$emit('organize')">重新 AI 整理</el-button>
        </div>
      </section>

      <section class="detail-actions">
        <el-button type="danger" plain :icon="Delete" @click="$emit('delete')">删除</el-button>
      </section>
    </article>
  </el-drawer>
</template>
