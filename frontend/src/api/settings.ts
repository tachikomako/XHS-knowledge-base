export interface SettingsResponse {
  aiEnabled: boolean
  aiConfigured: boolean
  model: string
}

export function fetchSettings(signal?: AbortSignal): Promise<SettingsResponse> {
  return requestJson('/api/v1/settings', { signal })
}

export function updateAiSettings(aiEnabled: boolean): Promise<SettingsResponse> {
  return requestJson('/api/v1/settings/ai', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ aiEnabled }),
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
  return response.json() as Promise<T>
}
