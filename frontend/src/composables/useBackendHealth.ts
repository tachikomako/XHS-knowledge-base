import { onBeforeUnmount, ref } from 'vue'
import { fetchHealth, type HealthResponse } from '../api/health'

export function useBackendHealth() {
  const health = ref<HealthResponse | null>(null)
  const loading = ref(false)
  const error = ref('')
  let activeController: AbortController | null = null

  async function checkHealth() {
    activeController?.abort()
    activeController = new AbortController()
    loading.value = true
    error.value = ''

    const timeoutId = window.setTimeout(() => activeController?.abort(), 5000)
    try {
      health.value = await fetchHealth(activeController.signal)
    } catch (cause) {
      health.value = null
      error.value = cause instanceof DOMException && cause.name === 'AbortError'
        ? '连接超时，请确认后端已经启动'
        : cause instanceof Error
          ? cause.message
          : '无法连接后端'
    } finally {
      window.clearTimeout(timeoutId)
      loading.value = false
    }
  }

  onBeforeUnmount(() => activeController?.abort())

  return { health, loading, error, checkHealth }
}
