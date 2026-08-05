import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  confirmCategorySuggestions,
  createCategory,
  createTag,
  deleteTag,
  fetchCategories,
  fetchSourceTags,
  fetchUnifiedTags,
  generateCategorySuggestions,
  mergeTag,
  updateCategory,
} from './metadata'

describe('metadata API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('loads taxonomy catalogs', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => [] })
    vi.stubGlobal('fetch', fetchMock)
    await fetchCategories()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/categories', expect.objectContaining({
      headers: expect.objectContaining({ Accept: 'application/json' }),
    }))
  })

  it('creates and updates normalized metadata contracts', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({}) })
    vi.stubGlobal('fetch', fetchMock)

    await createCategory({ name: '技术', parentId: null, sortOrder: 0 })
    await updateCategory('category/1', { name: 'AI', parentId: null, sortOrder: 10 })
    await createTag('#Agent')
    await mergeTag('tag/source', 'tag/target')

    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/categories/category%2F1', expect.objectContaining({ method: 'PUT' }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/tags', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ name: '#Agent' }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(4, '/api/v1/tags/tag%2Fsource/merge', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ targetTagId: 'tag/target' }),
    }))
  })

  it('loads source tags and confirms category suggestions', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => [{ name: 'AI', itemCount: 2 }] })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ suggestions: [{ name: 'AI 与编程' }], sourceTags: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => [] })
    vi.stubGlobal('fetch', fetchMock)

    await fetchSourceTags()
    await generateCategorySuggestions()
    await confirmCategorySuggestions([{ name: 'AI 与编程', definition: '', scope: '', exclusions: '' }])

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/categories/source-tags', expect.anything())
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/categories/suggestions', expect.objectContaining({ method: 'POST' }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/categories/suggestions/confirm', expect.objectContaining({
      method: 'POST',
    }))
  })

  it('supports no-content deletes and readable errors', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, status: 204 })
      .mockResolvedValueOnce({ ok: false, status: 409, json: async () => ({ message: '分类仍在使用' }) })
    vi.stubGlobal('fetch', fetchMock)

    await expect(deleteTag('tag-1')).resolves.toBeUndefined()
    await expect(createTag('重复')).rejects.toThrow('分类仍在使用')
  })

  it('loads the unified read-only tag view', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => [] })
    vi.stubGlobal('fetch', fetchMock)
    await fetchUnifiedTags()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/tags?view=unified', expect.anything())
  })
})
