import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearAiCredentials, fetchLatestSyncRun, fetchSettings, testAiConnection, updateAiSettings } from './settings'

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

  it('updates AI settings without localStorage', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      text: async () => JSON.stringify({
        aiEnabled: true,
        aiConfigured: true,
        baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
        model: 'qwen-plus',
      }),
    })
    vi.stubGlobal('fetch', fetchMock)
    const localStorage = { setItem: vi.fn(), getItem: vi.fn(), removeItem: vi.fn() }
    vi.stubGlobal('localStorage', localStorage)

    await updateAiSettings({
      aiEnabled: true,
      apiKey: 'sk-test',
      baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      model: 'qwen-plus',
    })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/settings/ai', expect.objectContaining({
      method: 'PATCH',
      headers: expect.objectContaining({
        'X-Extension-Token': 'dev-local-token',
      }),
      body: JSON.stringify({
        aiEnabled: true,
        apiKey: 'sk-test',
        baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
        model: 'qwen-plus',
      }),
    }))
    expect(localStorage.setItem).not.toHaveBeenCalled()
  })

  it('tests Qwen connection through a safe endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      text: async () => JSON.stringify({ success: true, configured: true, model: 'qwen-plus', message: 'ok' }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(testAiConnection()).resolves.toMatchObject({ success: true, configured: true })
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/settings/ai/test', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({
        'X-Extension-Token': 'dev-local-token',
      }),
    }))
  })

  it('clears AI credentials through the protected endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      text: async () => JSON.stringify({ aiEnabled: true, aiConfigured: false, model: 'qwen-plus' }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(clearAiCredentials()).resolves.toMatchObject({ aiConfigured: false })
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/settings/ai/credentials', expect.objectContaining({
      method: 'DELETE',
      headers: expect.objectContaining({
        'X-Extension-Token': 'dev-local-token',
      }),
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
