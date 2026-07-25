<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { Connection, Collection, DataAnalysis, PriceTag } from '@element-plus/icons-vue'
import { useBackendHealth } from './composables/useBackendHealth'

const { health, loading, error, checkHealth } = useBackendHealth()

const connectionLabel = computed(() => {
  if (loading.value) return '检查中'
  if (health.value?.status === 'UP') return '后端已连接'
  return '后端未连接'
})

onMounted(checkHealth)
</script>

<template>
  <main class="page-shell">
    <section class="hero-panel">
      <div class="eyebrow">LOCAL-FIRST KNOWLEDGE BASE</div>
      <h1>把收藏变成<br />随时找得到的知识。</h1>
      <p class="hero-copy">
        小红书只是第一个入口。插件负责采集，独立网站负责分类、标签、搜索和你的个人笔记。
      </p>

      <div class="connection-card" :class="{ online: health }">
        <div class="connection-main">
          <el-icon size="22"><Connection /></el-icon>
          <div>
            <strong>{{ connectionLabel }}</strong>
            <span v-if="health">API {{ health.apiVersion }} · {{ health.appVersion }}</span>
            <span v-else>{{ error || '正在连接本地服务…' }}</span>
          </div>
        </div>
        <el-button :loading="loading" @click="checkHealth">重新检查</el-button>
      </div>
    </section>

    <section class="feature-grid" aria-label="MVP 能力">
      <article>
        <el-icon><Collection /></el-icon>
        <h2>收藏采集</h2>
        <p>批量索引收藏卡片，也可以为重要帖子保存更完整的内容快照。</p>
      </article>
      <article>
        <el-icon><PriceTag /></el-icon>
        <h2>分类与标签</h2>
        <p>一个稳定主分类，多个跨分类标签，保留你自己的知识组织方式。</p>
      </article>
      <article>
        <el-icon><DataAnalysis /></el-icon>
        <h2>可选 AI 整理</h2>
        <p>AI 负责建议摘要和元数据；即使没有模型配置，收藏仍然可以正常保存。</p>
      </article>
    </section>
  </main>
</template>
