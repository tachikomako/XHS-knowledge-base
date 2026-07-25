const DEFAULT_SETTINGS = Object.freeze({
  backendUrl: 'http://127.0.0.1:8080',
  knowledgeBaseUrl: 'http://127.0.0.1:5173',
  extensionToken: '',
})

chrome.runtime.onInstalled.addListener(async () => {
  const saved = await chrome.storage.local.get(Object.keys(DEFAULT_SETTINGS))
  await chrome.storage.local.set({ ...DEFAULT_SETTINGS, ...saved })
})

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type !== 'CHECK_HEALTH') {
    return false
  }

  checkHealth()
    .then(sendResponse)
    .catch((error) => sendResponse({
      ok: false,
      error: error instanceof Error ? error.message : '无法连接后端',
    }))

  return true
})

async function checkHealth() {
  const settings = await chrome.storage.local.get(Object.keys(DEFAULT_SETTINGS))
  const backendUrl = normalizeBaseUrl(settings.backendUrl || DEFAULT_SETTINGS.backendUrl)
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), 5000)

  try {
    const headers = { Accept: 'application/json' }
    if (settings.extensionToken) {
      headers['X-Extension-Token'] = settings.extensionToken
    }

    const response = await fetch(`${backendUrl}/api/v1/health`, {
      headers,
      signal: controller.signal,
    })
    if (!response.ok) {
      throw new Error(`后端返回 ${response.status}`)
    }

    return { ok: true, health: await response.json() }
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new Error('连接超时，请确认后端已经启动')
    }
    throw error
  } finally {
    clearTimeout(timeoutId)
  }
}

function normalizeBaseUrl(value) {
  return String(value).trim().replace(/\/+$/, '')
}
