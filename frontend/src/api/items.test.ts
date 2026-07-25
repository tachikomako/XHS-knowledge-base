import { afterEach, describe, expect, it, vi } from 'vitest'
import { changeItemLifecycle, searchItems, updateItem } from './items'

describe('knowledge item API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('builds a stable search query and omits blank filters', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ items: [], page: 2, pageSize: 12, total: 0 }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await searchItems({ q: '  Agent  ', lifecycleStatus: 'ARCHIVED', captureLevel: '', page: 2 })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/items?q=Agent&lifecycleStatus=ARCHIVED&page=2&pageSize=12&sort=updatedAt%2Cdesc',
      expect.objectContaining({ signal: undefined }),
    )
  })

  it('sends note edits and lifecycle actions to their dedicated endpoints', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ id: 'item/1' }) })
    vi.stubGlobal('fetch', fetchMock)

    await updateItem('item/1', { summary: '摘要', userNote: null })
    await changeItemLifecycle('item/1', 'trash')

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/items/item%2F1', expect.objectContaining({
      method: 'PATCH',
      body: JSON.stringify({ summary: '摘要', userNote: null }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/items/item%2F1/trash', expect.objectContaining({
      method: 'POST',
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
