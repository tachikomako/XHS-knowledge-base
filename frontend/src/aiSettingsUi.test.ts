import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { aiSettingsButtonText, canStartAiAction } from './aiSettingsUi'
import type { SettingsResponse } from './api/settings'

function settings(overrides: Partial<SettingsResponse>): SettingsResponse {
  return {
    aiEnabled: true,
    aiConfigured: false,
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    model: 'qwen-plus',
    pendingAiCount: 0,
    failedAiCount: 0,
    ...overrides,
  }
}

describe('AI settings UI state', () => {
  it('shows different visible button text for configured and unconfigured states', () => {
    expect(aiSettingsButtonText(settings({ aiConfigured: true }))).toBe('⚙ AI 设置 · 已配置')
    expect(aiSettingsButtonText(settings({ aiConfigured: false }))).toBe('⚠ AI 设置 · 未配置')
  })

  it('does not allow creating AI tasks before Qwen is configured', () => {
    expect(canStartAiAction(settings({ aiConfigured: false }))).toBe(false)
    expect(canStartAiAction(null)).toBe(false)
    expect(canStartAiAction(settings({ aiConfigured: true }))).toBe(true)
  })

  it('surfaces the AI settings entry and unconfigured prompt in the app shell', () => {
    const app = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')

    expect(app).toContain('aiSettingsButtonText(settings)')
    expect(app).toContain('尚未配置 Qwen API，请先完成 AI 设置')
    expect(app).toContain('前往 AI 设置')
  })

  it('keeps the settings dialog hierarchy and masked API key placeholder', () => {
    const dialog = readFileSync(new URL('./components/SettingsDialog.vue', import.meta.url), 'utf8')

    expect(dialog).toContain('AI 与本地服务设置')
    expect(dialog.indexOf('Qwen 配置状态')).toBeLessThan(dialog.indexOf('Qwen API Key'))
    expect(dialog.indexOf('Qwen API Key')).toBeLessThan(dialog.indexOf('保存并测试'))
    expect(dialog.indexOf('保存并测试')).toBeLessThan(dialog.indexOf('当前 AI 任务进度'))
    expect(dialog.indexOf('当前 AI 任务进度')).toBeLessThan(dialog.indexOf('最近同步信息'))
    expect(dialog).toContain("const MASKED_KEY = '••••••••••••••••'")
  })
})
