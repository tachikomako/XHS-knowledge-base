import { createImportSummary, mergeImportResult, splitImportBatches } from './batch-core.js'
import {
  buildContentCandidates,
  hasCompletableContentResults,
  splitContentBatches,
  summarizeContentFailure,
} from './sync-core.js'
import { collectAccessContextsFromPageState } from '../content/access-context.js'

const DEFAULT_SETTINGS = Object.freeze({
  backendUrl: 'http://127.0.0.1:8080',
  knowledgeBaseUrl: 'http://127.0.0.1:5173',
  extensionToken: 'dev-local-token',
})
const CONTENT_COMPLETION_STATE_KEY = 'contentCompletionState'
const CONTENT_COMPLETION_CANCEL_PREFIX = 'contentCompletionCancel:'

chrome.runtime.onInstalled.addListener(async () => {
  const saved = await chrome.storage.local.get(Object.keys(DEFAULT_SETTINGS))
  await chrome.storage.local.set(withDefaultToken({ ...DEFAULT_SETTINGS, ...saved }))
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
          : message?.type === 'GET_CONTENT_COMPLETION_STATE'
            ? contentCompletionState()
            : message?.type === 'CANCEL_CONTENT_COMPLETION'
              ? cancelContentCompletion(message.payload?.syncRunId)
          : message?.type === 'READ_XHS_ACCESS_CONTEXT'
            ? readAccessContexts(sender)
            : null

  if (!operation) return false

  operation
    .then(sendResponse)
    .catch((error) => sendResponse({
      ok: false,
      error: summarizeContentFailure(error),
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
    throw new Error('采集数据不完整，请刷新页面后重试')
  }
  let batches
  try {
    batches = splitImportBatches(items)
  } catch {
    throw new Error('采集数据必须包含 1 到 500 条内容')
  }
  if (!['CURRENT_POST', 'FAVORITES_PAGE'].includes(captureMode)) {
    throw new Error('不支持的采集模式')
  }

  const settings = await chrome.storage.local.get(Object.keys(DEFAULT_SETTINGS))
  const backendUrl = normalizeBaseUrl(settings.backendUrl || DEFAULT_SETTINGS.backendUrl)
  const extensionToken = String(settings.extensionToken || DEFAULT_SETTINGS.extensionToken).trim()
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
  if (requestedSources.length === 0) throw new Error('请至少选择收藏')

  const settings = await chrome.storage.local.get(Object.keys(DEFAULT_SETTINGS))
  const backendUrl = normalizeBaseUrl(settings.backendUrl || DEFAULT_SETTINGS.backendUrl)
  const extensionToken = String(settings.extensionToken || DEFAULT_SETTINGS.extensionToken).trim()
  if (!extensionToken) throw new Error('请先在设置中填写与后端一致的本地访问令牌')

  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
  if (!tab?.id || !tab.url) throw new Error('无法读取当前标签页')

  const syncRun = await createSyncRun(backendUrl, extensionToken, requestedSources)
  const summary = createImportSummary()
  const errors = []
  const completeFavoriteContent = requestedSources.includes('FAVORITE') && Boolean(payload?.completeFavoriteContent)
  if (completeFavoriteContent) await clearContentCompletionState(syncRun.id)
  const diagnostics = {
    build: payload?.extensionBuild || 'unknown',
    started: 1,
    contentRequested: completeFavoriteContent,
    cardsImported: 0,
    contentCandidates: 0,
    detailPagesOpened: 0,
    contentExtracted: 0,
    detailImportsSucceeded: 0,
    contentFailed: 0,
  }
  let discoveredCount = 0
  let contentCompleted = 0
  let contentFailed = 0
  let contentCancelled = false
  await updateSyncRun(backendUrl, extensionToken, syncRun.id, {
    status: 'RUNNING',
    errorSummary: syncDiagnostics(diagnostics, errors),
  })

  for (const source of requestedSources) {
    try {
      if (completeFavoriteContent && await isContentCompletionCancelled(syncRun.id)) {
        contentCancelled = true
        errors.push('正文补全由用户中断')
        break
      }
      const targetUrl = profileTabUrl(tab.url, source)
      await navigateAndWait(tab.id, targetUrl)
      const discovery = await sendTabMessage(tab.id, { type: 'DISCOVER_XHS_LIST', source })
      if (!discovery?.ok) throw new Error(discovery?.error || '列表发现失败')

      const items = (discovery.items || []).map((item) => ({ ...item, sourceRelation: source }))
      discoveredCount += items.length
      if (items.length === 0) {
        errors.push(`${sourceLabel(source)}没有发现可同步笔记`)
        continue
      }

      const imported = await importItems({
        captureMode: 'FAVORITES_PAGE',
        extractorVersion: discovery.extractorVersion,
        items,
      })
      mergeImportResult(summary, imported.result)
      diagnostics.cardsImported = summary.received

      if (source === 'FAVORITE' && completeFavoriteContent) {
        await chrome.storage.local.set({
          [CONTENT_COMPLETION_STATE_KEY]: { syncRunId: syncRun.id, status: 'RUNNING' },
        })
        const completedBeforeSource = contentCompleted
        const failedBeforeSource = contentFailed
        const completion = await completeMissingContent(tab.id, items, imported.result, discovery.extractorVersion, {
          shouldCancel: () => isContentCompletionCancelled(syncRun.id),
          onProgress: async (progress) => {
            try {
              Object.assign(diagnostics, progress.diagnostics)
              await updateSyncRun(backendUrl, extensionToken, syncRun.id, {
                status: 'RUNNING',
                discoveredCount,
                processedCount: summary.received,
                createdCount: summary.created,
                updatedCount: summary.updated,
                unchangedCount: summary.skipped,
                contentCompletedCount: completedBeforeSource + progress.completed,
                contentFailedCount: failedBeforeSource + progress.failed,
                aiCompletedCount: 0,
                aiFailedCount: 0,
                errorSummary: syncDiagnostics(diagnostics, [...errors, ...progress.errors]),
              })
            } catch (error) {
              errors.push(`${sourceLabel(source)}正文进度回写失败：${summarizeContentFailure(error)}`)
            }
          },
        })
        Object.assign(diagnostics, completion.diagnostics)
        contentCompleted += completion.completed
        contentFailed += completion.failed
        contentCancelled = completion.cancelled
        diagnostics.contentFailed = contentFailed
        for (const error of completion.errors) errors.push(`${sourceLabel(source)}正文：${error}`)
        if (contentCancelled) {
          errors.push('正文补全由用户中断')
          break
        }
      }
      for (const warning of discovery.warnings || []) errors.push(`${sourceLabel(source)}：${warning}`)
    } catch (error) {
      errors.push(`${sourceLabel(source)}：${summarizeContentFailure(error)}`)
    }
  }

  const failedSources = errors.filter((error) => !error.includes('重复卡片')).length
  const status = summary.received === 0
    ? 'FAILED'
    : contentCancelled || failedSources > 0 || summary.failed > 0 || contentFailed > 0
      ? 'PARTIAL_FAILED'
      : 'COMPLETED'
  let updated
  try {
    updated = await updateSyncRun(backendUrl, extensionToken, syncRun.id, {
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
      errorSummary: syncDiagnostics(diagnostics, errors),
    })
  } finally {
    if (completeFavoriteContent) await clearContentCompletionState(syncRun.id)
  }
  summary.contentCompleted = contentCompleted
  summary.contentFailed = contentFailed
  summary.contentRequested = completeFavoriteContent
  return { ok: status !== 'FAILED', result: summary, syncRun: updated, errors, contentRequested: completeFavoriteContent, contentCancelled }
}

async function completeMissingContent(tabId, discoveredItems, importResult, extractorVersion, options = {}) {
  const candidates = buildContentCandidates(discoveredItems, importResult)
  const diagnostics = {
    contentCandidates: candidates.length,
    detailPagesOpened: 0,
    contentExtracted: 0,
    detailImportsSucceeded: 0,
  }
  const errors = []
  let completed = 0
  let failed = 0
  let cancelled = false

  if (candidates.length === 0 && hasCompletableContentResults(importResult)) {
    errors.push('正文补全未启动：导入结果没有匹配到可补全条目')
    return { completed, failed, errors, diagnostics, cancelled }
  }

  for (const batch of splitContentBatches(candidates, 5)) {
    for (const item of batch) {
      if (await options.shouldCancel?.()) {
        cancelled = true
        break
      }
      try {
        await navigateAndWait(tabId, item.url, { shouldCancel: options.shouldCancel })
        if (await options.shouldCancel?.()) throw new ContentCompletionCancelledError()
        diagnostics.detailPagesOpened++
        const detail = await awaitWithCancellation(
          sendTabMessage(tabId, { type: 'INSPECT_XHS_PAGE' }),
          options.shouldCancel,
        )
        if (!detail?.ok || !detail.item) {
          throw new Error(detail?.error || `详情页 content script 未注入或无响应: ${detail?.code || 'NO_DETAIL_ITEM'}`)
        }
        if (await options.shouldCancel?.()) throw new ContentCompletionCancelledError()
        if (detail.item.text) diagnostics.contentExtracted++
        const detailItem = {
          ...detail.item,
          sourceRelation: item.sourceRelation,
          contentStatus: detail.item.text ? 'COMPLETED' : 'FAILED',
          contentLastError: detail.item.text ? null : '详情页未识别到正文',
        }
        await importItems({ captureMode: 'CURRENT_POST', extractorVersion: detail.extractorVersion, items: [detailItem] })
        diagnostics.detailImportsSucceeded++
        if (detailItem.contentStatus === 'COMPLETED') {
          completed++
        } else {
          failed++
          const detailDiagnostic = detail.diagnostics
            ? `pageType=${detail.diagnostics.pageType}; noteId=${detail.diagnostics.noteId}; contentLength=${detail.diagnostics.contentLength}; errorCode=${detail.diagnostics.errorCode}`
            : 'no detail diagnostics'
          errors.push(`${contentCandidateLabel(item)}：详情页已打开，但正文提取结果为空 (${detailDiagnostic})`)
        }
      } catch (error) {
        if (error instanceof ContentCompletionCancelledError) {
          cancelled = true
          break
        }
        failed++
        const message = summarizeContentFailure(error)
        errors.push(`${contentCandidateLabel(item)}：${message}`)
        try {
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
          })
        } catch (writeError) {
          errors.push(`${contentCandidateLabel(item)}：失败状态回写失败：${summarizeContentFailure(writeError)}`)
        }
      }
    }
    if (typeof options.onProgress === 'function') {
      await options.onProgress({ completed, failed, errors: [...errors], diagnostics: { ...diagnostics } })
    }
    if (cancelled) break
  }

  return { completed, failed, errors, diagnostics, cancelled }
}

class ContentCompletionCancelledError extends Error {
  constructor() {
    super('Content completion cancelled by user')
  }
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

function contentCompletionCancelKey(syncRunId) {
  return `${CONTENT_COMPLETION_CANCEL_PREFIX}${syncRunId}`
}

async function isContentCompletionCancelled(syncRunId) {
  const key = contentCompletionCancelKey(syncRunId)
  const stored = await chrome.storage.local.get([key])
  return stored[key] === true
}

async function clearContentCompletionState(syncRunId) {
  await chrome.storage.local.remove(contentCompletionCancelKey(syncRunId))
  const stored = await chrome.storage.local.get([CONTENT_COMPLETION_STATE_KEY])
  if (!syncRunId || stored[CONTENT_COMPLETION_STATE_KEY]?.syncRunId === syncRunId) {
    await chrome.storage.local.remove(CONTENT_COMPLETION_STATE_KEY)
  }
}

async function contentCompletionState() {
  const stored = await chrome.storage.local.get([CONTENT_COMPLETION_STATE_KEY])
  return { ok: true, state: stored[CONTENT_COMPLETION_STATE_KEY] || null }
}

async function cancelContentCompletion(syncRunId) {
  const stored = await chrome.storage.local.get([CONTENT_COMPLETION_STATE_KEY])
  const activeSyncRunId = syncRunId || stored[CONTENT_COMPLETION_STATE_KEY]?.syncRunId
  if (!activeSyncRunId) return { ok: false, error: '当前没有正在运行的正文补全' }
  await chrome.storage.local.set({ [contentCompletionCancelKey(activeSyncRunId)]: true })
  return { ok: true, syncRunId: activeSyncRunId }
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

function withDefaultToken(settings) {
  return {
    ...settings,
    extensionToken: String(settings.extensionToken || '').trim() || DEFAULT_SETTINGS.extensionToken,
  }
}

function normalizeSources(value) {
  const sources = Array.isArray(value) ? value : []
  return [...new Set(sources.filter((source) => source === 'FAVORITE'))]
}

function profileTabUrl(currentUrl) {
  const url = new URL(currentUrl)
  if (url.hostname !== 'www.xiaohongshu.com' || !url.pathname.startsWith('/user/profile/')) {
    throw new Error('请先打开小红书个人主页')
  }
  url.searchParams.set('tab', 'fav')
  url.searchParams.set('subTab', 'note')
  url.hash = ''
  return url.toString()
}

function navigateAndWait(tabId, url, options = {}) {
  return new Promise((resolve, reject) => {
    let settled = false
    let cancelTimer = null
    const cleanup = () => {
      clearTimeout(timeoutId)
      if (cancelTimer !== null) clearTimeout(cancelTimer)
      chrome.tabs.onUpdated.removeListener(listener)
    }
    const finish = (callback, value) => {
      if (settled) return
      settled = true
      cleanup()
      callback(value)
    }
    const timeoutId = setTimeout(() => {
      finish(reject, new Error('页面加载超时'))
    }, 30000)
    const listener = (updatedTabId, changeInfo) => {
      if (updatedTabId === tabId && changeInfo.status === 'complete') {
        setTimeout(() => finish(resolve), 1200)
      }
    }
    const checkCancellation = async () => {
      try {
        if (await options.shouldCancel?.()) {
          finish(reject, new ContentCompletionCancelledError())
          return
        }
        cancelTimer = setTimeout(checkCancellation, 500)
      } catch (error) {
        finish(reject, error)
      }
    }
    chrome.tabs.onUpdated.addListener(listener)
    chrome.tabs.update(tabId, { url }).catch((error) => {
      finish(reject, error)
    })
    checkCancellation()
  })
}

async function awaitWithCancellation(promise, shouldCancel) {
  if (typeof shouldCancel !== 'function') return promise
  let timer = null
  let rejectCancellation
  const cancellation = new Promise((_, reject) => {
    rejectCancellation = reject
  })
  const checkCancellation = async () => {
    try {
      if (await shouldCancel()) {
        rejectCancellation(new ContentCompletionCancelledError())
        return
      }
      timer = setTimeout(checkCancellation, 500)
    } catch (error) {
      rejectCancellation(error)
    }
  }
  checkCancellation()
  try {
    return await Promise.race([promise, cancellation])
  } finally {
    if (timer !== null) clearTimeout(timer)
  }
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
  return source === 'FAVORITE' ? '收藏' : source
}

function contentCandidateLabel(item) {
  return item?.title || item?.sourceItemId || 'unknown item'
}

function syncDiagnostics(diagnostics, errors = []) {
  const parts = [
    `build=${diagnostics.build}`,
    'manual sync started',
    `content requested: ${diagnostics.contentRequested ? 'yes' : 'no'}`,
    `cards imported: ${diagnostics.cardsImported || 0}`,
    `content candidates: ${diagnostics.contentCandidates || 0}`,
    `detail pages opened: ${diagnostics.detailPagesOpened || 0}`,
    `content extracted: ${diagnostics.contentExtracted || 0}`,
    `detail imports succeeded: ${diagnostics.detailImportsSucceeded || 0}`,
    `content failed: ${diagnostics.contentFailed || 0}`,
  ]
  const lastError = errors.at(-1)
  if (lastError) parts.push(`last error: ${summarizeContentFailure(lastError)}`)
  return parts.join('; ').slice(0, 1000)
}
