export type LifecycleStatus = 'ACTIVE' | 'ARCHIVED' | 'TRASHED'
export type CaptureLevel = 'CARD' | 'DETAIL'

export interface KnowledgeItem {
  id: string
  sourceType: string
  sourceItemId: string | null
  canonicalUrl: string
  originalUrl: string
  title: string
  content: string | null
  author: string | null
  coverUrl: string | null
  imageUrls: string[]
  captureLevel: CaptureLevel
  summary: string | null
  userNote: string | null
  categoryId: string | null
  tagIds: string[]
  aiStatus: string
  aiConfidence: number | null
  aiLastError: string | null
  lifecycleStatus: LifecycleStatus
  manualMetadataLocked: boolean
  createdAt: string
  sourceUpdatedAt: string | null
  userEditedAt: string | null
  updatedAt: string
}

export interface PageResponse<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}

export interface ItemSearchParams {
  q?: string
  categoryId?: string
  tagId?: string
  lifecycleStatus?: LifecycleStatus
  captureLevel?: CaptureLevel | ''
  page?: number
  pageSize?: number
  sort?: 'updatedAt,desc' | 'updatedAt,asc' | 'createdAt,desc' | 'createdAt,asc'
}

export interface UpdateKnowledgeItem {
  summary?: string | null
  userNote?: string | null
  categoryId?: string | null
  tagIds?: string[]
}

export async function searchItems(
  params: ItemSearchParams = {},
  signal?: AbortSignal,
): Promise<PageResponse<KnowledgeItem>> {
  const query = new URLSearchParams()
  appendIfPresent(query, 'q', params.q?.trim())
  appendIfPresent(query, 'categoryId', params.categoryId)
  appendIfPresent(query, 'tagId', params.tagId)
  appendIfPresent(query, 'lifecycleStatus', params.lifecycleStatus)
  appendIfPresent(query, 'captureLevel', params.captureLevel)
  appendIfPresent(query, 'page', params.page ?? 1)
  appendIfPresent(query, 'pageSize', params.pageSize ?? 12)
  appendIfPresent(query, 'sort', params.sort ?? 'updatedAt,desc')
  return requestJson(`/api/v1/items?${query.toString()}`, { signal })
}

export function getItem(id: string, signal?: AbortSignal): Promise<KnowledgeItem> {
  return requestJson(`/api/v1/items/${encodeURIComponent(id)}`, { signal })
}

export function updateItem(id: string, changes: UpdateKnowledgeItem): Promise<KnowledgeItem> {
  return requestJson(`/api/v1/items/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(changes),
  })
}

export function changeItemLifecycle(id: string, action: 'archive' | 'trash' | 'restore'): Promise<KnowledgeItem> {
  return requestJson(`/api/v1/items/${encodeURIComponent(id)}/${action}`, { method: 'POST' })
}

export function bulkTrashItems(categoryId?: string): Promise<{ affected: number }> {
  return requestJson('/api/v1/items/bulk-trash', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(categoryId ? { scope: 'CATEGORY', categoryId } : { scope: 'ALL' }),
  })
}

export async function permanentlyDeleteItem(id: string): Promise<void> {
  const response = await fetch(`/api/v1/items/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { message?: string } | null
    throw new Error(body?.message || `后端返回 ${response.status}`)
  }
}

async function requestJson<T>(url: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(url, {
    ...options,
    headers: {
      Accept: 'application/json',
      ...options.headers,
    },
  })
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { message?: string } | null
    throw new Error(body?.message || `后端返回 ${response.status}`)
  }
  return response.json() as Promise<T>
}

function appendIfPresent(params: URLSearchParams, key: string, value: string | number | undefined) {
  if (value !== undefined && value !== '') params.set(key, String(value))
}
