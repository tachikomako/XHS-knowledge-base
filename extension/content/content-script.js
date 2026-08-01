let extractorPromise

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (!['INSPECT_XHS_PAGE', 'DISCOVER_XHS_LIST'].includes(message?.type)) return false

  getExtractor()
    .then(async ({ EXTRACTOR_VERSION, detectPage, extractCurrentPost, extractFavoritesPage, extractLikedPage }) => {
      if (message.type === 'DISCOVER_XHS_LIST') {
        return discoverList(message.source, { extractFavoritesPage, extractLikedPage })
      }
      const page = detectPage(window.location.href, document)
      if (page.pageType === 'CURRENT_POST') return extractCurrentPost(document, window.location.href)
      if (page.pageType === 'FAVORITE') {
        const response = await chrome.runtime.sendMessage({ type: 'READ_XHS_ACCESS_CONTEXT' }).catch(() => null)
        return extractFavoritesPage(document, window.location.href, new Date(), response?.accessContexts || [])
      }
      if (page.pageType === 'LIKED') {
        const response = await chrome.runtime.sendMessage({ type: 'READ_XHS_ACCESS_CONTEXT' }).catch(() => null)
        return extractLikedPage(document, window.location.href, new Date(), response?.accessContexts || [])
      }
      if (page.pageType === 'FEED') return { ...page, extractorVersion: EXTRACTOR_VERSION }
      throw Object.assign(new Error(page.reason), { code: 'UNSUPPORTED_PAGE' })
    })
    .then((result) => sendResponse({ ok: true, ...result }))
    .catch((error) => sendResponse({
      ok: false,
      code: error?.code || 'EXTRACTION_FAILED',
      error: error instanceof Error ? error.message : '无法读取当前帖子',
    }))

  return true
})

function getExtractor() {
  extractorPromise ||= import(chrome.runtime.getURL('content/extractor-core.js'))
  return extractorPromise
}

async function discoverList(source, extractors) {
  const extract = source === 'LIKED' ? extractors.extractLikedPage : extractors.extractFavoritesPage
  const itemsById = new Map()
  let latest = null
  let noNewRounds = 0
  const startedAt = Date.now()

  for (let round = 0; round < 30 && itemsById.size < 500 && Date.now() - startedAt < 60_000; round++) {
    const response = await chrome.runtime.sendMessage({ type: 'READ_XHS_ACCESS_CONTEXT' }).catch(() => null)
    latest = extract(document, window.location.href, new Date(), response?.accessContexts || [])
    const before = itemsById.size
    for (const item of latest.items) {
      itemsById.set(item.sourceItemId || item.url, item)
    }
    noNewRounds = itemsById.size === before ? noNewRounds + 1 : 0
    if (noNewRounds >= 3) break
    window.scrollTo({ top: document.documentElement.scrollHeight, behavior: 'auto' })
    await sleep(900)
  }

  return {
    ...latest,
    items: [...itemsById.values()],
    stats: {
      ...(latest?.stats || {}),
      extracted: itemsById.size,
    },
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
