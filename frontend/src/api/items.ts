export type CaptureLevel = 'CARD' | 'DETAIL'
export type ContentStatus = 'DISCOVERED' | 'FETCHING' | 'COMPLETED' | 'FAILED'

export interface KnowledgeItem {
  id: string
  sourceType: string
  sourceItemId: string | null
  canonicalUrl: string
  originalUrl: string
  title: string
  content: string | null
  contentStatus: ContentStatus
  contentLastError: string | null
  sourceTags: string[]
  author: string | null
  captureLevel: CaptureLevel
  summary: string | null
  userNote: string | null
  categoryId: string | null
  tagIds: string[]
  aiStatus: string
  aiConfidence: number | null
  aiLastError: string | null
  lifecycleStatus: 'ACTIVE'
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
  sourceType?: string
  captureLevel?: CaptureLevel | ''
  contentStatus?: ContentStatus | ''
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

export interface AiOrganizeBatchResponse {
  processed: number
  succeeded: number
  failed: number
  skipped: number
  message: string | null
}

export async function searchItems(
  params: ItemSearchParams = {},
  signal?: AbortSignal,
): Promise<PageResponse<KnowledgeItem>> {
  const query = new URLSearchParams()
  appendIfPresent(query, 'q', params.q?.trim())
  appendIfPresent(query, 'categoryId', params.categoryId)
  appendIfPresent(query, 'tagId', params.tagId)
  appendIfPresent(query, 'sourceType', params.sourceType)
  appendIfPresent(query, 'captureLevel', params.captureLevel)
  appendIfPresent(query, 'contentStatus', params.contentStatus)
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

export function organizeItem(id: string): Promise<KnowledgeItem> {
  return requestJson(`/api/v1/items/${encodeURIComponent(id)}/organize`, { method: 'POST' })
}

export function organizePendingAi(): Promise<AiOrganizeBatchResponse> {
  return requestJson('/api/v1/ai/organize-pending', { method: 'POST' })
}

export async function deleteItem(id: string): Promise<void> {
  await requestJson<void>(`/api/v1/items/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export function clearItems(confirmation: string): Promise<{ deletedItems: number }> {
  return requestJson('/api/v1/items/clear', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ confirmation }),
  })
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
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

function appendIfPresent(params: URLSearchParams, key: string, value: string | number | undefined) {
  if (value !== undefined && value !== '') params.set(key, String(value))
}
