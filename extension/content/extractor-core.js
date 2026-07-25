export const EXTRACTOR_VERSION = 'xhs-dom-1'

const POST_PATH_PATTERN = /^\/(?:explore|discovery\/item)\/([^/?#]+)/u
const TITLE_SELECTORS = [
  '#detail-title',
  '[data-testid="note-title"]',
  '.note-content .title',
  'meta[property="og:title"]',
]
const AUTHOR_SELECTORS = [
  '.author-wrapper .username',
  '.author-container .name',
  '[data-testid="author-name"]',
  'meta[name="author"]',
]
const TEXT_SELECTORS = [
  '#detail-desc',
  '[data-testid="note-content"]',
  '.note-content .desc',
  'meta[property="og:description"]',
]
const IMAGE_SELECTORS = [
  '.note-slider img',
  '.swiper-slide img',
  '[data-testid="note-image"] img',
  'meta[property="og:image"]',
]

export class ExtractionError extends Error {
  constructor(code, message) {
    super(message)
    this.name = 'ExtractionError'
    this.code = code
  }
}

export function detectPage(url, document) {
  const parsed = safeUrl(url)
  if (!parsed || parsed.hostname !== 'www.xiaohongshu.com') {
    return { pageType: 'UNSUPPORTED', reason: '请打开小红书网页' }
  }

  const match = parsed.pathname.match(POST_PATH_PATTERN)
  if (!match) {
    return { pageType: 'UNSUPPORTED', reason: '当前不是可识别的小红书帖子页' }
  }

  return {
    pageType: 'CURRENT_POST',
    sourceItemId: match?.[1] || null,
  }
}

export function extractCurrentPost(document, pageUrl, capturedAt = new Date()) {
  const detection = detectPage(pageUrl, document)
  if (detection.pageType !== 'CURRENT_POST') {
    throw new ExtractionError('UNSUPPORTED_PAGE', detection.reason)
  }

  const warnings = []
  const title = limit(firstText(document, TITLE_SELECTORS) || cleanDocumentTitle(document.title), 500)
  if (!title) {
    throw new ExtractionError('TITLE_NOT_FOUND', '未识别到帖子标题，小红书页面结构可能已变化')
  }

  const author = limit(firstText(document, AUTHOR_SELECTORS), 200) || null
  const text = limit(firstText(document, TEXT_SELECTORS), 100_000) || null
  const imageUrls = collectImageUrls(document, pageUrl).slice(0, 20)

  if (!author) warnings.push('未识别到作者')
  if (!text) warnings.push('未识别到正文，将保存为卡片')
  if (imageUrls.length === 0) warnings.push('未识别到帖子图片')

  return {
    pageType: 'CURRENT_POST',
    extractorVersion: EXTRACTOR_VERSION,
    warnings,
    item: {
      sourceItemId: detection.sourceItemId,
      url: stripFragment(pageUrl),
      title,
      author,
      text,
      coverUrl: imageUrls[0] || null,
      imageUrls,
      captureLevel: text ? 'DETAIL' : 'CARD',
      capturedAt: capturedAt.toISOString(),
    },
  }
}

function firstText(document, selectors) {
  for (const selector of selectors) {
    const element = document.querySelector(selector)
    if (!element || !isVisible(element)) continue
    const value = element.tagName?.toLowerCase() === 'meta'
      ? element.getAttribute('content')
      : element.textContent
    const normalized = normalizeWhitespace(value)
    if (normalized) return normalized
  }
  return ''
}

function collectImageUrls(document, pageUrl) {
  const urls = []
  for (const selector of IMAGE_SELECTORS) {
    for (const element of document.querySelectorAll(selector)) {
      if (!isVisible(element)) continue
      const raw = element.tagName?.toLowerCase() === 'meta'
        ? element.getAttribute('content')
        : element.currentSrc || element.getAttribute('data-src') || element.getAttribute('src')
      const normalized = normalizeMediaUrl(raw, pageUrl)
      if (normalized && !urls.includes(normalized)) urls.push(normalized)
    }
  }
  return urls
}

function isVisible(element) {
  let current = element
  while (current) {
    const style = String(current.getAttribute?.('style') || '').toLowerCase()
    if (current.hasAttribute?.('hidden') || current.getAttribute?.('aria-hidden') === 'true') return false
    if (/display\s*:\s*none|visibility\s*:\s*hidden/u.test(style)) return false
    current = current.parentElement
  }
  return true
}

function normalizeMediaUrl(value, pageUrl) {
  if (!value) return null
  try {
    const url = new URL(String(value).trim(), pageUrl)
    return ['http:', 'https:'].includes(url.protocol) ? url.toString() : null
  } catch {
    return null
  }
}

function safeUrl(value) {
  try {
    return new URL(value)
  } catch {
    return null
  }
}

function stripFragment(value) {
  const url = new URL(value)
  url.hash = ''
  return url.toString()
}

function cleanDocumentTitle(value) {
  return normalizeWhitespace(value).replace(/\s*[-—_|]\s*小红书.*$/u, '')
}

function normalizeWhitespace(value) {
  return String(value || '').replace(/\s+/gu, ' ').trim()
}

function limit(value, maximum) {
  return value ? value.slice(0, maximum) : ''
}
