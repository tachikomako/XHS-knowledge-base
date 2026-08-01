export const MAX_IMPORT_ITEMS = 500
export const IMPORT_BATCH_SIZE = 50

export function splitImportBatches(items) {
  if (!Array.isArray(items) || items.length === 0 || items.length > MAX_IMPORT_ITEMS) {
    throw new Error(`items must contain 1 to ${MAX_IMPORT_ITEMS} entries`)
  }
  const batches = []
  for (let offset = 0; offset < items.length; offset += IMPORT_BATCH_SIZE) {
    batches.push(items.slice(offset, offset + IMPORT_BATCH_SIZE))
  }
  return batches
}

export function createImportSummary() {
  return { batches: 0, received: 0, created: 0, updated: 0, skipped: 0, failed: 0, results: [] }
}

export function mergeImportResult(summary, batch) {
  summary.batches++
  summary.received += batch.received
  summary.created += batch.created
  summary.updated += batch.updated
  summary.skipped += batch.skipped
  summary.failed += batch.failed
  summary.results.push(...batch.results)
  return summary
}
