import { describe, expect, it } from 'vitest'
import { aiTaskProgressText, isAiActionDisabled, isAiActionLoading } from './aiTaskUi'
import type { AiOrganizeTask } from './api/items'

function task(overrides: Partial<AiOrganizeTask>): AiOrganizeTask {
  return {
    id: 'task-1',
    scope: 'SELECTED',
    requestedCount: 3,
    total: 3,
    processed: 2,
    succeeded: 2,
    failed: 0,
    skipped: 0,
    status: 'RUNNING',
    errors: [],
    message: null,
    ...overrides,
  }
}

describe('AI task UI state', () => {
  it('only shows loading on the selected action', () => {
    expect(isAiActionLoading('SELECTED', 'SELECTED')).toBe(true)
    expect(isAiActionLoading('SELECTED', 'ALL_PENDING')).toBe(false)
    expect(isAiActionDisabled('SELECTED', 'ALL_PENDING')).toBe(true)
  })

  it('only shows loading on the all pending action', () => {
    expect(isAiActionLoading('ALL_PENDING', 'ALL_PENDING')).toBe(true)
    expect(isAiActionLoading('ALL_PENDING', 'SELECTED')).toBe(false)
    expect(isAiActionDisabled('ALL_PENDING', 'SELECTED')).toBe(true)
  })

  it('keeps current item loading away from batch buttons', () => {
    expect(isAiActionLoading('CURRENT', 'CURRENT')).toBe(true)
    expect(isAiActionLoading('CURRENT', 'SELECTED')).toBe(false)
    expect(isAiActionLoading('CURRENT', 'ALL_PENDING')).toBe(false)
  })

  it('describes selected task scope and real progress', () => {
    expect(aiTaskProgressText(task({ scope: 'SELECTED', requestedCount: 3, total: 3 })))
      .toBe('正在分类所选 3 篇帖子 · 已处理 2 / 3 · 成功 2 · 失败 0')
  })

  it('describes all pending and current task scopes', () => {
    expect(aiTaskProgressText(task({ scope: 'ALL_PENDING' }))).toContain('正在分类全部待整理帖子')
    expect(aiTaskProgressText(task({ scope: 'CURRENT', requestedCount: 1, total: 1 }))).toContain('正在分类当前帖子')
  })

  it('describes cancelled tasks as interrupted', () => {
    expect(aiTaskProgressText(task({ status: 'CANCELLED', processed: 1, succeeded: 1 })))
      .toBe('AI 分类已中断 · 已处理 1 / 3 · 成功 1 · 失败 0')
  })

  it('describes rejected tasks without showing a fake 0 / 0 run', () => {
    expect(aiTaskProgressText(task({
      status: 'REJECTED',
      total: 0,
      skipped: 3,
      message: '所选帖子当前不可分类',
    }))).toBe('所选帖子当前不可分类')
  })
})
