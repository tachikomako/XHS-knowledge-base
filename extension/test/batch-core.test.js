import assert from 'node:assert/strict'
import { test } from 'node:test'

import { createImportSummary, mergeImportResult, splitImportBatches } from '../background/batch-core.js'

test('splits large imports into backend-safe batches', () => {
  const batches = splitImportBatches(Array.from({ length: 121 }, (_, index) => ({ index })))
  assert.deepEqual(batches.map((batch) => batch.length), [50, 50, 21])
  assert.equal(batches[2][0].index, 100)
})

test('rejects empty and oversized imports', () => {
  assert.throws(() => splitImportBatches([]), /1 to 500/u)
  assert.throws(() => splitImportBatches(Array.from({ length: 501 })), /1 to 500/u)
})

test('merges backend results into one popup summary', () => {
  const summary = createImportSummary()
  mergeImportResult(summary, {
    received: 50, created: 40, updated: 5, skipped: 4, failed: 1, results: [{ status: 'CREATED' }],
  })
  mergeImportResult(summary, {
    received: 10, created: 2, updated: 1, skipped: 7, failed: 0, results: [{ status: 'SKIPPED' }],
  })
  assert.deepEqual(summary, {
    batches: 2,
    received: 60,
    created: 42,
    updated: 6,
    skipped: 11,
    failed: 1,
    results: [{ status: 'CREATED' }, { status: 'SKIPPED' }],
  })
})
