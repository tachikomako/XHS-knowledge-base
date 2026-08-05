import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const app = readFileSync(resolve(__dirname, 'App.vue'), 'utf8')
const help = readFileSync(resolve(__dirname, 'components/HelpCenterDialog.vue'), 'utf8')

describe('帮助中心入口与首次引导', () => {
  it('首次进入自动打开，关闭后使用本地状态避免重复弹出', () => {
    expect(app).toContain("localStorage.getItem('shiyé-onboarding-seen')")
    expect(app).toContain("localStorage.setItem('shiyé-onboarding-seen', 'true')")
    expect(app).toContain('新手提示')
    expect(app).toContain('HelpCenterDialog')
  })

  it('reloads once after a completed batch AI task', () => {
    expect(app).toContain('finishAiTaskAndReload(task)')
    expect(app).toContain('window.location.reload()')
    expect(app).toContain("aiTask.value = null")
  })

  it('使用分页教程突出单个重点，不使用下拉或折叠内容', () => {
    expect(help).toContain('const guidePages: GuidePage[]')
    expect(help).toContain('上一步')
    expect(help).toContain('下一步')
    expect(help).toContain('help-page-nav')
    expect(help).not.toContain('<details')
    expect(help).not.toContain('<summary')
    expect(help).toContain('启动后端')
    expect(help).toContain('需要正文时，再补全正文')
    expect(help).toContain('手动触发 AI 分类')
    expect(help).not.toContain('点赞同步')
    expect(help).not.toContain('同步后自动 AI')
    expect(help).toContain("openAiSettings: []")
    expect(help).toContain("openTaxonomy: []")
  })
})
