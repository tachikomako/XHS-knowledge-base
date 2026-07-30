import { afterEach, describe, expect, it, vi } from 'vitest'
import { createCategory, createTag, deleteTag, fetchCategories, mergeTag, updateCategory } from './metadata'

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

  it('supports no-content deletes and readable errors', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, status: 204 })
      .mockResolvedValueOnce({ ok: false, status: 409, json: async () => ({ message: '分类仍在使用' }) })
    vi.stubGlobal('fetch', fetchMock)

    await expect(deleteTag('tag-1')).resolves.toBeUndefined()
    await expect(createTag('重复')).rejects.toThrow('分类仍在使用')
  })
})
