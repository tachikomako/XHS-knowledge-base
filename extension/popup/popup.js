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
const captureButton = document.querySelector('#captureButton')
const captureTitle = document.querySelector('#captureTitle')
const captureMeta = document.querySelector('#captureMeta')
const captureWarnings = document.querySelector('#captureWarnings')
const captureResult = document.querySelector('#captureResult')

let currentExtraction = null

await loadSettings()
await Promise.all([checkHealth(), inspectCurrentPage()])

form.addEventListener('submit', async (event) => {
  event.preventDefault()
  submitButton.disabled = true
  submitButton.textContent = '检查中…'
  try {
    await chrome.storage.local.set(readFormSettings())
    await checkHealth()
  } catch (error) {
    renderHealthError(error instanceof Error ? error.message : '保存设置失败')
  } finally {
    submitButton.disabled = false
    submitButton.textContent = '保存并检查连接'
  }
})

captureButton.addEventListener('click', async () => {
  if (!currentExtraction?.items?.length) return
  captureButton.disabled = true
  captureButton.textContent = currentExtraction.captureMode === 'FAVORITES_PAGE' ? '正在分批同步…' : '正在剪藏…'
  renderCaptureResult('', '')

  try {
    const response = await chrome.runtime.sendMessage({ type: 'IMPORT_XHS_ITEMS', payload: currentExtraction })
    if (!response?.ok) throw new Error(response?.error || '保存失败')
    renderImportResult(response.result)
  } catch (error) {
    renderCaptureResult(error instanceof Error ? error.message : '保存失败', 'error')
  } finally {
    captureButton.disabled = false
    captureButton.textContent = captureButtonLabel()
  }
})

openButton.addEventListener('click', async () => {
  try {
    const { knowledgeBaseUrl } = readFormSettings()
    await chrome.tabs.create({ url: knowledgeBaseUrl })
  } catch (error) {
    renderHealthError(error instanceof Error ? error.message : '无法打开知识库')
  }
})

async function inspectCurrentPage() {
  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
    if (!tab?.id) throw new Error('无法读取当前标签页')
    const response = await chrome.tabs.sendMessage(tab.id, { type: 'INSPECT_XHS_PAGE' })
    if (!response?.ok) throw new Error(response?.error || '当前页面无法剪藏')

    if (response.pageType === 'CURRENT_POST') {
      currentExtraction = {
        captureMode: 'CURRENT_POST',
        extractorVersion: response.extractorVersion,
        items: [response.item],
      }
      captureTitle.textContent = response.item.title
      captureMeta.textContent = [
        response.item.author || '作者未知',
        response.item.captureLevel === 'DETAIL' ? '正文快照' : '链接卡片',
        response.item.imageUrls.length ? `${response.item.imageUrls.length} 张图片` : null,
      ].filter(Boolean).join(' · ')
    } else if (response.pageType === 'FAVORITES_PAGE') {
      currentExtraction = {
        captureMode: 'FAVORITES_PAGE',
        extractorVersion: response.extractorVersion,
        items: response.items,
      }
      captureTitle.textContent = `识别到 ${response.items.length} 条已加载收藏`
      captureMeta.textContent = response.items.length
        ? `页面共扫描 ${response.stats.candidates} 个卡片；向下滚动后重新打开插件可加载更多`
        : '请确认已进入“收藏”标签，并先向下滚动加载内容'
    } else {
      throw new Error('当前页面类型暂不支持')
    }

    renderWarnings(response.warnings || [])
    captureButton.textContent = captureButtonLabel()
    captureButton.disabled = currentExtraction.items.length === 0
  } catch (error) {
    currentExtraction = null
    captureTitle.textContent = '当前页面不可剪藏'
    captureMeta.textContent = explainTabError(error)
    renderWarnings([])
    captureButton.disabled = true
  }
}

function renderImportResult(result) {
  const changed = result.created + result.updated
  const message = `新增 ${result.created} · 更新 ${result.updated} · 已存在 ${result.skipped}`
  if (result.failed > 0) {
    renderCaptureResult(`${message} · 失败 ${result.failed}`, 'error')
  } else {
    renderCaptureResult(changed > 0 ? message : '这些内容已经在知识库中', 'success')
  }
}

function captureButtonLabel() {
  if (currentExtraction?.captureMode === 'FAVORITES_PAGE') return `同步 ${currentExtraction.items.length} 条收藏`
  return '剪藏当前帖子'
}

async function checkHealth() {
  const response = await chrome.runtime.sendMessage({ type: 'CHECK_HEALTH' })
  if (!response?.ok) {
    renderHealthError(response?.error || '无法连接后端')
    return
  }
  statusCard.className = 'status-card online'
  statusTitle.textContent = '后端已连接'
  statusDetail.textContent = `API ${response.health.apiVersion} · ${response.health.appVersion}`
}

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
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('地址必须使用 HTTP 或 HTTPS')
  return url.toString().replace(/\/$/u, '')
}

function renderWarnings(warnings) {
  captureWarnings.replaceChildren(...warnings.map((warning) => {
    const item = document.createElement('li')
    item.textContent = warning
    return item
  }))
  captureWarnings.hidden = warnings.length === 0
}

function renderCaptureResult(message, type) {
  captureResult.textContent = message
  captureResult.className = `capture-result ${type}`.trim()
}

function renderHealthError(message) {
  statusCard.className = 'status-card error'
  statusTitle.textContent = '连接失败'
  statusDetail.textContent = message
}

function explainTabError(error) {
  const message = error instanceof Error ? error.message : String(error || '')
  if (message.includes('Receiving end does not exist')) return '请打开或刷新小红书帖子或“我的收藏”页面'
  return message || '请打开小红书帖子或“我的收藏”页面'
}
