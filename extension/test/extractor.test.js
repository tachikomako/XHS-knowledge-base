import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { parseHTML } from 'linkedom'

import { detectPage, ExtractionError, extractCurrentPost } from '../content/extractor-core.js'

const fixtureRoot = new URL('./fixtures/', import.meta.url)

test('extracts a visible current post into the backend contract', async () => {
  const document = await loadFixture('current-post.html')
  const result = extractCurrentPost(
    document,
    'https://www.xiaohongshu.com/explore/fixture-post-001?xsec_source=test#comments',
    new Date('2026-07-25T04:00:00.000Z'),
  )

  assert.equal(result.pageType, 'CURRENT_POST')
  assert.equal(result.extractorVersion, 'xhs-dom-1')
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
    reason: '当前不是可识别的小红书帖子页',
  })
  assert.throws(
    () => extractCurrentPost(document, 'https://www.xiaohongshu.com/user/profile/example'),
    (error) => error instanceof ExtractionError && error.code === 'UNSUPPORTED_PAGE',
  )
})

async function loadFixture(name) {
  const html = await readFile(fileURLToPath(new URL(name, fixtureRoot)), 'utf8')
  return parseHTML(html).document
}
