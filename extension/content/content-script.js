let extractorPromise

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type !== 'INSPECT_XHS_PAGE') return false

  getExtractor()
    .then(({ detectPage, extractCurrentPost, extractFavoritesPage }) => {
      const page = detectPage(window.location.href, document)
      if (page.pageType === 'CURRENT_POST') return extractCurrentPost(document, window.location.href)
      if (page.pageType === 'FAVORITES_PAGE') return extractFavoritesPage(document, window.location.href)
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
