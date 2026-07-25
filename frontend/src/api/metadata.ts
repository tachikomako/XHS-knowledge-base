export interface Category {
  id: string
  name: string
  parentId: string | null
  sortOrder: number
  itemCount: number
}

export interface Tag {
  id: string
  name: string
  itemCount: number
}

export interface CategoryInput {
  name: string
  parentId: string | null
  sortOrder: number
}

export function fetchCategories(signal?: AbortSignal): Promise<Category[]> {
  return requestJson('/api/v1/categories', { signal })
}

export function createCategory(input: CategoryInput): Promise<Category> {
  return requestJson('/api/v1/categories', jsonRequest('POST', input))
}

export function updateCategory(id: string, input: CategoryInput): Promise<Category> {
  return requestJson(`/api/v1/categories/${encodeURIComponent(id)}`, jsonRequest('PUT', input))
}

export function deleteCategory(id: string): Promise<void> {
  return requestVoid(`/api/v1/categories/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export function fetchTags(signal?: AbortSignal): Promise<Tag[]> {
  return requestJson('/api/v1/tags', { signal })
}

export function createTag(name: string): Promise<Tag> {
  return requestJson('/api/v1/tags', jsonRequest('POST', { name }))
}

export function updateTag(id: string, name: string): Promise<Tag> {
  return requestJson(`/api/v1/tags/${encodeURIComponent(id)}`, jsonRequest('PUT', { name }))
}

export function deleteTag(id: string): Promise<void> {
  return requestVoid(`/api/v1/tags/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

async function requestJson<T>(url: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(url, withAccept(options))
  if (!response.ok) throw await apiError(response)
  return response.json() as Promise<T>
}

async function requestVoid(url: string, options: RequestInit): Promise<void> {
  const response = await fetch(url, withAccept(options))
  if (!response.ok) throw await apiError(response)
}

function jsonRequest(method: string, body: unknown): RequestInit {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }
}

function withAccept(options: RequestInit): RequestInit {
  return {
    ...options,
    headers: { Accept: 'application/json', ...options.headers },
  }
}

async function apiError(response: Response) {
  const body = await response.json().catch(() => null) as { message?: string } | null
  return new Error(body?.message || `后端返回 ${response.status}`)
}
