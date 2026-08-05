import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const popupHtml = readFileSync(new URL('../popup/popup.html', import.meta.url), 'utf8')
const popupJs = readFileSync(new URL('../popup/popup.js', import.meta.url), 'utf8')
const serviceWorker = readFileSync(new URL('../background/service-worker.js', import.meta.url), 'utf8')

test('popup no longer exposes liked sync', () => {
  assert.equal(popupHtml.includes('我的点赞'), false)
  assert.equal(popupHtml.includes('sourceLiked'), false)
  assert.equal(popupHtml.includes('补全收藏正文'), true)
})

test('manual sync only selects favorites', () => {
  assert.match(popupJs, /return \['FAVORITE'\]/u)
  assert.equal(popupJs.includes('sourceLiked'), false)
  assert.equal(popupJs.includes('我的点赞'), false)
})

test('manual sync navigation stays on favorites tab', () => {
  assert.match(serviceWorker, /url\.searchParams\.set\('tab', 'fav'\)/u)
  assert.equal(serviceWorker.includes("'liked'"), false)
})

test('content completion supports a persistent cancellation request', () => {
  assert.match(popupHtml, /id="stopContentButton"/u)
  assert.match(popupJs, /CANCEL_CONTENT_COMPLETION/u)
  assert.match(popupJs, /停止补全正文/u)
  assert.match(serviceWorker, /contentCompletionCancel:/u)
  assert.match(serviceWorker, /chrome\.storage\.local\.set/u)
  assert.match(serviceWorker, /shouldCancel/u)
  assert.match(serviceWorker, /ContentCompletionCancelledError/u)
  assert.match(serviceWorker, /awaitWithCancellation/u)
})

test('content completion confirmation explains variable timing and preserves completed content', () => {
  assert.match(popupJs, /图文帖子通常较快，视频帖子通常较慢/u)
  assert.match(popupJs, /已完成的内容会保留/u)
  assert.match(popupJs, /正文补全已由用户中断/u)
})
