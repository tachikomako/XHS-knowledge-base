import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const app = readFileSync(resolve(__dirname, 'App.vue'), 'utf8')
const dialog = readFileSync(resolve(__dirname, 'components/TaxonomyDialog.vue'), 'utf8')
const styles = readFileSync(resolve(__dirname, 'styles/main.css'), 'utf8')

describe('分类与标签入口', () => {
  it('将知识库标签筛选放在顶部搜索表单', () => {
    expect(app).toContain('分类管理')
    expect(app).toContain('v-for="tag in orderedTags"')
    expect(app).toContain("left.name.localeCompare(right.name, 'zh-CN')")
    expect(app).not.toContain('sourceTags.value.map')
  })

  it('分类管理弹窗不再提供手动标签管理', () => {
    expect(dialog).toContain('title="分类管理"')
    expect(dialog).not.toContain('createTag')
    expect(dialog).not.toContain('tag-manager-list')
    expect(dialog).not.toContain('mergeTag')
    expect(dialog).not.toContain('deleteTag')
  })

  it('分类管理使用单列布局并清理旧标签栏样式', () => {
    expect(styles).toContain('.taxonomy-layout { display: block;')
    expect(styles).not.toContain('.tag-manager-list')
    expect(styles).not.toContain('grid-template-columns: 1fr 1fr;')
  })
})
