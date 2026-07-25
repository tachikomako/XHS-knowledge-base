import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { parseHTML } from 'linkedom'

import { detectPage, ExtractionError, extractCurrentPost, extractFavoritesPage } from '../content/extractor-core.js'

const fixtureRoot = new URL('./fixtures/', import.meta.url)

test('extracts a visible current post into the backend contract', async () => {
  const document = await loadFixture('current-post.html')
  const result = extractCurrentPost(
    document,
    'https://www.xiaohongshu.com/explore/fixture-post-001?xsec_source=test#comments',
    new Date('2026-07-25T04:00:00.000Z'),
  )

  assert.equal(result.pageType, 'CURRENT_POST')
  assert.equal(result.extractorVersion, 'xhs-dom-2')
  assert.deepEqual(result.warnings, [])
  assert.deepEqual(result.item, {
    sourceItemId: 'fixture-post-001',
    url: 'https://www.xiaohongshu.com/explore/fixture-post-001?xsec_source=test',
    title: '用 AI 整理英语学习资料',
    author: '示例作者',
    text: '这是一份用于自动化测试的脱敏正文。 #英语 #AI #教程',
    coverUrl: 'https://images.example.invalid/one.jpg',
    imageUrls: [
      'https://images.example.invalid/one.jpg',
      'https://images.example.invalid/two.jpg',
      'https://images.example.invalid/cover.jpg',
    ],
    captureLevel: 'DETAIL',
    capturedAt: '2026-07-25T04:00:00.000Z',
  })
})

test('falls back to Open Graph metadata', async () => {
  const document = await loadFixture('metadata-only-post.html')
  const result = extractCurrentPost(document, 'https://www.xiaohongshu.com/discovery/item/fixture-002')

  assert.equal(result.item.title, '由元数据提供的标题')
  assert.equal(result.item.author, '元数据作者')
  assert.equal(result.item.text, '由元数据提供的正文摘要')
  assert.equal(result.item.coverUrl, 'https://images.example.invalid/fallback.jpg')
  assert.equal(result.item.captureLevel, 'DETAIL')
})

test('rejects unrelated pages without extracting arbitrary content', () => {
  const document = parseHTML('<html><head><title>普通页面</title></head><body></body></html>').document
  assert.deepEqual(detectPage('https://www.xiaohongshu.com/user/profile/example', document), {
    pageType: 'UNSUPPORTED',
    reason: '请打开小红书帖子或“我的收藏”页面',
  })
  assert.throws(
    () => extractCurrentPost(document, 'https://www.xiaohongshu.com/user/profile/example'),
    (error) => error instanceof ExtractionError && error.code === 'UNSUPPORTED_PAGE',
  )
})

test('extracts and deduplicates loaded cards from the favorites page', async () => {
  const document = await loadFixture('favorites-page.html')
  const result = extractFavoritesPage(
    document,
    'https://www.xiaohongshu.com/user/profile/fixture-user?tab=fav',
    new Date('2026-07-25T05:00:00.000Z'),
  )

  assert.equal(result.pageType, 'FAVORITES_PAGE')
  assert.equal(result.extractorVersion, 'xhs-dom-2')
  assert.deepEqual(result.stats, {
    candidates: 4,
    extracted: 2,
    skipped: 1,
    duplicates: 1,
    knownContainers: 5,
    postLinks: 4,
    fallbackContainers: 0,
  })
  assert.deepEqual(result.warnings, ['1 个卡片缺少标题或帖子链接，已跳过', '1 个重复卡片已合并'])
  assert.deepEqual(result.items.map((item) => ({
    sourceItemId: item.sourceItemId,
    title: item.title,
    author: item.author,
    captureLevel: item.captureLevel,
  })), [
    { sourceItemId: 'favorite-001', title: 'Java Agent 入门资料', author: '示例作者甲', captureLevel: 'CARD' },
    { sourceItemId: 'favorite-002', title: '英语听力练习方法', author: '示例作者乙', captureLevel: 'CARD' },
  ])
  assert.equal(result.items[0].capturedAt, '2026-07-25T05:00:00.000Z')
  assert.equal(
    result.items[0].url,
    'https://www.xiaohongshu.com/explore/favorite-001?xsec_token=fixture-token&xsec_source=pc_collect',
  )
})

test('detects an active favorites tab even when the URL has no tab query', async () => {
  const document = await loadFixture('favorites-page.html')
  assert.deepEqual(detectPage('https://www.xiaohongshu.com/user/profile/fixture-user', document), {
    pageType: 'FAVORITES_PAGE',
  })
})

test('infers card boundaries from post links when Xiaohongshu class names change', async () => {
  const document = await loadFixture('favorites-page-unknown-layout.html')
  const result = extractFavoritesPage(
    document,
    'https://www.xiaohongshu.com/user/profile/fixture-user?tab=fav',
  )

  assert.equal(result.stats.knownContainers, 0)
  assert.equal(result.stats.postLinks, 2)
  assert.equal(result.stats.fallbackContainers, 2)
  assert.equal(result.stats.extracted, 2)
  assert.deepEqual(result.items.map((item) => item.title), [
    '由图片替代文本标题',
    '由链接属性提供标题',
  ])
  assert.equal(result.items[0].author, '回退作者')
})

test('uses a post link itself when the page has no card wrapper', () => {
  const document = parseHTML(`
    <html><body><main>
      <a href="/explore/direct-001" title="直接链接一"><img /></a>
      <a href="/explore/direct-002" title="直接链接二"><img /></a>
    </main></body></html>
  `).document
  const result = extractFavoritesPage(
    document,
    'https://www.xiaohongshu.com/user/profile/fixture-user?tab=fav',
  )
  assert.deepEqual(result.items.map((item) => item.title), ['直接链接一', '直接链接二'])
})

async function loadFixture(name) {
  const html = await readFile(fileURLToPath(new URL(name, fixtureRoot)), 'utf8')
  return parseHTML(html).document
}
