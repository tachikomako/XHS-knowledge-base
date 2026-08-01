export interface SettingsResponse {
  aiEnabled: boolean
  aiConfigured: boolean
  baseUrl: string
  model: string
  pendingAiCount: number
  failedAiCount: number
}

export interface AiConnectionTestResponse {
  success: boolean
  configured: boolean
  model: string
  message: string
}

export interface SyncRunResponse {
  id: string
  requestedSources: string
  status: 'RUNNING' | 'COMPLETED' | 'PARTIAL_FAILED' | 'FAILED'
  discoveredCount: number
  processedCount: number
  createdCount: number
  updatedCount: number
  unchangedCount: number
  contentCompletedCount: number
  contentFailedCount: number
  aiCompletedCount: number
  aiFailedCount: number
  startedAt: string
  finishedAt: string | null
  errorSummary: string | null
}

export interface AiSettingsUpdate {
  aiEnabled: boolean
  apiKey: string
  baseUrl: string
  model: string
}

const extensionToken = import.meta.env.VITE_XHS_EXTENSION_TOKEN || 'dev-local-token'

export function fetchSettings(signal?: AbortSignal): Promise<SettingsResponse> {
  return requestJson('/api/v1/settings', { signal })
}

export function fetchLatestSyncRun(signal?: AbortSignal): Promise<SyncRunResponse | null> {
  return requestJson('/api/v1/sync-runs/latest', { signal })
}

export function updateAiSettings(settings: AiSettingsUpdate): Promise<SettingsResponse> {
  return requestJson('/api/v1/settings/ai', {
    method: 'PATCH',
    headers: writeHeaders(),
    body: JSON.stringify(settings),
  })
}

export function testAiConnection(): Promise<AiConnectionTestResponse> {
  return requestJson('/api/v1/settings/ai/test', { method: 'POST', headers: writeHeaders() })
}

export function clearAiCredentials(): Promise<SettingsResponse> {
  return requestJson('/api/v1/settings/ai/credentials', { method: 'DELETE', headers: writeHeaders() })
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
  const text = await response.text()
  return (text ? JSON.parse(text) : null) as T
}

function writeHeaders() {
  return {
    'Content-Type': 'application/json',
    'X-Extension-Token': extensionToken,
  }
}
