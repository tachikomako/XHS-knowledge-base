<script setup lang="ts">
import type { SettingsResponse } from '../api/settings'

defineProps<{
  modelValue: boolean
  settings: SettingsResponse | null
  loading: boolean
  saving: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  toggleAi: [enabled: boolean]
  reload: []
}>()
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
          :disabled="!settings?.aiConfigured"
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

      <el-alert
        v-if="settings && !settings.aiConfigured"
        title="请在后端环境变量中配置 QWEN_API_KEY；浏览器端不会显示或保存 API Key。"
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
