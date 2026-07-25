export const EXTRACTOR_VERSION = 'xhs-dom-2'

const POST_PATH_PATTERN = /^\/(?:explore|discovery\/item)\/([^/?#]+)/u
const PROFILE_PATH_PATTERN = /^\/user\/profile\/[^/?#]+/u
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
const CARD_SELECTORS = [
  'section.note-item',
  '.feeds-container .note-item',
  '[data-testid="note-card"]',
  '.collection-list .note-item',
]
const CARD_TITLE_SELECTORS = [
  '[data-testid="note-card-title"]',
  '.footer .title',
  '.title',
]
const CARD_AUTHOR_SELECTORS = [
  '[data-testid="note-card-author"]',
  '[data-user-name]',
  '.author-wrapper .name',
  '.author .name',
  '[class*="author"] [class*="name"]',
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
  if (match) {
    return {
      pageType: 'CURRENT_POST',
      sourceItemId: match[1] || null,
    }
  }

  const tab = parsed.searchParams.get('tab')?.toLowerCase()
  const isFavoritesTab = ['fav', 'collect', 'collection', 'favorites'].includes(tab || '')
    || hasActiveFavoritesTab(document)
  if (PROFILE_PATH_PATTERN.test(parsed.pathname) && isFavoritesTab) {
    return { pageType: 'FAVORITES_PAGE' }
  }

  return { pageType: 'UNSUPPORTED', reason: '请打开小红书帖子或“我的收藏”页面' }
}

export function extractFavoritesPage(document, pageUrl, capturedAt = new Date()) {
  const detection = detectPage(pageUrl, document)
  if (detection.pageType !== 'FAVORITES_PAGE') {
    throw new ExtractionError('UNSUPPORTED_PAGE', detection.reason)
  }

  const collection = collectCardElements(document, pageUrl)
  const candidates = collection.cards.filter(isVisible)
  const items = []
  const seen = new Set()
  let skipped = 0
  let duplicates = 0
  const timestamp = capturedAt.toISOString()

  for (const card of candidates) {
    if (items.length >= 500) break
    const item = extractCard(card, pageUrl, timestamp)
    if (!item) {
      skipped++
      continue
    }
    const key = item.sourceItemId || canonicalPostKey(item.url)
    if (seen.has(key)) {
      duplicates++
      continue
    }
    seen.add(key)
    items.push(item)
  }

  const warnings = []
  if (items.length === 0) warnings.push('没有识别到已加载的收藏卡片，请确认已打开“收藏”标签')
  if (skipped > 0) warnings.push(`${skipped} 个卡片缺少标题或帖子链接，已跳过`)
  if (duplicates > 0) warnings.push(`${duplicates} 个重复卡片已合并`)
  if (candidates.length > 500) warnings.push('当前页面卡片超过 500 条，本次只处理前 500 条')

  return {
    pageType: 'FAVORITES_PAGE',
    extractorVersion: EXTRACTOR_VERSION,
    warnings,
    stats: {
      candidates: candidates.length,
      extracted: items.length,
      skipped,
      duplicates,
      knownContainers: collection.knownContainers,
      postLinks: collection.postLinks,
      fallbackContainers: collection.fallbackContainers,
    },
    items,
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
  return firstTextWithin(document, selectors)
}

function firstTextWithin(root, selectors) {
  for (const selector of selectors) {
    const element = root.querySelector(selector)
    if (!element || !isVisible(element)) continue
    const value = element.tagName?.toLowerCase() === 'meta'
      ? element.getAttribute('content')
      : element.textContent
    const normalized = normalizeWhitespace(value)
    if (normalized) return normalized
  }
  return ''
}

function hasActiveFavoritesTab(document) {
  const selectors = [
    '[role="tab"][aria-selected="true"]',
    '.reds-tab-item.active',
    '.tab-item.active',
    '[data-testid="favorites-tab"]',
  ]
  return selectors.some((selector) => [...document.querySelectorAll(selector)]
    .some((element) => normalizeWhitespace(element.textContent).includes('收藏')))
}

function collectCardElements(document, pageUrl) {
  const cards = new Set()
  const knownCards = new Set()
  for (const selector of CARD_SELECTORS) {
    for (const card of document.querySelectorAll(selector)) {
      cards.add(card)
      knownCards.add(card)
    }
  }

  const postLinks = [...document.querySelectorAll('a[href]')]
    .filter((link) => isVisible(link) && isPostLink(link.href || link.getAttribute('href'), pageUrl))
  let fallbackContainers = 0
  for (const link of postLinks) {
    if ([...knownCards].some((card) => card.contains(link))) continue
    const inferred = inferCardContainer(link, pageUrl)
    if (inferred && !cards.has(inferred)) {
      cards.add(inferred)
      fallbackContainers++
    }
  }
  return {
    cards: [...cards],
    knownContainers: knownCards.size,
    postLinks: postLinks.length,
    fallbackContainers,
  }
}

function inferCardContainer(link, pageUrl) {
  let current = link
  let inferred = link
  for (let depth = 0; depth < 7 && current?.parentElement; depth++) {
    current = current.parentElement
    if (current.tagName?.toLowerCase() === 'body') break
    const linkCount = [...current.querySelectorAll('a[href]')]
      .filter((candidate) => isPostLink(candidate.href || candidate.getAttribute('href'), pageUrl)).length
    if (linkCount !== 1) break
    inferred = current
  }
  return inferred
}

function isPostLink(value, base) {
  return POST_PATH_PATTERN.test(safeUrl(value, base)?.pathname || '')
}

function extractCard(card, pageUrl, capturedAt) {
  const anchors = card.matches?.('a[href]')
    ? [card, ...card.querySelectorAll('a[href]')]
    : [...card.querySelectorAll('a[href]')]
  const anchor = anchors
    .map((candidate, index) => ({
      candidate,
      index,
      url: safeUrl(candidate.href || candidate.getAttribute('href'), pageUrl),
    }))
    .filter(({ url }) => POST_PATH_PATTERN.test(url?.pathname || ''))
    .sort((left, right) => accessUrlScore(right.url) - accessUrlScore(left.url) || left.index - right.index)
    .at(0)?.candidate
  const postUrl = anchor ? normalizePostUrl(anchor.href || anchor.getAttribute('href'), pageUrl) : null
  if (!postUrl) return null

  const image = card.querySelector('.cover img, a.cover img, img')
  const coverUrl = normalizeMediaUrl(
    image?.currentSrc || image?.getAttribute('data-src') || image?.getAttribute('src'),
    pageUrl,
  )
  const title = limit(
    firstTextWithin(card, CARD_TITLE_SELECTORS)
      || normalizeWhitespace(anchor.getAttribute('title'))
      || normalizeWhitespace(image?.getAttribute('alt')),
    500,
  )
  if (!title) return null

  const sourceItemId = new URL(postUrl).pathname.match(POST_PATH_PATTERN)?.[1] || null
  return {
    sourceItemId,
    url: postUrl,
    title,
    author: limit(firstTextWithin(card, CARD_AUTHOR_SELECTORS), 200) || null,
    text: null,
    coverUrl,
    imageUrls: coverUrl ? [coverUrl] : [],
    captureLevel: 'CARD',
    capturedAt,
  }
}

function accessUrlScore(url) {
  if (!url) return 0
  let score = 0
  if (url.searchParams.get('xsec_token')) score += 2
  if (url.searchParams.get('xsec_source')) score += 1
  return score
}

function normalizePostUrl(value, pageUrl) {
  const parsed = safeUrl(value, pageUrl)
  if (!parsed || parsed.hostname !== 'www.xiaohongshu.com' || !POST_PATH_PATTERN.test(parsed.pathname)) return null
  parsed.hash = ''
  return parsed.toString()
}

function canonicalPostKey(value) {
  const parsed = new URL(value)
  return `${parsed.hostname}${parsed.pathname}`
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

function safeUrl(value, base) {
  try {
    return new URL(value, base)
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
