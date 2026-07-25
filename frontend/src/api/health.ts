export interface HealthResponse {
  status: 'UP'
  apiVersion: string
  appVersion: string
  aiConfigured: boolean
}

export async function fetchHealth(signal?: AbortSignal): Promise<HealthResponse> {
  const response = await fetch('/api/v1/health', {
    headers: {
      Accept: 'application/json',
    },
    signal,
  })

  if (!response.ok) {
    throw new Error(`后端返回 ${response.status}`)
  }

  return response.json() as Promise<HealthResponse>
}
