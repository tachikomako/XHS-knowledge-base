import { withTimeout } from './popup-core.js'

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
const rescanButton = document.querySelector('#rescanButton')
const diagnosticPanel = document.querySelector('#diagnosticPanel')
const diagnosticText = document.querySelector('#diagnosticText')
const copyDiagnostics = document.querySelector('#copyDiagnostics')

let currentExtraction = null

await loadSettings()
await Promise.all([checkHealth(), inspectCurrentPage()])

form.addEventListener('submit', async (event) => {
  event.preventDefault()
  submitButton.disabled = true
  submitButton.setAttribute('aria-busy', 'true')
  submitButton.textContent = '检查中…'
  try {
    await chrome.storage.local.set(readFormSettings())
    await checkHealth()
  } catch (error) {
    renderHealthError(error instanceof Error ? error.message : '保存设置失败')
  } finally {
    submitButton.disabled = false
    submitButton.removeAttribute('aria-busy')
    submitButton.textContent = '保存并检查连接'
  }
})

captureButton.addEventListener('click', async () => {
  if (!currentExtraction?.items?.length) return
  captureButton.disabled = true
  captureButton.setAttribute('aria-busy', 'true')
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
    captureButton.removeAttribute('aria-busy')
    captureButton.textContent = captureButtonLabel()
  }
})

rescanButton.addEventListener('click', inspectCurrentPage)

copyDiagnostics.addEventListener('click', () => {
  diagnosticText.select()
  const copied = document.execCommand('copy')
  renderCaptureResult(copied ? '适配诊断已复制' : '请手动复制诊断文本', copied ? 'success' : 'error')
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
  captureButton.disabled = true
  rescanButton.disabled = true
  rescanButton.setAttribute('aria-busy', 'true')
  rescanButton.textContent = '识别中…'
  diagnosticPanel.hidden = true
  captureTitle.textContent = '正在重新扫描…'
  captureMeta.textContent = '只读取当前页面已经加载的内容'
  renderCaptureResult('', '')
  try {
    const response = await withTimeout(inspectActiveTab())
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
        response.item.imageUrls.length ? `${response.item.imageUrls.length} 张图片` : null,
      ].filter(Boolean).join(' · ')
    } else if (response.pageType === 'FAVORITE') {
      currentExtraction = {
        captureMode: 'FAVORITES_PAGE',
        extractorVersion: response.extractorVersion,
        items: response.items,
      }
      captureTitle.textContent = `识别到 ${response.stats.candidates} 条已加载收藏`
      captureMeta.textContent = response.stats.candidates
        ? `可同步 ${response.items.length} 条 · 缺少访问参数 ${response.stats.missingTokenCount} 条；向下滚动后可加载更多`
        : '请确认已进入“收藏”标签，并先向下滚动加载内容'
      renderDiagnostics(response.stats)
    } else if (response.pageType === 'FEED') {
      currentExtraction = null
      captureTitle.textContent = response.postCount
        ? `识别到 ${response.postCount} 条帖子`
        : '已识别小红书页面'
      captureMeta.textContent = response.postCount
        ? '当前为普通信息流，仅展示识别结果；不会同步点赞、主页或信息流'
        : '当前页面暂未加载帖子，可滚动页面后重新扫描'
      captureButton.textContent = '当前页面仅识别'
    } else if (response.pageType === 'LIKED') {
      currentExtraction = null
      captureTitle.textContent = '已识别点赞页面'
      captureMeta.textContent = `识别到 ${response.postCount} 条已加载帖子；当前版本不提供点赞同步`
      captureButton.textContent = '当前版本不提供点赞同步'
    } else {
      throw new Error('当前页面类型暂不支持')
    }

    renderWarnings(response.warnings || [])
    if (currentExtraction) {
      captureButton.textContent = captureButtonLabel()
      captureButton.disabled = currentExtraction.items.length === 0
    }
  } catch (error) {
    currentExtraction = null
    captureTitle.textContent = '当前页面不可剪藏'
    captureMeta.textContent = explainTabError(error)
    captureButton.textContent = '识别当前页面'
    renderWarnings([])
    captureButton.disabled = true
    diagnosticPanel.hidden = true
  } finally {
    rescanButton.disabled = false
    rescanButton.removeAttribute('aria-busy')
    rescanButton.textContent = '重新扫描'
  }
}

async function inspectActiveTab() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
  if (!tab?.id) throw new Error('无法读取当前标签页')
  try {
    return await chrome.tabs.sendMessage(tab.id, { type: 'INSPECT_XHS_PAGE' })
  } catch (error) {
    if (!isMissingContentScriptError(error)) throw error
    await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      files: ['content/content-script.js'],
    })
    return chrome.tabs.sendMessage(tab.id, { type: 'INSPECT_XHS_PAGE' })
  }
}

function isMissingContentScriptError(error) {
  return error instanceof Error && error.message.includes('Receiving end does not exist')
}

function renderDiagnostics(stats) {
  diagnosticText.value = JSON.stringify({
    extensionVersion: chrome.runtime.getManifest().version,
    extractorVersion: currentExtraction.extractorVersion,
    pageType: currentExtraction.captureMode,
    candidates: stats.candidates,
    extracted: stats.extracted,
    skipped: stats.skipped,
    duplicates: stats.duplicates,
    knownContainers: stats.knownContainers,
    postLinks: stats.postLinks,
    fallbackContainers: stats.fallbackContainers,
    fullUrlCount: stats.fullUrlCount,
    bareUrlCount: stats.bareUrlCount,
    stateTokenMatchCount: stats.stateTokenMatchCount,
    missingTokenCount: stats.missingTokenCount,
  }, null, 2)
  diagnosticPanel.hidden = false
}

function renderImportResult(result) {
  const restored = result.restored || 0
  const changed = result.created + result.updated + restored
  const message = `新增 ${result.created} · 更新 ${result.updated} · 恢复 ${restored} · 已存在 ${result.skipped}`
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
