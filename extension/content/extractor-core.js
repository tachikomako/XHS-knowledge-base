export const EXTRACTOR_VERSION = 'xhs-dom-6'

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
const LIST_SOURCE_CONFIG = {
  FAVORITE: {
    pageType: 'FAVORITE',
    missingRootCode: 'FAVORITES_ROOT_NOT_FOUND',
    missingRootMessage: '未能识别当前收藏区域，请重新扫描或复制匿名诊断',
    emptyWarning: '没有识别到已加载的收藏卡片，请确认已打开“收藏”标签',
    xsecSource: 'pc_collect',
  },
  LIKED: {
    pageType: 'LIKED',
    missingRootCode: 'LIKED_ROOT_NOT_FOUND',
    missingRootMessage: '未能识别当前点赞区域，请重新扫描或复制匿名诊断',
    emptyWarning: '没有识别到已加载的点赞卡片，请确认已打开“点赞”标签',
    xsecSource: 'pc_like',
  },
}

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
      canClip: true,
      canBatch: false,
      postCount: 1,
    }
  }

  const profileTab = detectProfileTab(parsed)
  if (profileTab === 'FAVORITE' || (PROFILE_PATH_PATTERN.test(parsed.pathname) && findActiveFavoritesTab(document))) {
    const root = findProfileListRoot(document, 'FAVORITE') || (profileTab === 'FAVORITE' ? document : null)
    const postCount = root ? countPostLinks(root, url) : 0
    return { pageType: 'FAVORITE', canClip: false, canBatch: postCount > 1, postCount }
  }
  if (profileTab === 'LIKED') {
    const postCount = countPostLinks(document, url)
    return { pageType: 'LIKED', canClip: false, canBatch: postCount > 0, postCount }
  }

  const postCount = countPostLinks(document, url)
  return { pageType: 'FEED', canClip: false, canBatch: postCount > 1, postCount }
}

export function detectProfileTab(value) {
  const parsed = value instanceof URL ? value : safeUrl(value)
  if (!parsed || !PROFILE_PATH_PATTERN.test(parsed.pathname)) return null
  const tab = parsed.searchParams.get('tab')?.toLowerCase()
  const subTab = parsed.searchParams.get('subTab')?.toLowerCase()
  if (subTab !== 'note') return null
  if (tab === 'fav') return 'FAVORITE'
  if (tab === 'liked') return 'LIKED'
  return null
}

export function extractFavoritesPage(document, pageUrl, capturedAt = new Date(), accessContexts = []) {
  return extractProfileListPage(document, pageUrl, 'FAVORITE', capturedAt, accessContexts)
}

export function extractLikedPage(document, pageUrl, capturedAt = new Date(), accessContexts = []) {
  return extractProfileListPage(document, pageUrl, 'LIKED', capturedAt, accessContexts)
}

function extractProfileListPage(document, pageUrl, source, capturedAt = new Date(), accessContexts = []) {
  const config = LIST_SOURCE_CONFIG[source]
  const detection = detectPage(pageUrl, document)
  if (detection.pageType !== config.pageType) {
    throw new ExtractionError('UNSUPPORTED_PAGE', detection.reason)
  }

  const root = findProfileListRoot(document, source) || (detectProfileTab(pageUrl) === source ? document : null)
  if (!root) {
    throw new ExtractionError(
      config.missingRootCode,
      config.missingRootMessage,
    )
  }

  const collection = collectCardElements(root, pageUrl)
  const candidates = collection.cards.filter(isVisible)
  const items = []
  const seen = new Set()
  let skipped = 0
  let duplicates = 0
  let fullUrlCount = 0
  let bareUrlCount = 0
  let stateTokenMatchCount = 0
  let missingTokenCount = 0
  const timestamp = capturedAt.toISOString()
  const accessContextByNoteId = new Map(accessContexts.map((context) => [String(context.noteId), context]))
  const skippedItems = []

  for (const card of candidates) {
    if (items.length >= 500) break
    const extracted = extractCard(card, pageUrl, timestamp, accessContextByNoteId, config.xsecSource)
    if (!extracted) {
      skipped++
      continue
    }
    if (extracted.hadCompleteHref) fullUrlCount++
    else bareUrlCount++
    if (extracted.status === 'SKIPPED_MISSING_ACCESS_CONTEXT') {
      missingTokenCount++
      skippedItems.push(extracted)
      continue
    }
    if (extracted.accessSource === 'PAGE_STATE') stateTokenMatchCount++
    const item = extracted.item
    if (!hasExpectedXsecSource(item.url, config.xsecSource)) {
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
  if (items.length === 0) warnings.push(config.emptyWarning)
  if (skipped > 0) warnings.push(`${skipped} 个卡片缺少标题或帖子链接，已跳过`)
  if (missingTokenCount > 0) warnings.push(`${missingTokenCount} 个帖子缺少访问参数，已跳过且不会保存为失效链接`)
  if (duplicates > 0) warnings.push(`${duplicates} 个重复卡片已合并`)
  if (candidates.length > 500) warnings.push('当前页面卡片超过 500 条，本次只处理前 500 条')

  return {
    pageType: config.pageType,
    canClip: false,
    canBatch: items.length > 1,
    extractorVersion: EXTRACTOR_VERSION,
    warnings,
    stats: {
      candidates: candidates.length,
      extracted: items.length,
      skipped,
      duplicates,
      fullUrlCount,
      bareUrlCount,
      stateTokenMatchCount,
      missingTokenCount,
      knownContainers: collection.knownContainers,
      postLinks: collection.postLinks,
      fallbackContainers: collection.fallbackContainers,
    },
    skippedItems,
    items,
  }
}

export function buildXiaohongshuAccessUrl({
  noteId,
  hrefCandidates = [],
  xsecToken,
  xsecSource = 'pc_collect',
}) {
  const completeCandidate = hrefCandidates
    .map((href) => safeUrl(href, 'https://www.xiaohongshu.com'))
    .find((url) => url
      && url.hostname === 'www.xiaohongshu.com'
      && url.pathname.match(POST_PATH_PATTERN)?.[1] === noteId
      && url.searchParams.get('xsec_token')
      && url.searchParams.get('xsec_source'))

  if (completeCandidate) {
    completeCandidate.hash = ''
    return completeCandidate.toString()
  }
  if (!noteId || !xsecToken) return null

  const url = new URL(`/explore/${noteId}`, 'https://www.xiaohongshu.com')
  url.searchParams.set('xsec_token', xsecToken)
  url.searchParams.set('xsec_source', xsecSource || 'pc_collect')
  return url.toString()
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

  return {
    pageType: 'CURRENT_POST',
    canClip: true,
    canBatch: false,
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

function findActiveFavoritesTab(document) {
  return findActiveProfileTab(document, ['收藏'])
}

function findActiveProfileTab(document, labels) {
  const selectors = [
    '[role="tab"][aria-selected="true"]',
    '.reds-tab-item.active',
    '.tab-item.active',
    '[data-testid="favorites-tab"][aria-selected="true"]',
    '[data-testid="favorites-tab"].active',
  ]
  for (const selector of selectors) {
    const tab = [...document.querySelectorAll(selector)]
      .find((element) => isVisible(element)
        && labels.some((label) => normalizeWhitespace(element.textContent).includes(label)))
    if (tab) return tab
  }
  return null
}

function findProfileListRoot(document, source) {
  const tab = source === 'LIKED'
    ? findActiveProfileTab(document, ['点赞', '赞过'])
    : findActiveFavoritesTab(document)
  if (!tab) return null

  const controlledId = tab.getAttribute('aria-controls')
  const controlled = controlledId ? document.getElementById(controlledId) : null
  if (controlled && isVisible(controlled)) return controlled

  const labelledPanel = tab.id
    ? [...document.querySelectorAll('[role="tabpanel"]')]
      .find((panel) => panel.getAttribute('aria-labelledby') === tab.id && isVisible(panel))
    : null
  if (labelledPanel) return labelledPanel

  const visiblePanels = [...document.querySelectorAll('[role="tabpanel"]')].filter(isVisible)
  if (visiblePanels.length === 1) return visiblePanels[0]

  const visibleRoots = [...new Set([
    ...document.querySelectorAll('#userPostedFeeds'),
    ...document.querySelectorAll('[data-testid="favorites-content"]'),
    ...document.querySelectorAll('.collection-list'),
    ...document.querySelectorAll('.feeds-container'),
  ])].filter(isVisible)
  return visibleRoots.length === 1 ? visibleRoots[0] : null
}

function collectCardElements(root, pageUrl) {
  const cards = new Set()
  const knownCards = new Set()
  for (const selector of CARD_SELECTORS) {
    for (const card of root.querySelectorAll(selector)) {
      cards.add(card)
      knownCards.add(card)
    }
  }

  const postLinks = [...root.querySelectorAll('a[href]')]
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

function countPostLinks(root, pageUrl) {
  const ids = [...root.querySelectorAll('a[href]')]
    .filter((link) => isVisible(link))
    .map((link) => safeUrl(link.href || link.getAttribute('href'), pageUrl)?.pathname.match(POST_PATH_PATTERN)?.[1])
    .filter(Boolean)
  return new Set(ids).size
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

function extractCard(card, pageUrl, capturedAt, accessContextByNoteId, defaultXsecSource = 'pc_collect') {
  const anchors = card.matches?.('a[href]')
    ? [card, ...card.querySelectorAll('a[href]')]
    : [...card.querySelectorAll('a[href]')]
  const postLinks = anchors
    .map((candidate, index) => ({
      candidate,
      index,
      url: safeUrl(candidate.href || candidate.getAttribute('href'), pageUrl),
    }))
    .filter(({ url }) => POST_PATH_PATTERN.test(url?.pathname || ''))
    .sort((left, right) => accessUrlScore(right.url) - accessUrlScore(left.url) || left.index - right.index)
  const anchor = postLinks.at(0)?.candidate
  const noteId = postLinks.at(0)?.url.pathname.match(POST_PATH_PATTERN)?.[1] || null
  if (!anchor || !noteId) return null

  const hrefCandidates = postLinks.map(({ candidate }) => candidate.href || candidate.getAttribute('href'))
  const cardContext = findCardAccessContext(card)
  const pageContext = accessContextByNoteId.get(noteId)
  const context = cardContext || pageContext
  const postUrl = buildXiaohongshuAccessUrl({
    noteId,
    hrefCandidates,
    xsecToken: context?.xsecToken,
    xsecSource: context?.xsecSource || defaultXsecSource,
  })
  const hadCompleteHref = hrefCandidates.some((href) => accessUrlScore(safeUrl(href, pageUrl)) === 3)
  if (!postUrl) return { status: 'SKIPPED_MISSING_ACCESS_CONTEXT', noteId, hadCompleteHref }

  const image = card.querySelector('.cover img, a.cover img, img')
  const coverUrl = normalizeMediaUrl(imageUrlFromElement(image), pageUrl)
  const title = limit(
    firstTextWithin(card, CARD_TITLE_SELECTORS)
      || normalizeWhitespace(anchor.getAttribute('title'))
      || normalizeWhitespace(image?.getAttribute('alt')),
    500,
  )
  if (!title) return null

  return {
    status: 'EXTRACTED',
    hadCompleteHref,
    accessSource: hadCompleteHref ? 'HREF' : cardContext ? 'CARD_DATA' : 'PAGE_STATE',
    item: {
      sourceItemId: noteId,
      url: postUrl,
      title,
      author: limit(firstTextWithin(card, CARD_AUTHOR_SELECTORS), 200) || null,
      text: null,
      coverUrl,
      imageUrls: coverUrl ? [coverUrl] : [],
      captureLevel: 'CARD',
      capturedAt,
    },
  }
}

function findCardAccessContext(card) {
  const elements = [card, ...card.querySelectorAll('[data-xsec-token]')]
  for (const element of elements) {
    const xsecToken = element.getAttribute?.('data-xsec-token')
    if (xsecToken) {
      return {
        xsecToken,
        xsecSource: element.getAttribute('data-xsec-source') || 'pc_collect',
      }
    }
  }
  return null
}

function accessUrlScore(url) {
  if (!url) return 0
  let score = 0
  if (url.searchParams.get('xsec_token')) score += 2
  if (url.searchParams.get('xsec_source')) score += 1
  return score
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
      const normalized = normalizeMediaUrl(imageUrlFromElement(element), pageUrl)
      if (normalized && !urls.includes(normalized)) urls.push(normalized)
    }
  }
  return urls
}

function imageUrlFromElement(element) {
  if (!element) return null
  if (element.tagName?.toLowerCase() === 'meta') return element.getAttribute('content')
  return element.currentSrc
    || element.getAttribute('data-src')
    || element.getAttribute('src')
    || firstSrcsetUrl(element.getAttribute('srcset'))
}

function firstSrcsetUrl(value) {
  return String(value || '').split(',').map((part) => part.trim().split(/\s+/u)[0]).find(Boolean) || null
}

function hasExpectedXsecSource(value, expected) {
  return safeUrl(value)?.searchParams.get('xsec_source') === expected
}

function isVisible(element) {
  let current = element
  while (current) {
    const style = String(current.getAttribute?.('style') || '').toLowerCase()
    const computedStyle = current.ownerDocument?.defaultView?.getComputedStyle?.(current)
    if (current.hasAttribute?.('hidden') || current.getAttribute?.('aria-hidden') === 'true') return false
    if (/display\s*:\s*none|visibility\s*:\s*hidden/u.test(style)) return false
    if (computedStyle?.display === 'none' || computedStyle?.visibility === 'hidden') return false
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
