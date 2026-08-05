<script setup lang="ts">
import { Collection, FolderOpened, MagicStick, Search, Setting } from '@element-plus/icons-vue'

defineProps<{
  modelValue: boolean
  health: boolean
  aiConfigured: boolean | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  openAiSettings: []
  openTaxonomy: []
}>()
</script>

<template>
  <el-dialog :model-value="modelValue" title="帮助中心" width="min(720px, calc(100% - 24px))" class="help-dialog" @update:model-value="emit('update:modelValue', $event)">
    <div class="help-content">
      <section class="help-intro">
        <span class="eyebrow">SHIYE WORKFLOW</span>
        <h2>从收藏，到可检索的知识</h2>
        <p>拾叶把同步、整理和检索分开，让你可以先保存，再按自己的节奏补全文本和分类。</p>
      </section>
      <ol class="help-steps">
        <li><span class="help-step-icon"><Setting /></span><div><strong>安装并连接插件</strong><p>安装拾叶插件后打开小红书收藏页，在插件中重新扫描并开始同步。</p></div></li>
        <li><span class="help-step-icon"><Collection /></span><div><strong>同步收藏</strong><p>插件只同步收藏列表信息。需要更完整正文时，再主动勾选“补全收藏正文”。</p></div></li>
        <li><span class="help-step-icon"><FolderOpened /></span><div><strong>整理知识</strong><p>回到知识库后，可以手动为当前帖子、所选帖子或全部待整理内容执行 AI 分类。</p></div></li>
        <li><span class="help-step-icon"><Search /></span><div><strong>分类、标签与搜索</strong><p>用分类维护稳定目录，用顶部标签筛选和关键词搜索组合查找内容。</p></div></li>
      </ol>
      <div class="help-status-grid">
        <div class="help-status"><span>后端服务</span><strong>{{ health ? '已连接' : '未连接' }}</strong></div>
        <div class="help-status"><span>AI 配置</span><strong>{{ aiConfigured === null ? '状态未加载' : aiConfigured ? '已配置' : '未配置' }}</strong></div>
      </div>
      <div class="help-actions">
        <el-button plain :icon="Setting" @click="emit('openAiSettings')">打开 AI 设置</el-button>
        <el-button plain :icon="MagicStick" @click="emit('openTaxonomy')">打开分类管理</el-button>
      </div>
    </div>
  </el-dialog>
</template>
