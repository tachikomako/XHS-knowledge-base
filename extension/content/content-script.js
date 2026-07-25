let extractorPromise

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type !== 'EXTRACT_CURRENT_POST') return false

  getExtractor()
    .then(({ extractCurrentPost }) => extractCurrentPost(document, window.location.href))
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
