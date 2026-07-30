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

async function requestJson(url, options = {}) {
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), 5000)

  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
    })
    if (!response.ok) {
      const errorBody = await response.json().catch(() => null)
      throw new Error(errorBody?.message || `后端返回 ${response.status}`)
    }
    return await response.json()
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
