const DEFAULT_SETTINGS = Object.freeze({
  backendUrl: 'http://127.0.0.1:8080',
  knowledgeBaseUrl: 'http://127.0.0.1:5173',
  extensionToken: '',
})

const form = document.querySelector('#settingsForm')
const submitButton = form.querySelector('button[type="submit"]')
const statusCard = document.querySelector('#statusCard')
const statusTitle = document.querySelector('#statusTitle')
const statusDetail = document.querySelector('#statusDetail')
const openButton = document.querySelector('#openKnowledgeBase')

await loadSettings()

form.addEventListener('submit', async (event) => {
  event.preventDefault()
  submitButton.disabled = true
  submitButton.textContent = '检查中…'

  try {
    const settings = readFormSettings()
    await chrome.storage.local.set(settings)
    const response = await chrome.runtime.sendMessage({ type: 'CHECK_HEALTH' })
    renderHealth(response)
  } catch (error) {
    renderError(error instanceof Error ? error.message : '保存设置失败')
  } finally {
    submitButton.disabled = false
    submitButton.textContent = '保存并检查连接'
  }
})

openButton.addEventListener('click', async () => {
  try {
    const { knowledgeBaseUrl } = readFormSettings()
    await chrome.tabs.create({ url: knowledgeBaseUrl })
  } catch (error) {
    renderError(error instanceof Error ? error.message : '无法打开知识库')
  }
})

async function loadSettings() {
  const saved = await chrome.storage.local.get(Object.keys(DEFAULT_SETTINGS))
  const settings = { ...DEFAULT_SETTINGS, ...saved }
  for (const [key, value] of Object.entries(settings)) {
    const input = document.querySelector(`#${key}`)
    if (input) input.value = value
  }
}

function readFormSettings() {
  const data = new FormData(form)
  return {
    backendUrl: normalizeUrl(data.get('backendUrl')),
    knowledgeBaseUrl: normalizeUrl(data.get('knowledgeBaseUrl')),
    extensionToken: String(data.get('extensionToken') || '').trim(),
  }
}

function normalizeUrl(value) {
  const url = new URL(String(value).trim())
  if (!['http:', 'https:'].includes(url.protocol)) {
    throw new Error('地址必须使用 HTTP 或 HTTPS')
  }
  return url.toString().replace(/\/$/, '')
}

function renderHealth(response) {
  if (!response?.ok) {
    renderError(response?.error || '无法连接后端')
    return
  }

  statusCard.className = 'status-card online'
  statusTitle.textContent = '后端已连接'
  statusDetail.textContent = `API ${response.health.apiVersion} · ${response.health.appVersion}`
}

function renderError(message) {
  statusCard.className = 'status-card error'
  statusTitle.textContent = '连接失败'
  statusDetail.textContent = message
}
