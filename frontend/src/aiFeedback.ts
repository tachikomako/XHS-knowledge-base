import type { AiOrganizeBatchResponse } from './api/items'

export function organizePendingFeedback(result: AiOrganizeBatchResponse) {
  if (result.processed > 0) {
    return {
      type: 'success' as const,
      message: result.message || `已处理 ${result.processed} 条，成功 ${result.succeeded} 条，失败 ${result.failed} 条`,
    }
  }
  return {
    type: result.message ? 'warning' as const : 'info' as const,
    message: result.message || '当前没有需要 AI 整理的内容',
  }
}
