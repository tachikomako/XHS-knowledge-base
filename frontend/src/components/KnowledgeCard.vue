<script setup lang="ts">
import { Calendar } from '@element-plus/icons-vue'
import type { KnowledgeItem } from '../api/items'

defineProps<{
  item: KnowledgeItem
  categoryName: string | null
  tagNames: Record<string, string>
  selected?: boolean
}>()
defineEmits<{
  open: [item: KnowledgeItem]
  filterTag: [tagId: string]
  toggleSelect: [item: KnowledgeItem]
}>()

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric' }).format(new Date(value))
}

function reviewStatus(item: KnowledgeItem) {
  if (item.aiStatus === 'PENDING') return 'AI 待处理'
  if (item.aiStatus === 'FAILED') return 'AI 处理失败，可重试'
  if (item.aiStatus === 'COMPLETED' && item.contentStatus !== 'COMPLETED') return 'AI 已基于标题和标签整理；正文尚未补全'
  if (item.aiStatus === 'COMPLETED' && item.aiConfidence !== null && item.aiConfidence < 0.5) return '建议人工确认'
  if (item.aiStatus === 'COMPLETED' && !item.categoryId) return '暂无分类，建议人工整理'
  if (item.contentStatus !== 'COMPLETED') return '正文尚未补全'
  return ''
}

function sourceLabel(item: KnowledgeItem) {
  const relations = item.sourceRelations || []
  if (relations.includes('FAVORITE') && relations.includes('LIKED')) return '收藏（含历史点赞）'
  if (relations.includes('LIKED')) return '历史点赞'
  if (relations.includes('FAVORITE')) return '收藏'
  return item.sourceType === 'XIAOHONGSHU' ? '小红书' : item.sourceType
}
</script>

<template>
  <article class="knowledge-card" tabindex="0" @click="$emit('open', item)" @keyup.enter="$emit('open', item)">
    <div class="card-body">
      <label class="card-select" @click.stop>
        <input type="checkbox" :checked="selected" @change="$emit('toggleSelect', item)" />
        <span>选择</span>
      </label>
      <div class="card-meta">
        <span>{{ item.author || '未知作者' }}</span>
        <span><el-icon><Calendar /></el-icon>{{ formatDate(item.updatedAt) }}</span>
      </div>
      <h2>{{ item.title }}</h2>
      <p>{{ item.summary || item.content || '尚未保存正文摘要。打开详情可前往原帖。' }}</p>
      <div v-if="item.tagIds.length" class="card-tags">
        <button v-for="tagId in item.tagIds.slice(0, 4)" :key="tagId" type="button" @click.stop="$emit('filterTag', tagId)">
          #{{ tagNames[tagId] || '未知标签' }}
        </button>
      </div>
      <div class="card-footer">
        <span>{{ categoryName || '未分类' }}</span>
        <span>{{ sourceLabel(item) }}</span>
        <span v-if="item.userNote" class="has-note">有笔记</span>
      </div>
      <div v-if="reviewStatus(item)" class="card-status">{{ reviewStatus(item) }}</div>
    </div>
  </article>
</template>
