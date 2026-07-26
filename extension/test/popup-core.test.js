import assert from 'node:assert/strict'
import { test } from 'node:test'

import { withTimeout } from '../popup/popup-core.js'

test('finishes page inspection on success or timeout', async () => {
  assert.equal(await withTimeout(Promise.resolve('ok'), 20), 'ok')
  await assert.rejects(withTimeout(new Promise(() => {}), 20), /页面识别超时/u)
})
