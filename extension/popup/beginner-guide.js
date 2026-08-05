const GUIDE_SEEN_KEY = 'extensionOnboardingSeen'

const sections = [
  ['启动本地服务', '先在 PowerShell 启动后端和前端，再打开插件。后端命令：\ncd C:\\Users\\17705\\Documents\\xiaohongshu-knowledge-base\\backend\n.\\mvnw.cmd spring-boot:run\n前端命令：\ncd C:\\Users\\17705\\Documents\\xiaohongshu-knowledge-base\\frontend\nnpm install\nnpm run dev'],
  ['安装并连接插件', '打开 chrome://extensions，开启开发者模式，加载项目中的 extension 文件夹。修改代码后点击重新加载，并刷新小红书页面。'],
  ['重新扫描收藏', '打开小红书个人收藏页，点击重新扫描，确认识别到收藏页面和帖子数量。重新扫描只读取页面，不会写入知识库。'],
  ['同步收藏', '点击开始同步。插件只同步收藏，不会自动调用 AI。默认只同步列表信息，勾选补全收藏正文后才会逐篇打开详情页。'],
  ['补全与停止', '正文补全属于可选的慢速操作。过程中可以点击停止补全正文，已经同步和已经完成的内容会保留，下次可以继续补全未完成内容。'],
  ['回到知识库', '点击打开知识库查看内容。网站中的 AI 分类必须由用户手动触发，不会因为插件同步自动执行。'],
  ['连接失败排查', '确认后端已经启动、访问令牌是 dev-local-token 或你保存的令牌、插件已经重新加载、小红书页面已经刷新。'],
]

function renderSections(container) {
  container.replaceChildren()
  for (const [title, body] of sections) {
    const section = document.createElement('details')
    section.className = 'guide-section'
    if (title === '启动本地服务') section.open = true
    const summary = document.createElement('summary')
    summary.textContent = title
    const paragraph = document.createElement('p')
    paragraph.textContent = body
    section.append(summary, paragraph)
    container.append(section)
  }
}

export function setupBeginnerGuide() {
  const button = document.querySelector('#openBeginnerGuide')
  const dialog = document.querySelector('#beginnerGuideDialog')
  const content = document.querySelector('#beginnerGuideContent')
  if (!button || !dialog || !content) return
  renderSections(content)
  button.addEventListener('click', () => dialog.showModal())
  dialog.addEventListener('close', () => chrome.storage.local.set({ [GUIDE_SEEN_KEY]: true }))
  chrome.storage.local.get(GUIDE_SEEN_KEY).then((state) => {
    if (!state[GUIDE_SEEN_KEY]) dialog.showModal()
  })
}

export { GUIDE_SEEN_KEY }
