import type { SettingsResponse } from './api/settings'

export function aiSettingsButtonText(settings: SettingsResponse | null) {
  return settings?.aiConfigured ? '⚙ AI 设置 · 已配置' : '⚠ AI 设置 · 未配置'
}

export function canStartAiAction(settings: SettingsResponse | null) {
  return settings?.aiConfigured === true
}
