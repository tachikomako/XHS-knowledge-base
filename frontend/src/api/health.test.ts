import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchHealth } from './health'

describe('fetchHealth', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('returns the stable health contract', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        status: 'UP',
        apiVersion: 'v1',
        appVersion: '0.1.0-SNAPSHOT',
        aiConfigured: false,
      }),
    }))

    await expect(fetchHealth()).resolves.toMatchObject({
      status: 'UP',
      apiVersion: 'v1',
    })
  })

  it('turns an unsuccessful response into a readable error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 503 }))

    await expect(fetchHealth()).rejects.toThrow('后端返回 503')
  })
})
