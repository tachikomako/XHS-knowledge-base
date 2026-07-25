<script setup lang="ts">
import { Calendar, Document, Picture } from '@element-plus/icons-vue'
import type { KnowledgeItem } from '../api/items'

defineProps<{
  item: KnowledgeItem
  categoryName: string | null
  tagNames: Record<string, string>
}>()
defineEmits<{
  open: [item: KnowledgeItem]
  filterTag: [tagId: string]
}>()

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric' }).format(new Date(value))
}
</script>

<template>
  <article class="knowledge-card" tabindex="0" @click="$emit('open', item)" @keyup.enter="$emit('open', item)">
    <div class="card-cover" :class="{ empty: !item.coverUrl }">
      <el-image v-if="item.coverUrl" :src="item.coverUrl" fit="cover" loading="lazy" referrerpolicy="no-referrer">
        <template #error><el-icon><Picture /></el-icon></template>
      </el-image>
      <el-icon v-else><Document /></el-icon>
      <span class="source-chip">小红书</span>
    </div>
    <div class="card-body">
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
        <span>{{ categoryName || (item.captureLevel === 'DETAIL' ? '正文快照' : '链接卡片') }}</span>
        <span v-if="item.userNote" class="has-note">有笔记</span>
      </div>
    </div>
  </article>
</template>
