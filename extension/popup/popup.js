import { withTimeout } from './popup-core.js'
import { setupBeginnerGuide } from './beginner-guide.js'

const EXTENSION_BUILD = 'ae649ff'
const DEFAULT_SETTINGS = Object.freeze({
  backendUrl: 'http://127.0.0.1:8080',
  knowledgeBaseUrl: 'http://127.0.0.1:5173',
  extensionToken: 'dev-local-token',
})

const form = document.querySelector('#settingsForm')
const submitButton = form.querySelector('button[type="submit"]')
const statusCard = document.querySelector('#statusCard')
const statusTitle = document.querySelector('#statusTitle')
const statusDetail = document.querySelector('#statusDetail')
const openButton = document.querySelector('#openKnowledgeBase')
const captureButton = document.querySelector('#captureButton')
const startSyncButton = document.querySelector('#startSyncButton')
const syncRescanButton = document.querySelector('#syncRescanButton')
const stopContentButton = document.querySelector('#stopContentButton')
const completeFavoriteContent = document.querySelector('#completeFavoriteContent')
const syncResult = document.querySelector('#syncResult')
const latestSync = document.querySelector('#latestSync')
const captureTitle = document.querySelector('#captureTitle')
const captureMeta = document.querySelector('#captureMeta')
const captureWarnings = document.querySelector('#captureWarnings')
const captureResult = document.querySelector('#captureResult')
const rescanButton = document.querySelector('#rescanButton')
const diagnosticPanel = document.querySelector('#diagnosticPanel')
const diagnosticText = document.querySelector('#diagnosticText')
const copyDiagnostics = document.querySelector('#copyDiagnostics')
const extensionBuild = document.querySelector('#extensionBuild')

setupBeginnerGuide()

let currentExtraction = null

extensionBuild.textContent = `Build ${EXTENSION_BUILD}`
await loadSettings()
await Promise.all([checkHealth(), inspectCurrentPage(), loadLatestSyncRun(), loadContentCompletionState()])

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
    if (currentExtraction.captureMode === 'FAVORITES_PAGE') {
      renderCaptureResult('列表页请使用上方手动同步入口', 'error')
      return
    }
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

startSyncButton.addEventListener('click', async () => {
  const sources = selectedSources()
  if (sources.length === 0) {
    renderSyncResult('请打开小红书个人主页后同步收藏', 'error')
    return
  }
  if (completeFavoriteContent.checked && sources.includes('FAVORITE')) {
    const confirmed = window.confirm('补全正文需要逐篇打开收藏帖子，耗时较长。\n收藏正文预计需要几分钟，图文帖子通常较快，视频帖子通常较慢，实际时间取决于网络和页面加载速度。同步过程中可以点击“停止补全正文”，已完成的内容会保留。')
    if (!confirmed) return
  }
  startSyncButton.disabled = true
  startSyncButton.setAttribute('aria-busy', 'true')
  startSyncButton.textContent = '同步中…'
  renderSyncResult('正在遍历所选页面', '')
  try {
    const response = await performManualSync(sources)
    renderManualSyncResult(response)
  } catch (error) {
    renderSyncResult(error instanceof Error ? error.message : '同步失败', 'error')
    await loadLatestSyncRun()
  } finally {
    startSyncButton.disabled = false
    startSyncButton.removeAttribute('aria-busy')
    startSyncButton.textContent = '开始同步'
    await loadContentCompletionState()
  }
})

stopContentButton.addEventListener('click', async () => {
  stopContentButton.disabled = true
  stopContentButton.setAttribute('aria-busy', 'true')
  stopContentButton.textContent = '正在停止…'
  try {
    const response = await chrome.runtime.sendMessage({ type: 'CANCEL_CONTENT_COMPLETION' })
    if (!response?.ok) throw new Error(response?.error || '停止正文补全失败')
    renderSyncResult('已请求停止正文补全，当前已完成内容会保留', 'success')
  } catch (error) {
    renderSyncResult(error instanceof Error ? error.message : '停止正文补全失败', 'error')
  } finally {
    stopContentButton.removeAttribute('aria-busy')
    await loadContentCompletionState()
  }
})

syncRescanButton.addEventListener('click', scanBatchPanel)
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
      ].filter(Boolean).join(' · ')
    } else if (response.pageType === 'FAVORITE') {
      currentExtraction = null
      captureTitle.textContent = `识别到 ${response.stats.candidates} 条收藏卡片`
      captureMeta.textContent = response.stats.candidates
        ? `可同步 ${response.items.length} 条 · 缺少访问参数 ${response.stats.missingTokenCount} 条；向下滚动后可加载更多`
        : '请确认已进入“收藏”标签，并先向下滚动加载内容'
      renderDiagnostics(response.stats, response.extractorVersion, 'FAVORITES_PAGE')
      captureButton.textContent = '使用上方手动同步'
      captureButton.disabled = true
    } else if (response.pageType === 'FEED') {
      currentExtraction = null
      captureTitle.textContent = response.postCount
        ? `识别到 ${response.postCount} 条帖子`
        : '已识别小红书页面'
      captureMeta.textContent = response.postCount
        ? '当前为普通信息流，仅展示识别结果；不会同步主页或信息流'
        : '当前页面暂未加载帖子，可滚动页面后重新扫描'
      captureButton.textContent = '当前页面仅识别'
    } else if (response.pageType === 'LIKED') {
      currentExtraction = null
      captureTitle.textContent = '当前页面不再支持同步'
      captureMeta.textContent = '请打开个人主页的“收藏”标签后同步'
      captureButton.textContent = '仅支持收藏同步'
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

async function scanBatchPanel() {
  syncRescanButton.disabled = true
  syncRescanButton.setAttribute('aria-busy', 'true')
  syncRescanButton.textContent = '扫描中…'
  renderSyncResult('正在重新读取当前小红书页面…', '')
  try {
    const response = await withTimeout(inspectActiveTab(), 10_000)
    if (!response?.ok) throw new Error(response?.error || '页面识别失败')
    if (response.pageType === 'FAVORITE') {
      renderSyncResult(`已识别收藏页面，当前发现 ${response.stats?.extracted ?? response.postCount ?? 0} 篇帖子`, 'success')
      renderDiagnostics(response.stats || {}, response.extractorVersion, 'FAVORITES_PAGE')
      return
    }
    if (response.pageType === 'CURRENT_POST') {
      renderSyncResult('已识别单篇帖子；批量同步请打开个人主页的收藏页面', 'error')
      return
    }
    renderSyncResult(response.reason || '未识别到收藏列表，请打开个人主页收藏标签后重试', 'error')
  } catch (error) {
    renderSyncResult(error instanceof Error ? error.message : '重新扫描失败', 'error')
  } finally {
    syncRescanButton.disabled = false
    syncRescanButton.removeAttribute('aria-busy')
    syncRescanButton.textContent = '重新扫描'
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

function renderDiagnostics(stats, extractorVersion = currentExtraction?.extractorVersion, pageType = currentExtraction?.captureMode) {
  diagnosticText.value = JSON.stringify({
    extensionBuild: EXTENSION_BUILD,
    extensionVersion: chrome.runtime.getManifest().version,
    extractorVersion,
    pageType,
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

async function performManualSync(sources) {
  const response = await chrome.runtime.sendMessage({
    type: 'START_MANUAL_SYNC',
    payload: {
      sources,
      completeFavoriteContent: completeFavoriteContent.checked && sources.includes('FAVORITE'),
      extensionBuild: EXTENSION_BUILD,
    },
  })
  if (!response?.ok) throw new Error(response?.error || response?.errors?.[0] || '同步失败')
  return response
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

function renderManualSyncResult(response) {
  const run = response.syncRun
  const result = response.result
  const contentText = response.contentCancelled
    ? '正文补全已由用户中断'
    : response.contentRequested || result.contentRequested
      ? `正文 ${run.contentCompletedCount}/${run.contentCompletedCount + run.contentFailedCount}`
    : '正文：未请求'
  const message = [
    `发现 ${run.discoveredCount}`,
    `处理 ${run.processedCount}`,
    `新增 ${result.created}`,
    `更新 ${result.updated}`,
    `未变 ${result.skipped}`,
    contentText,
    `失败 ${result.failed}`,
  ].join(' · ')
  renderSyncResult(message, run.status === 'COMPLETED' ? 'success' : 'error')
  renderLatestSync(run)
}

async function loadLatestSyncRun() {
  try {
    const response = await chrome.runtime.sendMessage({ type: 'GET_LATEST_SYNC_RUN' })
    if (response?.ok) renderLatestSync(response.run)
  } catch {
    latestSync.textContent = '最近同步：暂无记录'
  }
}

async function loadContentCompletionState() {
  try {
    const response = await chrome.runtime.sendMessage({ type: 'GET_CONTENT_COMPLETION_STATE' })
    const active = response?.ok && response.state?.status === 'RUNNING'
    stopContentButton.hidden = !active
    if (!active) {
      stopContentButton.disabled = false
      stopContentButton.textContent = '停止补全正文'
    }
  } catch {
    stopContentButton.hidden = true
  }
}

function renderLatestSync(run) {
  if (!run) {
    latestSync.textContent = '最近同步：暂无记录'
    return
  }
  latestSync.textContent = [
    `最近同步：${syncStatusLabel(run.status)}`,
    `发现 ${run.discoveredCount}`,
    `新增 ${run.createdCount}`,
    `更新 ${run.updatedCount}`,
    `未变 ${run.unchangedCount}`,
    run.errorSummary?.includes('正文补全由用户中断')
      ? '正文补全已由用户中断'
      : run.contentCompletedCount + run.contentFailedCount > 0
        ? `正文 ${run.contentCompletedCount}/${run.contentCompletedCount + run.contentFailedCount}`
        : '正文：未请求',
  ].filter(Boolean).join(' · ')
}

function syncStatusLabel(status) {
  return {
    RUNNING: '进行中',
    COMPLETED: '已完成',
    PARTIAL_FAILED: '部分失败',
    FAILED: '失败',
  }[status] || status
}

function renderSyncResult(message, type) {
  syncResult.textContent = message
  syncResult.className = `capture-result ${type}`.trim()
}

function selectedSources() {
  return ['FAVORITE']
}

function updateSyncButton() {
  startSyncButton.disabled = selectedSources().length === 0
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
  const settings = withDefaultToken({ ...DEFAULT_SETTINGS, ...saved })
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
    extensionToken: String(data.get('extensionToken') || '').trim() || DEFAULT_SETTINGS.extensionToken,
  }
}

function withDefaultToken(settings) {
  return {
    ...settings,
    extensionToken: String(settings.extensionToken || '').trim() || DEFAULT_SETTINGS.extensionToken,
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
