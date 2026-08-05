import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const app = readFileSync(resolve(__dirname, 'App.vue'), 'utf8')
const help = readFileSync(resolve(__dirname, 'components/HelpCenterDialog.vue'), 'utf8')

describe('帮助中心入口与首次引导', () => {
  it('首次进入自动打开，关闭后使用本地状态避免重复弹出', () => {
    expect(app).toContain("localStorage.getItem('shiyé-help-seen')")
    expect(app).toContain("localStorage.setItem('shiyé-help-seen', 'true')")
    expect(app).toContain('帮助中心')
    expect(app).toContain('HelpCenterDialog')
  })

  it('复用同一个帮助内容并提供现有设置与分类入口', () => {
    expect(help).toContain('同步收藏')
    expect(help).toContain('补全收藏正文')
    expect(help).toContain('手动为当前帖子、所选帖子或全部待整理内容执行 AI 分类')
    expect(help).toContain('分类、标签与搜索')
    expect(help).not.toContain('点赞同步')
    expect(help).not.toContain('同步后自动 AI')
    expect(help).toContain("openAiSettings: []")
    expect(help).toContain("openTaxonomy: []")
  })
})
