<script setup lang="ts">
import { reactive, watch } from 'vue'
import { CircleClose } from '@element-plus/icons-vue'
import type { AiOrganizeTask } from '../api/items'
import { aiTaskProgressText, isAiActionDisabled, isAiActionLoading } from '../aiTaskUi'
import type { AiAction } from '../aiTaskUi'
import type { AiSettingsUpdate, SettingsResponse, SyncRunResponse } from '../api/settings'

const MASKED_KEY = '••••••••••••••••'

const props = defineProps<{
  modelValue: boolean
  settings: SettingsResponse | null
  latestSyncRun: SyncRunResponse | null
  loading: boolean
  saving: boolean
  testingAi: boolean
  activeAiAction: AiAction
  aiCancelling: boolean
  aiTask: AiOrganizeTask | null
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saveAi: [value: AiSettingsUpdate]
  reload: []
  testAi: []
  clearAiKey: []
  organizePending: []
  cancelAiTask: []
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
    title="AI 与本地服务设置"
    width="min(560px, calc(100% - 24px))"
    @update:model-value="$emit('update:modelValue', $event)"
    @open="$emit('reload')"
  >
    <div v-loading="loading" class="settings-layout">
      <section class="settings-block">
        <div class="settings-row">
          <div>
            <h3>Qwen 配置状态</h3>
            <p>{{ settings?.aiConfigured ? `已配置 ${settings?.model || form.model}` : '尚未配置 Qwen API，请先填写并保存。' }}</p>
          </div>
          <el-tag :type="settings?.aiConfigured ? 'success' : 'warning'">
            {{ settings?.aiConfigured ? '已配置' : '未配置' }}
          </el-tag>
        </div>
        <section class="settings-row compact">
          <span>AI 分类开关</span>
          <el-switch v-model="form.aiEnabled" :disabled="loading || saving" />
        </section>
        <section class="settings-row compact">
          <span>当前模型</span>
          <strong>{{ settings?.model || form.model }}</strong>
        </section>
        <section class="settings-row compact">
          <span>AI 待处理</span>
          <strong>{{ settings?.pendingAiCount || 0 }}</strong>
        </section>
        <section class="settings-row compact">
          <span>AI 失败</span>
          <strong>{{ settings?.failedAiCount || 0 }}</strong>
        </section>

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

        <section class="settings-actions">
          <el-button type="primary" :loading="saving" @click="save">保存并测试</el-button>
          <el-button type="danger" plain :disabled="!settings?.aiConfigured" :loading="saving" @click="$emit('clearAiKey')">清除 API Key</el-button>
        </section>
      </section>

      <section class="settings-note">
        <h3>当前 AI 任务进度</h3>
        <div class="settings-actions">
          <el-button type="primary" plain :loading="isAiActionLoading(activeAiAction, 'ALL_PENDING')" :disabled="isAiActionDisabled(activeAiAction, 'ALL_PENDING')" @click="$emit('organizePending')">分类全部待分类帖子</el-button>
          <el-button plain type="danger" :icon="CircleClose" :loading="aiCancelling" :disabled="!aiTask?.id || !activeAiAction || activeAiAction === 'CURRENT'" @click="$emit('cancelAiTask')">中断分类</el-button>
        </div>
        <p v-if="aiTask">{{ aiTaskProgressText(aiTask) }}</p>
        <p v-else>暂无正在运行的 AI 分类任务。</p>
      </section>

      <section class="settings-note">
        <h3>最近同步信息</h3>
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
    </div>
  </el-dialog>
</template>
