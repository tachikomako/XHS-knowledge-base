<script setup lang="ts">
import type { SettingsResponse, SyncRunResponse } from '../api/settings'

defineProps<{
  modelValue: boolean
  settings: SettingsResponse | null
  latestSyncRun: SyncRunResponse | null
  loading: boolean
  saving: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  toggleAi: [enabled: boolean]
  reload: []
}>()

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
    width="min(520px, calc(100% - 24px))"
    @update:model-value="$emit('update:modelValue', $event)"
    @open="$emit('reload')"
  >
    <div v-loading="loading" class="settings-layout">
      <section class="settings-row">
        <div>
          <h3>AI 整理</h3>
          <p>开启后，新导入或更新的内容会在后台尝试生成摘要、分类和标签。</p>
        </div>
        <el-switch
          :model-value="settings?.aiEnabled || false"
          :loading="saving"
          :disabled="loading"
          @change="$emit('toggleAi', Boolean($event))"
        />
      </section>

      <section class="settings-row compact">
        <span>Qwen 配置</span>
        <el-tag :type="settings?.aiConfigured ? 'success' : 'info'">
          {{ settings?.aiConfigured ? '已配置' : '未配置' }}
        </el-tag>
      </section>
      <section class="settings-row compact">
        <span>当前模型</span>
        <code>{{ settings?.model || 'qwen-plus' }}</code>
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
        <p v-if="latestSyncRun?.errorSummary">{{ latestSyncRun.errorSummary }}</p>
        <p v-if="!latestSyncRun">暂无同步记录。</p>
      </section>

      <el-alert
        v-if="settings && !settings.aiConfigured"
        title="AI 开关可以先保存；真正整理前仍需在后端环境变量中配置 QWEN_API_KEY。"
        type="warning"
        show-icon
        :closable="false"
      />

      <section class="settings-note">
        <h3>插件访问令牌</h3>
        <p>Chrome 扩展里的本地访问令牌需要和后端 `XHS_EXTENSION_TOKEN` 保持一致。令牌只用于保护本地导入接口，不会上传到小红书。</p>
      </section>
    </div>
  </el-dialog>
</template>
