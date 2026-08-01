import { afterEach, describe, expect, it, vi } from 'vitest'
import { deleteItem, searchItems, updateItem } from './items'

describe('knowledge item API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('builds a stable search query and omits blank filters', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ items: [], page: 2, pageSize: 12, total: 0 }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await searchItems({
      q: '  Agent  ',
      categoryId: 'category-1',
      tagId: 'tag-1',
      sourceType: 'XIAOHONGSHU',
      captureLevel: '',
      page: 2,
    })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/items?q=Agent&categoryId=category-1&tagId=tag-1&sourceType=XIAOHONGSHU&page=2&pageSize=12&sort=updatedAt%2Cdesc',
      expect.objectContaining({ signal: undefined }),
    )
  })

  it('sends note edits and physical deletes to their dedicated endpoints', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ id: 'item/1' }) })
      .mockResolvedValueOnce({ ok: true, status: 204 })
    vi.stubGlobal('fetch', fetchMock)

    await updateItem('item/1', { summary: '摘要', userNote: null })
    await deleteItem('item/1')

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/items/item%2F1', expect.objectContaining({
      method: 'PATCH',
      body: JSON.stringify({ summary: '摘要', userNote: null }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/items/item%2F1', expect.objectContaining({
      method: 'DELETE',
    }))
  })

  it('uses the backend error message when available', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({ message: '查询参数无效' }),
    }))

    await expect(searchItems()).rejects.toThrow('查询参数无效')
  })

})
