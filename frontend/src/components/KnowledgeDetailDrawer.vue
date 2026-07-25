<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Delete, Link, RefreshLeft, Upload } from '@element-plus/icons-vue'
import type { KnowledgeItem } from '../api/items'

const props = defineProps<{
  modelValue: boolean
  item: KnowledgeItem | null
  loading: boolean
  saving: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  save: [changes: { summary: string | null; userNote: string | null }]
  lifecycle: [action: 'archive' | 'trash' | 'restore']
}>()

const summary = ref('')
const userNote = ref('')

watch(() => props.item, (item) => {
  summary.value = item?.summary || ''
  userNote.value = item?.userNote || ''
}, { immediate: true })

const drawerVisible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

function save() {
  emit('save', {
    summary: summary.value.trim() || null,
    userNote: userNote.value.trim() || null,
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
        <span>小红书 · {{ item.captureLevel === 'DETAIL' ? '正文快照' : '链接卡片' }}</span>
        <el-button text @click="drawerVisible = false">关闭</el-button>
      </div>

      <h1>{{ item.title }}</h1>
      <div class="detail-byline">
        <span>{{ item.author || '未知作者' }}</span>
        <span>更新于 {{ formatDate(item.updatedAt) }}</span>
      </div>

      <div v-if="item.imageUrls.length" class="image-strip">
        <el-image
          v-for="image in item.imageUrls"
          :key="image"
          :src="image"
          :preview-src-list="item.imageUrls"
          fit="cover"
          lazy
          referrerpolicy="no-referrer"
        />
      </div>

      <section class="detail-section">
        <div class="section-heading"><h2>内容快照</h2><span>{{ item.content ? '已保存在本地' : '未采集正文' }}</span></div>
        <p class="source-content">{{ item.content || '当前记录只有卡片信息，可以打开原帖后再次剪藏以升级为正文快照。' }}</p>
        <a :href="item.originalUrl" target="_blank" rel="noreferrer" class="source-link">
          <el-icon><Link /></el-icon>查看小红书原帖
        </a>
      </section>

      <section class="detail-section editor-section">
        <div class="section-heading"><h2>知识整理</h2><span>手工内容不会被来源同步覆盖</span></div>
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
