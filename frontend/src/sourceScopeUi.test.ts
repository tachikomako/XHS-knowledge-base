import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const appVue = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')

describe('source scope navigation', () => {
  it('shows only all content, favorites, and pending in the sidebar source nav', () => {
    expect(appVue).toContain('所有内容')
    expect(appVue).toContain('我的收藏')
    expect(appVue).toContain('待整理')
    expect(appVue).not.toContain('我的点赞')
    expect(appVue).not.toContain('收藏 + 点赞')
    expect(appVue).not.toContain("sourceScope === 'LIKED'")
    expect(appVue).not.toContain("sourceScope === 'BOTH'")
  })
})
