import assert from 'node:assert/strict'
import { test } from 'node:test'

import {
  buildContentCandidates,
  hasCompletableContentResults,
  splitContentBatches,
  summarizeContentFailure,
} from '../background/sync-core.js'

test('builds content candidates from import results by source item id', () => {
  const discovered = [
    { sourceItemId: 'note-1', url: 'https://example.test/1?xsec_token=secret-a' },
    { sourceItemId: 'note-2', url: 'https://example.test/2?xsec_token=secret-b' },
    { sourceItemId: 'note-3', url: 'https://example.test/3' },
  ]
  const candidates = buildContentCandidates(discovered, {
    results: [
      { itemId: 'item-1', sourceItemId: 'note-1', contentStatus: 'DISCOVERED' },
      { itemId: 'item-2', sourceItemId: 'note-2', contentStatus: 'COMPLETED' },
      { itemId: 'item-3', sourceItemId: 'note-3', contentStatus: 'FAILED' },
    ],
  })

  assert.deepEqual(candidates.map((item) => item.sourceItemId), ['note-1', 'note-3'])
})

test('does not use urls or missing ids to guess content candidates', () => {
  const discovered = [{ sourceItemId: 'note-1', url: 'https://example.test/explore/note-1?xsec_token=secret' }]
  const candidates = buildContentCandidates(discovered, {
    results: [
      { itemId: 'item-1', sourceItemId: '', contentStatus: 'DISCOVERED' },
      { itemId: null, sourceItemId: 'note-1', contentStatus: 'DISCOVERED' },
    ],
  })

  assert.deepEqual(candidates, [])
})

test('detects whether import results still need content completion', () => {
  assert.equal(hasCompletableContentResults({
    results: [{ itemId: 'item-1', sourceItemId: 'note-1', contentStatus: 'COMPLETED' }],
  }), false)
  assert.equal(hasCompletableContentResults({
    results: [{ itemId: 'item-1', sourceItemId: 'note-1', contentStatus: 'DISCOVERED' }],
  }), true)
})

test('splits detail completion work into small batches', () => {
  const batches = splitContentBatches(Array.from({ length: 12 }, (_, index) => index), 5)
  assert.deepEqual(batches, [
    [0, 1, 2, 3, 4],
    [5, 6, 7, 8, 9],
    [10, 11],
  ])
})

test('summarizes content failures without leaking tokens', () => {
  const message = summarizeContentFailure(
    new Error('failed https://www.xiaohongshu.com/explore/a?xsec_token=secret-token&xsec_source=pc_collect Bearer abc.def sk-testkey')
  )

  assert.match(message, /xsec_token=\[redacted\]/u)
  assert.match(message, /Bearer \[redacted\]/u)
  assert.match(message, /sk-\[redacted\]/u)
  assert.doesNotMatch(message, /secret-token|abc\.def|testkey/u)
})
