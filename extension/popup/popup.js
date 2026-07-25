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
const clipButton = document.querySelector('#clipCurrentPost')
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

clipButton.addEventListener('click', async () => {
  if (!currentExtraction?.item) return
  clipButton.disabled = true
  clipButton.textContent = '正在剪藏…'
  renderCaptureResult('', '')

  try {
    const response = await chrome.runtime.sendMessage({
      type: 'IMPORT_XHS_ITEMS',
      payload: currentExtraction,
    })
    if (!response?.ok) throw new Error(response?.error || '剪藏失败')

    const { created, updated, skipped, failed } = response.result
    if (failed) throw new Error(response.result.results?.[0]?.error || '后端未能保存这篇帖子')
    const action = created ? '已保存到知识库' : updated ? '已更新知识副本' : skipped ? '知识库中已存在' : '处理完成'
    renderCaptureResult(action, 'success')
  } catch (error) {
    renderCaptureResult(error instanceof Error ? error.message : '剪藏失败', 'error')
  } finally {
    clipButton.disabled = false
    clipButton.textContent = '剪藏当前帖子'
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
    const response = await chrome.tabs.sendMessage(tab.id, { type: 'EXTRACT_CURRENT_POST' })
    if (!response?.ok) throw new Error(response?.error || '当前页面无法剪藏')

    currentExtraction = {
      extractorVersion: response.extractorVersion,
      item: response.item,
    }
    captureTitle.textContent = response.item.title
    captureMeta.textContent = [
      response.item.author || '作者未知',
      response.item.captureLevel === 'DETAIL' ? '正文快照' : '链接卡片',
      response.item.imageUrls.length ? `${response.item.imageUrls.length} 张图片` : null,
    ].filter(Boolean).join(' · ')
    renderWarnings(response.warnings || [])
    clipButton.disabled = false
  } catch (error) {
    captureTitle.textContent = '当前页面不可剪藏'
    captureMeta.textContent = explainTabError(error)
    renderWarnings([])
    clipButton.disabled = true
  }
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
  if (message.includes('Receiving end does not exist')) return '请打开或刷新一个小红书帖子页面'
  return message || '请打开一个小红书帖子页面'
}
