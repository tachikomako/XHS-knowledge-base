import { describe, expect, it } from 'vitest'
import { proxiedImageUrl } from './media'

describe('proxiedImageUrl', () => {
  it('proxies Xiaohongshu CDN images through the local backend', () => {
    expect(proxiedImageUrl('https://sns-img-qc.xhscdn.com/example.jpg'))
      .toBe('/api/v1/media/proxy?url=https%3A%2F%2Fsns-img-qc.xhscdn.com%2Fexample.jpg')
  })

  it('leaves unrelated images untouched', () => {
    expect(proxiedImageUrl('https://example.com/a.jpg')).toBe('https://example.com/a.jpg')
  })
})
