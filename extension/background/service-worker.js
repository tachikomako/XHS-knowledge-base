import { createImportSummary, mergeImportResult, splitImportBatches } from './batch-core.js'
import { collectAccessContextsFromPageState } from '../content/access-context.js'

const DEFAULT_SETTINGS = Object.freeze({
  backendUrl: 'http://127.0.0.1:8080',
  knowledgeBaseUrl: 'http://127.0.0.1:5173',
  extensionToken: '',
})

chrome.runtime.onInstalled.addListener(async () => {
  const saved = await chrome.storage.local.get(Object.keys(DEFAULT_SETTINGS))
  await chrome.storage.local.set({ ...DEFAULT_SETTINGS, ...saved })
})

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  const operation = message?.type === 'CHECK_HEALTH'
    ? checkHealth()
    : message?.type === 'IMPORT_XHS_ITEMS'
      ? importItems(message.payload)
      : message?.type === 'START_MANUAL_SYNC'
        ? startManualSync(message.payload)
        : message?.type === 'GET_LATEST_SYNC_RUN'
          ? latestSyncRun()
          : message?.type === 'READ_XHS_ACCESS_CONTEXT'
            ? readAccessContexts(sender)
            : null

  if (!operation) return false

  operation
    .then(sendResponse)
    .catch((error) => sendResponse({
      ok: false,
      error: error instanceof Error ? error.message : '无法连接后端',
    }))

  return true
})

async function readAccessContexts(sender) {
  if (!sender.tab?.id) return { ok: false, accessContexts: [] }
  const [{ result = [] } = {}] = await chrome.scripting.executeScript({
    target: { tabId: sender.tab.id },
    world: 'MAIN',
    func: collectAccessContextsFromPageState,
  })
  return { ok: true, accessContexts: result }
}

async function checkHealth() {
  const settings = await chrome.storage.local.get(Object.keys(DEFAULT_SETTINGS))
  const backendUrl = normalizeBaseUrl(settings.backendUrl || DEFAULT_SETTINGS.backendUrl)
  return requestJson(`${backendUrl}/api/v1/health`, { headers: { Accept: 'application/json' } })
    .then((health) => ({ ok: true, health }))
}

async function importItems(payload) {
  const items = Array.isArray(payload?.items) ? payload.items : payload?.item ? [payload.item] : []
  const captureMode = payload?.captureMode || (items.length === 1 ? 'CURRENT_POST' : 'FAVORITES_PAGE')
  if (!payload?.extractorVersion) {
    throw new Error('剪藏数据不完整，请刷新帖子页面后重试')
  }
  let batches
  try {
    batches = splitImportBatches(items)
  } catch {
    throw new Error('剪藏数据必须包含 1 到 500 条内容')
  }
  if (!['CURRENT_POST', 'FAVORITES_PAGE'].includes(captureMode)) {
    throw new Error('不支持的剪藏模式')
  }

  const settings = await chrome.storage.local.get(Object.keys(DEFAULT_SETTINGS))
  const backendUrl = normalizeBaseUrl(settings.backendUrl || DEFAULT_SETTINGS.backendUrl)
  const extensionToken = String(settings.extensionToken || '').trim()
  if (!extensionToken) {
    throw new Error('请先在设置中填写与后端一致的本地访问令牌')
  }

  const result = createImportSummary()
  for (const chunk of batches) {
    const batch = await requestJson(`${backendUrl}/api/v1/imports/xiaohongshu`, {
      method: 'POST',
      timeoutMs: 30000,
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-Extension-Token': extensionToken,
      },
      body: JSON.stringify({
        clientBatchId: crypto.randomUUID(),
        captureMode,
        extractorVersion: payload.extractorVersion,
        items: chunk,
      }),
    })
    mergeImportResult(result, batch)
  }
  return { ok: true, result }
}

async function startManualSync(payload) {
  const requestedSources = normalizeSources(payload?.sources)
  if (requestedSources.length === 0) throw new Error('请至少选择收藏或点赞')

  const settings = await chrome.storage.local.get(Object.keys(DEFAULT_SETTINGS))
  const backendUrl = normalizeBaseUrl(settings.backendUrl || DEFAULT_SETTINGS.backendUrl)
  const extensionToken = String(settings.extensionToken || '').trim()
  if (!extensionToken) throw new Error('请先在设置中填写与后端一致的本地访问令牌')

  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
  if (!tab?.id || !tab.url) throw new Error('无法读取当前标签页')

  const syncRun = await createSyncRun(backendUrl, extensionToken, requestedSources)
  const summary = createImportSummary()
  const errors = []
  let discoveredCount = 0
  let contentCompleted = 0
  let contentFailed = 0

  for (const source of requestedSources) {
    try {
      const targetUrl = profileTabUrl(tab.url, source)
      await navigateAndWait(tab.id, targetUrl)
      const discovery = await sendTabMessage(tab.id, { type: 'DISCOVER_XHS_LIST', source })
      if (!discovery?.ok) throw new Error(discovery?.error || '列表发现失败')
      const items = (discovery.items || []).map((item) => ({ ...item, sourceRelation: source }))
      discoveredCount += items.length
      if (items.length === 0) {
        errors.push(sourceLabel(source) + '没有发现可同步帖子')
        continue
      }
      const imported = await importItems({
        captureMode: 'FAVORITES_PAGE',
        extractorVersion: discovery.extractorVersion,
        items,
      })
      mergeImportResult(summary, imported.result)
      const completion = await completeMissingContent(tab.id, items, imported.result, discovery.extractorVersion)
      contentCompleted += completion.completed
      contentFailed += completion.failed
      for (const error of completion.errors) errors.push(`${sourceLabel(source)}正文：${error}`)
      for (const warning of discovery.warnings || []) errors.push(`${sourceLabel(source)}：${warning}`)
    } catch (error) {
      errors.push(`${sourceLabel(source)}：${error instanceof Error ? error.message : '同步失败'}`)
    }
  }

  const failedSources = errors.filter((error) => !error.includes('重复卡片')).length
  const status = summary.received === 0
    ? 'FAILED'
    : failedSources > 0 || summary.failed > 0 || contentFailed > 0
      ? 'PARTIAL_FAILED'
      : 'COMPLETED'
  const updated = await updateSyncRun(backendUrl, extensionToken, syncRun.id, {
    status,
    discoveredCount,
    processedCount: summary.received,
    createdCount: summary.created,
    updatedCount: summary.updated,
    unchangedCount: summary.skipped,
    contentCompletedCount: contentCompleted,
    contentFailedCount: contentFailed,
    aiCompletedCount: 0,
    aiFailedCount: 0,
    errorSummary: errors.slice(0, 5).join('；') || null,
  })
  summary.contentCompleted = contentCompleted
  summary.contentFailed = contentFailed
  return { ok: status !== 'FAILED', result: summary, syncRun: updated, errors }
}

async function completeMissingContent(tabId, discoveredItems, importResult, extractorVersion) {
  const bySourceId = new Map(discoveredItems.map((item) => [item.sourceItemId, item]))
  const candidates = (importResult.results || [])
    .filter((result) => result.itemId && ['DISCOVERED', 'FAILED'].includes(result.contentStatus))
    .map((result) => bySourceId.get(result.sourceItemId))
    .filter(Boolean)
  const errors = []
  let completed = 0
  let failed = 0

  for (const item of candidates) {
    try {
      await navigateAndWait(tabId, item.url)
      const detail = await sendTabMessage(tabId, { type: 'INSPECT_XHS_PAGE' })
      if (!detail?.ok || !detail.item) throw new Error(detail?.error || '正文提取失败')
      const detailItem = {
        ...detail.item,
        sourceRelation: item.sourceRelation,
        contentStatus: detail.item.text ? 'COMPLETED' : 'FAILED',
        contentLastError: detail.item.text ? null : '详情页未识别到正文',
      }
      await importItems({ captureMode: 'CURRENT_POST', extractorVersion: detail.extractorVersion, items: [detailItem] })
      if (detailItem.contentStatus === 'COMPLETED') completed++
      else failed++
    } catch (error) {
      failed++
      const message = error instanceof Error ? error.message : '正文提取失败'
      errors.push(`${item.title || item.sourceItemId || item.url}：${message}`)
      await importItems({
        captureMode: 'CURRENT_POST',
        extractorVersion,
        items: [{
          ...item,
          text: null,
          contentStatus: 'FAILED',
          contentLastError: message,
          captureLevel: 'CARD',
        }],
      }).catch(() => null)
    }
  }

  return { completed, failed, errors }
}

async function createSyncRun(backendUrl, extensionToken, sources) {
  return requestJson(`${backendUrl}/api/v1/sync-runs`, {
    method: 'POST',
    headers: syncHeaders(extensionToken),
    body: JSON.stringify({ requestedSources: sources }),
  })
}

async function updateSyncRun(backendUrl, extensionToken, id, body) {
  return requestJson(`${backendUrl}/api/v1/sync-runs/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    headers: syncHeaders(extensionToken),
    body: JSON.stringify(body),
  })
}

async function latestSyncRun() {
  const settings = await chrome.storage.local.get(Object.keys(DEFAULT_SETTINGS))
  const backendUrl = normalizeBaseUrl(settings.backendUrl || DEFAULT_SETTINGS.backendUrl)
  const run = await requestJson(`${backendUrl}/api/v1/sync-runs/latest`, { headers: { Accept: 'application/json' } })
  return { ok: true, run }
}

async function requestJson(url, options = {}) {
  const { timeoutMs = 5000, ...fetchOptions } = options
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs)

  try {
    const response = await fetch(url, {
      ...fetchOptions,
      signal: controller.signal,
    })
    if (!response.ok) {
      const errorBody = await response.json().catch(() => null)
      throw new Error(errorBody?.message || `后端返回 ${response.status}`)
    }
    const text = await response.text()
    return text ? JSON.parse(text) : null
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

function normalizeSources(value) {
  const sources = Array.isArray(value) ? value : []
  return [...new Set(sources.filter((source) => ['FAVORITE', 'LIKED'].includes(source)))]
}

function profileTabUrl(currentUrl, source) {
  const url = new URL(currentUrl)
  if (url.hostname !== 'www.xiaohongshu.com' || !url.pathname.startsWith('/user/profile/')) {
    throw new Error('请先打开小红书个人主页')
  }
  url.searchParams.set('tab', source === 'LIKED' ? 'liked' : 'fav')
  url.searchParams.set('subTab', 'note')
  url.hash = ''
  return url.toString()
}

function navigateAndWait(tabId, url) {
  return new Promise((resolve, reject) => {
    const timeoutId = setTimeout(() => {
      chrome.tabs.onUpdated.removeListener(listener)
      reject(new Error('页面加载超时'))
    }, 30000)
    const listener = (updatedTabId, changeInfo) => {
      if (updatedTabId === tabId && changeInfo.status === 'complete') {
        clearTimeout(timeoutId)
        chrome.tabs.onUpdated.removeListener(listener)
        setTimeout(resolve, 1200)
      }
    }
    chrome.tabs.onUpdated.addListener(listener)
    chrome.tabs.update(tabId, { url }).catch((error) => {
      clearTimeout(timeoutId)
      chrome.tabs.onUpdated.removeListener(listener)
      reject(error)
    })
  })
}

async function sendTabMessage(tabId, message) {
  try {
    return await chrome.tabs.sendMessage(tabId, message)
  } catch (error) {
    if (!(error instanceof Error) || !error.message.includes('Receiving end does not exist')) throw error
    await chrome.scripting.executeScript({
      target: { tabId },
      files: ['content/content-script.js'],
    })
    return chrome.tabs.sendMessage(tabId, message)
  }
}

function syncHeaders(extensionToken) {
  return {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    'X-Extension-Token': extensionToken,
  }
}

function sourceLabel(source) {
  return source === 'LIKED' ? '点赞' : '收藏'
}
