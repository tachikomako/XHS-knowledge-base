import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchLatestSyncRun, fetchSettings, updateAiSettings } from './settings'

describe('settings API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('loads settings without exposing secrets', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      text: async () => JSON.stringify({ aiEnabled: false, aiConfigured: true, model: 'qwen-plus' }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(fetchSettings()).resolves.toMatchObject({ aiConfigured: true, model: 'qwen-plus' })
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/settings', expect.objectContaining({
      headers: expect.objectContaining({ Accept: 'application/json' }),
    }))
  })

  it('updates the AI switch only', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      text: async () => JSON.stringify({ aiEnabled: true, aiConfigured: true, model: 'qwen-plus' }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await updateAiSettings(true)

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/settings/ai', expect.objectContaining({
      method: 'PATCH',
      body: JSON.stringify({ aiEnabled: true }),
    }))
  })

  it('loads the latest sync run and accepts an empty response', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, text: async () => JSON.stringify({ status: 'COMPLETED' }) })
      .mockResolvedValueOnce({ ok: true, text: async () => '' })
    vi.stubGlobal('fetch', fetchMock)

    await expect(fetchLatestSyncRun()).resolves.toMatchObject({ status: 'COMPLETED' })
    await expect(fetchLatestSyncRun()).resolves.toBeNull()
  })
})
