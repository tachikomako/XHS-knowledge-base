<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Delete, Link, RefreshLeft, Upload } from '@element-plus/icons-vue'
import type { KnowledgeItem } from '../api/items'
import { proxiedImageUrl } from '../api/media'
import type { Category, Tag } from '../api/metadata'

const props = defineProps<{
  modelValue: boolean
  item: KnowledgeItem | null
  loading: boolean
  saving: boolean
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
  lifecycle: [action: 'archive' | 'trash' | 'restore']
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
const imageUrls = computed(() => props.item?.imageUrls.map(proxiedImageUrl) || [])

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

      <div v-if="imageUrls.length" class="image-strip">
        <el-image
          v-for="image in imageUrls"
          :key="image"
          :src="image"
          :preview-src-list="imageUrls"
          fit="cover"
          lazy
        />
      </div>

      <section class="detail-section">
        <div class="section-heading"><h2>正文</h2><span>{{ item.content ? '已采集' : '未采集' }}</span></div>
        <p class="source-content">{{ item.content || '当前记录暂未采集正文。' }}</p>
        <a :href="item.originalUrl" target="_blank" rel="noopener noreferrer" class="source-link">
          <el-icon><Link /></el-icon>查看小红书原帖
        </a>
      </section>

      <section class="detail-section editor-section">
        <div class="section-heading"><h2>知识整理</h2><span>手工内容不会被来源同步覆盖</span></div>
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
        <el-button type="primary" :loading="saving" @click="save">保存整理</el-button>
      </section>

      <section class="detail-actions">
        <el-button v-if="item.lifecycleStatus === 'ACTIVE'" :icon="Upload" @click="$emit('lifecycle', 'archive')">归档</el-button>
        <el-button v-if="item.lifecycleStatus !== 'TRASHED'" type="danger" plain :icon="Delete" @click="$emit('lifecycle', 'trash')">移入回收站</el-button>
        <el-button v-if="item.lifecycleStatus !== 'ACTIVE'" type="success" plain :icon="RefreshLeft" @click="$emit('lifecycle', 'restore')">恢复到知识库</el-button>
      </section>
    </article>
  </el-drawer>
</template>
