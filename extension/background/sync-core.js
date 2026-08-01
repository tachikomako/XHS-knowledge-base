const DETAIL_BATCH_SIZE = 5
const COMPLETABLE_STATUSES = new Set(['DISCOVERED', 'FAILED'])

export function buildContentCandidates(discoveredItems, importResult) {
  const bySourceId = new Map()
  for (const item of Array.isArray(discoveredItems) ? discoveredItems : []) {
    const sourceItemId = normalizeSourceItemId(item?.sourceItemId)
    if (sourceItemId) bySourceId.set(sourceItemId, item)
  }

  const candidates = []
  const seen = new Set()
  for (const result of Array.isArray(importResult?.results) ? importResult.results : []) {
    const sourceItemId = normalizeSourceItemId(result?.sourceItemId)
    if (!result?.itemId || !sourceItemId || !COMPLETABLE_STATUSES.has(result?.contentStatus)) continue
    if (seen.has(sourceItemId)) continue
    const item = bySourceId.get(sourceItemId)
    if (!item) continue
    seen.add(sourceItemId)
    candidates.push(item)
  }
  return candidates
}

export function hasCompletableContentResults(importResult) {
  return (Array.isArray(importResult?.results) ? importResult.results : [])
    .some((result) => result?.itemId && COMPLETABLE_STATUSES.has(result?.contentStatus))
}

export function splitContentBatches(items, size = DETAIL_BATCH_SIZE) {
  const batchSize = Math.max(1, Number(size) || DETAIL_BATCH_SIZE)
  const list = Array.isArray(items) ? items : []
  const batches = []
  for (let index = 0; index < list.length; index += batchSize) {
    batches.push(list.slice(index, index + batchSize))
  }
  return batches
}

export function summarizeContentFailure(error) {
  const raw = error instanceof Error ? error.message : String(error || '')
  const message = raw.trim() || 'Content completion failed'
  return redactSensitive(message).slice(0, 160)
}

function normalizeSourceItemId(value) {
  const normalized = String(value || '').trim()
  return normalized || null
}

function redactSensitive(value) {
  return value
    .replace(/([?&](?:xsec_token|token|access_token|api_key|key)=)[^&\s]+/giu, '$1[redacted]')
    .replace(/(Bearer\s+)[A-Za-z0-9._~+/=-]+/giu, '$1[redacted]')
    .replace(/(sk-)[A-Za-z0-9._-]+/giu, '$1[redacted]')
}
