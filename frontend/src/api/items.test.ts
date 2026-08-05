import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  cancelAiTask,
  clearItems,
  deleteItem,
  getAiTask,
  organizeItem,
  organizePendingAi,
  organizeSelectedAi,
  searchItems,
  updateItem,
} from './items'

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
      tagName: 'Java',
      sourceScope: 'FAVORITE',
      captureLevel: '',
      contentStatus: 'FAILED',
      page: 2,
    })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/items?q=Agent&categoryId=category-1&tagId=tag-1&tagName=Java&sourceScope=FAVORITE&contentStatus=FAILED&page=2&pageSize=12&sort=updatedAt%2Cdesc',
      expect.objectContaining({ signal: undefined }),
    )
  })

  it('sends note edits and physical deletes to their dedicated endpoints', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ id: 'item/1' }) })
      .mockResolvedValueOnce({ ok: true, status: 204 })
    vi.stubGlobal('fetch', fetchMock)

    await updateItem('item/1', { summary: 'summary', userNote: null })
    await deleteItem('item/1')

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/items/item%2F1', expect.objectContaining({
      method: 'PATCH',
      body: JSON.stringify({ summary: 'summary', userNote: null }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/items/item%2F1', expect.objectContaining({
      method: 'DELETE',
    }))
  })

  it('sends the typed confirmation when clearing the library', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ deletedItems: 3 }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(clearItems('清空知识库')).resolves.toEqual({ deletedItems: 3 })
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/items/clear', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ confirmation: '清空知识库' }),
    }))
  })

  it('sends manual AI organization requests', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ id: 'item/1', aiStatus: 'COMPLETED' }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ id: 'task-1', status: 'QUEUED', total: 1 }) })
    vi.stubGlobal('fetch', fetchMock)

    await organizeItem('item/1')
    await organizePendingAi()

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/items/item%2F1/organize', expect.objectContaining({
      method: 'POST',
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/ai/organize-pending', expect.objectContaining({
      method: 'POST',
    }))
  })

  it('creates selected AI tasks and polls task progress', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ id: 'task-1', status: 'QUEUED' }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ id: 'task-1', status: 'COMPLETED', processed: 2 }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ id: 'task-1', status: 'CANCELLED' }) })
    vi.stubGlobal('fetch', fetchMock)

    await organizeSelectedAi(['item/1', 'item/2'])
    await getAiTask('task-1')
    await cancelAiTask('task-1')

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/ai/organize-tasks', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ itemIds: ['item/1', 'item/2'] }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/ai/organize-tasks/task-1', expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/ai/organize-tasks/task-1/cancel', expect.objectContaining({
      method: 'POST',
    }))
  })

  it('uses the backend error message when available', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({ message: 'invalid query' }),
    }))

    await expect(searchItems()).rejects.toThrow('invalid query')
  })
})
