import { describe, expect, it } from 'vitest'
import { organizePendingFeedback } from './aiFeedback'
import type { AiOrganizeBatchResponse } from './api/items'

const base: AiOrganizeBatchResponse = {
  eligible: 0,
  processed: 0,
  succeeded: 0,
  failed: 0,
  blockedByContent: 0,
  blockedByManualLock: 0,
  skipped: 0,
  errors: [],
  message: null,
}

describe('AI pending feedback', () => {
  it('uses the backend message when nothing was processed', () => {
    const feedback = organizePendingFeedback({
      ...base,
      blockedByContent: 18,
      message: '没有可整理内容，请先完成正文同步',
    })

    expect(feedback).toEqual({
      type: 'warning',
      message: '没有可整理内容，请先完成正文同步',
    })
    expect(feedback.message).not.toContain('成功 0')
  })

  it('uses warning feedback and errors when processed items fail', () => {
    expect(organizePendingFeedback({
      ...base,
      eligible: 3,
      processed: 3,
      succeeded: 2,
      failed: 1,
      errors: ['item-1: timeout'],
    })).toEqual({
      type: 'warning',
      message: '已处理 3 条，成功 2 条，失败 1 条；错误：item-1: timeout',
    })
  })
})
