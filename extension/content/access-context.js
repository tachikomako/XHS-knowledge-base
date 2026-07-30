export function collectAccessContextsFromPageState(stateRoots) {
  const roots = stateRoots || [
    globalThis.__INITIAL_STATE__,
    globalThis.__NUXT__,
    globalThis.__NEXT_DATA__,
    globalThis.__APOLLO_STATE__,
  ]
  const contexts = new Map()
  const seen = new WeakSet()
  const stack = roots.filter((value) => value && typeof value === 'object')
  let visited = 0

  while (stack.length && visited < 100_000) {
    const value = stack.pop()
    if (!value || typeof value !== 'object' || seen.has(value)) continue
    seen.add(value)
    visited++

    let entries
    try {
      entries = Object.entries(value)
    } catch {
      continue
    }

    const fields = Object.fromEntries(entries.filter(([, field]) => typeof field === 'string'))
    const xsecToken = fields.xsecToken || fields.xsec_token
    const xsecSource = fields.xsecSource || fields.xsec_source || 'pc_collect'
    const directNoteId = fields.noteId || fields.note_id || fields.noteID || fields.id
    const nestedNote = value.noteCard || value.note || value.noteInfo
    const nestedNoteId = nestedNote && typeof nestedNote === 'object'
      ? nestedNote.noteId || nestedNote.note_id || nestedNote.noteID || nestedNote.id
      : null
    const noteId = directNoteId || nestedNoteId

    if (/^[0-9a-f]{24}$/iu.test(String(noteId || '')) && xsecToken) {
      contexts.set(String(noteId), {
        noteId: String(noteId),
        xsecToken,
        xsecSource,
      })
    }

    for (const [, child] of entries) {
      if (child && typeof child === 'object') stack.push(child)
    }
  }

  return [...contexts.values()]
}
