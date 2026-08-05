<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight, MagicStick, Setting } from '@element-plus/icons-vue'

defineProps<{
  modelValue: boolean
  health: boolean
  aiConfigured: boolean | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  openAiSettings: []
  openTaxonomy: []
}>()

type GuidePage = {
  id: string
  kicker: string
  title: string
  body: string
  steps: string[]
  code?: string
  note?: string
  action?: 'ai' | 'taxonomy'
}

const guidePages: GuidePage[] = [
  {
    id: 'environment',
    kicker: '开始之前',
    title: '先启动两个本地服务',
    body: '拾叶运行在你的电脑上。第一次使用时，先打开两个 PowerShell 窗口，分别启动后端和前端。',
    steps: ['需要安装 JDK、Node.js 和 Chrome。', '后端窗口和前端窗口都要保持运行。', '看到本地地址后，再打开知识库网站。'],
    code: 'cd C:\\Users\\17705\\Documents\\xiaohongshu-knowledge-base\\backend\n.\\mvnw.cmd spring-boot:run\n\ncd C:\\Users\\17705\\Documents\\xiaohongshu-knowledge-base\\frontend\nnpm install\nnpm run dev',
    note: '后端默认是 8080 端口，前端默认是 http://127.0.0.1:5173。',
  },
  {
    id: 'extension',
    kicker: '只需设置一次',
    title: '把拾叶装进 Chrome',
    body: '插件负责从你正在浏览的小红书收藏页读取帖子，并把信息送进本地知识库。',
    steps: ['打开 chrome://extensions。', '开启右上角“开发者模式”。', '点击“加载已解压的扩展程序”，选择项目里的 extension 文件夹。', '打开插件的连接设置，填写后端地址和访问令牌。'],
    note: '插件只访问小红书和你的本地后端，不读取或上传 Cookie。',
  },
  {
    id: 'sync',
    kicker: '第一件要做的事',
    title: '只同步收藏，不自动整理',
    body: '打开小红书个人收藏页，再打开拾叶插件。同步前先重新扫描，确认插件识别到了收藏数量。',
    steps: ['点击“重新扫描”，等待收藏数量出现。', '通常不需要勾选“补全收藏正文”，先点击“开始同步”。', '同步完成后，回到知识库网站查看帖子。'],
    note: '同步只负责把收藏带回来，不会自动调用 AI。',
  },
  {
    id: 'content',
    kicker: '按需使用',
    title: '需要正文时，再补全正文',
    body: '收藏列表默认只保存标题、作者、链接和标签等卡片信息，这样同步更快，也更可控。',
    steps: ['需要完整正文时，勾选“补全收藏正文”。', '插件会逐篇打开详情页获取正文，耗时会更长。', '补全过程中可以点击“停止补全正文”，已经完成的内容会保留。'],
    note: '拾叶现在只处理收藏，开始同步前请确认当前页面是收藏页。',
  },
  {
    id: 'qwen',
    kicker: '可选配置',
    title: '先配置 AI，再开始分类',
    body: 'AI 不是同步的前置条件。只有你在网站里点击 AI 分类时，才需要配置 Qwen。',
    steps: ['点击页面顶部的“AI 设置”。', '填写 API Key、Base URL 和 Model。', '点击“保存并测试”，看到“已配置”后即可使用。'],
    note: 'API Key 只保存在本地服务端，不会写入浏览器 localStorage，也不会在界面显示真实密钥。',
    action: 'ai',
  },
  {
    id: 'ai',
    kicker: '整理知识库',
    title: '手动触发 AI 分类',
    body: '选择一篇或多篇帖子，再明确点击分类按钮。AI 会根据已有标题、作者、描述、正文和标签给出分类建议。',
    steps: ['“分类当前帖子”：只处理正在查看的帖子。', '“分类所选帖子”：只处理你勾选的帖子。', '“分类全部待整理帖子”：处理当前所有待整理内容。', '批量任务会显示已处理、成功和失败数量，失败项可以稍后重试。'],
    note: '没有正文也可以分类，AI 不会凭空生成虚假摘要。',
  },
  {
    id: 'taxonomy',
    kicker: '建立自己的目录',
    title: '分类是目录，标签是检索线索',
    body: '建议先建立少量稳定的分类，再用标签跨分类查找内容。AI 只会提出建议，最终由你确认。',
    steps: ['打开“分类与标签”管理分类目录。', '创建一级分类或子分类，按需要调整顺序。', '在顶部用标签、来源、分类和关键词组合筛选。'],
    note: '标签来自收藏原有标签或 AI 整理结果，当前不在管理弹窗里手动新增标签。',
    action: 'taxonomy',
  },
  {
    id: 'troubleshooting',
    kicker: '遇到问题时',
    title: '按这三处快速排查',
    body: '大多数连接问题都不是数据丢失，而是某个本地窗口、插件状态或页面没有刷新。',
    steps: ['顶部显示“本地服务未连接”：确认后端 PowerShell 窗口仍在运行。', '插件没有识别收藏：回到收藏页，刷新页面后重新扫描。', '代码更新后没生效：前端刷新页面，插件到 chrome://extensions 点击“重新加载”。'],
    note: '删除知识库只影响本地数据，不会取消你在小红书里的收藏。',
  },
]

const currentPage = ref(0)
const page = computed(() => guidePages[currentPage.value])
const progress = computed(() => Math.round(((currentPage.value + 1) / guidePages.length) * 100))

watch(() => guidePages.length, () => {
  if (currentPage.value >= guidePages.length) currentPage.value = guidePages.length - 1
})

function goToPage(index: number) {
  currentPage.value = Math.min(Math.max(index, 0), guidePages.length - 1)
}

function nextPage() {
  goToPage(currentPage.value + 1)
}

function previousPage() {
  goToPage(currentPage.value - 1)
}

function close(value: boolean) {
  emit('update:modelValue', value)
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="新手提示" width="min(860px, calc(100% - 24px))" class="help-dialog" @update:model-value="close">
    <div class="help-content">
      <header class="help-intro">
        <div>
          <span class="eyebrow">SHIYE START HERE</span>
          <h2>照着做，十分钟开始整理</h2>
          <p>每一页只讲一件事。完成当前步骤后，点击下一步继续。</p>
        </div>
        <div class="help-progress-summary" aria-label="教程进度">
          <strong>{{ currentPage + 1 }} / {{ guidePages.length }}</strong>
          <span>已完成 {{ progress }}%</span>
        </div>
      </header>

      <div class="help-status-grid">
        <div class="help-status"><span>本地服务</span><strong :class="{ 'status-ok': health }">{{ health ? '已连接' : '未连接' }}</strong></div>
        <div class="help-status"><span>AI 配置</span><strong :class="{ 'status-ok': aiConfigured }">{{ aiConfigured === null ? '状态未加载' : aiConfigured ? '已配置' : '未配置' }}</strong></div>
      </div>

      <nav class="help-page-nav" aria-label="新手提示步骤">
        <button
          v-for="(item, index) in guidePages"
          :key="item.id"
          class="help-page-dot"
          :class="{ active: index === currentPage, visited: index < currentPage }"
          type="button"
          :aria-label="`第 ${index + 1} 步：${item.title}`"
          :aria-current="index === currentPage ? 'step' : undefined"
          @click="goToPage(index)"
        >
          <span>{{ index + 1 }}</span>
          <b>{{ item.kicker }}</b>
        </button>
      </nav>

      <article class="help-page" :key="page.id">
        <div class="help-page-heading">
          <span class="help-page-kicker">{{ page.kicker }}</span>
          <h3>{{ page.title }}</h3>
          <p>{{ page.body }}</p>
        </div>
        <ol class="help-step-list">
          <li v-for="step in page.steps" :key="step">{{ step }}</li>
        </ol>
        <pre v-if="page.code" class="help-code"><code>{{ page.code }}</code></pre>
        <p v-if="page.note" class="help-note"><strong>记住：</strong>{{ page.note }}</p>
      </article>

      <footer class="help-footer">
        <el-button plain :disabled="currentPage === 0" :icon="ArrowLeft" @click="previousPage">上一步</el-button>
        <div class="help-footer-actions">
          <el-button v-if="page.action === 'ai'" plain :icon="Setting" @click="emit('openAiSettings')">打开 AI 设置</el-button>
          <el-button v-if="page.action === 'taxonomy'" plain :icon="MagicStick" @click="emit('openTaxonomy')">打开分类管理</el-button>
          <el-button v-if="currentPage < guidePages.length - 1" type="primary" :icon="ArrowRight" @click="nextPage">下一步</el-button>
          <el-button v-else type="primary" @click="close(false)">开始使用拾叶</el-button>
        </div>
      </footer>
    </div>
  </el-dialog>
</template>
