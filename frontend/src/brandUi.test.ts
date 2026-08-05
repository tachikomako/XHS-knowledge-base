import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')

describe('拾叶 brand system', () => {
  it('uses 拾叶 in the website and extension entry points', () => {
    expect(read('../index.html')).toContain('<title>拾叶 · 小红书知识库</title>')
    expect(read('../../extension/popup/popup.html')).toContain('拾叶 · 小红书收藏同步')
    expect(read('../../extension/manifest.json')).toContain('"name": "拾叶 · 小红书收藏同步"')
    expect(read('../../README.md')).toContain('# 拾叶 · 小红书知识库')
  })

  it('keeps the quiet green paper palette centralized', () => {
    const css = read('./styles/main.css')
    expect(css).toContain('--color-primary: #6f8f72')
    expect(css).toContain('--color-bg: #f3f0e6')
    expect(css).toContain('--shadow-soft: 0 8px 24px rgba(64, 82, 67, 0.08)')
    expect(css).toContain('font-family: Inter, "PingFang SC", "Microsoft YaHei", sans-serif')
  })
})
