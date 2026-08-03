<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { AiSettingsUpdate, SettingsResponse, SyncRunResponse } from '../api/settings'

const MASKED_KEY = '••••••••••••••••'

const props = defineProps<{
  modelValue: boolean
  settings: SettingsResponse | null
  latestSyncRun: SyncRunResponse | null
  loading: boolean
  saving: boolean
  testingAi: boolean
  organizingPending: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saveAi: [value: AiSettingsUpdate]
  reload: []
  testAi: []
  clearAiKey: []
  organizePending: []
}>()

const form = reactive({
  aiEnabled: false,
  apiKey: '',
  baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
  model: 'qwen-plus',
})

watch(() => props.settings, (settings) => {
  if (!settings) return
  form.aiEnabled = settings.aiEnabled
  form.apiKey = ''
  form.baseUrl = settings.baseUrl || 'https://dashscope.aliyuncs.com/compatible-mode/v1'
  form.model = settings.model || 'qwen-plus'
}, { immediate: true })

function save() {
  emit('saveAi', {
    aiEnabled: form.aiEnabled,
    apiKey: form.apiKey.trim(),
    baseUrl: form.baseUrl.trim(),
    model: form.model.trim(),
  })
}

function syncStatusLabel(status: SyncRunResponse['status']) {
  return {
    RUNNING: '进行中',
    COMPLETED: '已完成',
    PARTIAL_FAILED: '部分失败',
    FAILED: '失败',
  }[status]
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="设置"
    width="min(560px, calc(100% - 24px))"
    @update:model-value="$emit('update:modelValue', $event)"
    @open="$emit('reload')"
  >
    <div v-loading="loading" class="settings-layout">
      <section class="settings-block">
        <div class="settings-row">
          <div>
            <h3>AI 自动整理</h3>
            <p>开启后，新导入或更新的内容会在后台尝试生成摘要、分类和标签。</p>
          </div>
          <el-switch v-model="form.aiEnabled" :disabled="loading || saving" />
        </div>

        <el-form label-position="top" class="ai-form" @submit.prevent>
          <el-form-item label="Qwen API Key">
            <el-input
              v-model="form.apiKey"
              type="password"
              show-password
              :placeholder="settings?.aiConfigured ? MASKED_KEY : 'sk-xxxx'"
              autocomplete="off"
            />
          </el-form-item>
          <el-form-item label="Qwen Base URL" required>
            <el-input v-model="form.baseUrl" autocomplete="off" />
          </el-form-item>
          <el-form-item label="Qwen Model" required>
            <el-input v-model="form.model" autocomplete="off" />
          </el-form-item>
        </el-form>

        <section class="settings-row compact">
          <span>配置状态</span>
          <el-tag :type="settings?.aiConfigured ? 'success' : 'info'">
            {{ settings?.aiConfigured ? '已配置' : '未配置' }}
          </el-tag>
        </section>
        <section class="settings-row compact">
          <span>AI 待处理</span>
          <strong>{{ settings?.pendingAiCount || 0 }}</strong>
        </section>
        <section class="settings-row compact">
          <span>AI 失败</span>
          <strong>{{ settings?.failedAiCount || 0 }}</strong>
        </section>

        <section class="settings-actions">
          <el-button type="primary" :loading="saving" @click="save">保存并测试</el-button>
          <el-button :loading="testingAi" @click="$emit('testAi')">测试连接</el-button>
          <el-button type="danger" plain :disabled="!settings?.aiConfigured" :loading="saving" @click="$emit('clearAiKey')">清除 API Key</el-button>
        </section>
      </section>

      <section class="settings-actions">
        <el-button type="primary" plain :loading="organizingPending" @click="$emit('organizePending')">整理待处理内容</el-button>
      </section>

      <section class="settings-note">
        <h3>最近同步</h3>
        <p v-if="latestSyncRun">
          {{ syncStatusLabel(latestSyncRun.status) }} ·
          发现 {{ latestSyncRun.discoveredCount }} ·
          处理 {{ latestSyncRun.processedCount }} ·
          新增 {{ latestSyncRun.createdCount }} ·
          更新 {{ latestSyncRun.updatedCount }} ·
          未变 {{ latestSyncRun.unchangedCount }}
        </p>
        <details v-if="latestSyncRun?.errorSummary" class="sync-diagnostics">
          <summary>查看同步详情</summary>
          <p>{{ latestSyncRun.errorSummary }}</p>
        </details>
        <p v-if="!latestSyncRun">暂无同步记录。</p>
      </section>

      <section class="settings-note">
        <h3>本地访问令牌</h3>
        <p>写入设置和 Chrome 扩展导入都会使用后端的 `XHS_EXTENSION_TOKEN` 保护。</p>
      </section>
    </div>
  </el-dialog>
</template>
