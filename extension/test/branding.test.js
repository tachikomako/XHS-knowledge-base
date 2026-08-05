import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8')

test('extension branding uses 拾叶 and the green paper palette', () => {
  const manifest = JSON.parse(read('manifest.json'))
  const popup = read('popup/popup.html')
  const css = read('popup/popup.css')

  assert.equal(manifest.name, '拾叶 · 小红书收藏同步')
  assert.match(manifest.description, /拾叶/u)
  assert.match(popup, /<title>拾叶 · 小红书收藏同步<\/title>/u)
  assert.match(popup, /拾叶 · 小红书收藏同步/u)
  assert.match(css, /--color-primary: #6f8f72/u)
  assert.match(css, /--color-bg: #f3f0e6/u)
})
