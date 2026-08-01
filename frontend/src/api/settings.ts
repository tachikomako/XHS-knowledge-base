export interface SettingsResponse {
  aiEnabled: boolean
  aiConfigured: boolean
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

export function fetchSettings(signal?: AbortSignal): Promise<SettingsResponse> {
  return requestJson('/api/v1/settings', { signal })
}

export function fetchLatestSyncRun(signal?: AbortSignal): Promise<SyncRunResponse | null> {
  return requestJson('/api/v1/sync-runs/latest', { signal })
}

export function updateAiSettings(aiEnabled: boolean): Promise<SettingsResponse> {
  return requestJson('/api/v1/settings/ai', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ aiEnabled }),
  })
}

export function testAiConnection(): Promise<AiConnectionTestResponse> {
  return requestJson('/api/v1/settings/ai/test', { method: 'POST' })
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
