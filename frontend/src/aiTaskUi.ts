import type { AiTaskScope, AiOrganizeTask } from './api/items'

export type AiAction = AiTaskScope | null

export function isAiActionLoading(activeAction: AiAction, action: AiTaskScope) {
  return activeAction === action
}

export function isAiActionDisabled(activeAction: AiAction, action: AiTaskScope) {
  return activeAction !== null && activeAction !== action
}

export function aiTaskScopeLabel(task: AiOrganizeTask) {
  if (task.scope === 'CURRENT') return '正在分类当前帖子'
  if (task.scope === 'SELECTED') return `正在分类所选 ${task.requestedCount || task.total} 篇帖子`
  return '正在分类全部待整理帖子'
}

export function aiTaskProgressText(task: AiOrganizeTask) {
  if (task.status === 'REJECTED') return task.message || '所选帖子当前不可分类'
  const label = task.status === 'CANCELLED' ? 'AI 分类已中断' : aiTaskScopeLabel(task)
  const skipped = task.skipped > 0 ? ` · 跳过 ${task.skipped}` : ''
  return `${label} · 已处理 ${task.processed} / ${task.total} · 成功 ${task.succeeded} · 失败 ${task.failed}${skipped}`
}
