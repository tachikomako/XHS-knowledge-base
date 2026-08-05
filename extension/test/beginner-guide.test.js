import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'

const html = fs.readFileSync(new URL('../popup/popup.html', import.meta.url), 'utf8')
const script = fs.readFileSync(new URL('../popup/beginner-guide.js', import.meta.url), 'utf8')
const popup = fs.readFileSync(new URL('../popup/popup.js', import.meta.url), 'utf8')

test('extension exposes a persistent beginner guide', () => {
  assert.match(html, /id="openBeginnerGuide"[^>]*>新手提示/)
  assert.match(html, /id="beginnerGuideDialog"/)
  assert.match(script, /extensionOnboardingSeen/)
  assert.match(script, /mvnw\.cmd spring-boot:run/)
  assert.match(script, /npm run dev/)
  assert.match(script, /不会自动调用 AI/)
  assert.match(popup, /setupBeginnerGuide\(\)/)
})
