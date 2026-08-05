<script setup lang="ts">
import { MagicStick, Setting } from '@element-plus/icons-vue'

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

const guideSections = [
  { id: 'environment', title: '1. 环境准备', body: '需要 JDK、Node.js 和 Chrome 浏览器。默认项目路径为 C:\\Users\\17705\\Documents\\xiaohongshu-knowledge-base，请在 PowerShell 中执行命令。' },
  { id: 'backend', title: '2. 启动后端', body: '保持这个 PowerShell 窗口运行，看到 Spring Boot 启动成功后再启动前端。后端默认监听本机 8080 端口，开发访问令牌为 dev-local-token。', code: 'cd C:\\Users\\17705\\Documents\\xiaohongshu-knowledge-base\\backend\n.\\mvnw.cmd spring-boot:run' },
  { id: 'frontend', title: '3. 启动前端', body: '首次运行或依赖变化后执行 npm install，日常开发执行 npm run dev，并使用终端输出的本地地址打开知识库。', code: 'cd C:\\Users\\17705\\Documents\\xiaohongshu-knowledge-base\\frontend\nnpm install\nnpm run dev' },
  { id: 'extension', title: '4. 安装 Chrome 插件', body: '打开 chrome://extensions，开启开发者模式，选择“加载已解压的扩展程序”，选中项目中的 extension 文件夹。修改代码后点击“重新加载”，再刷新已经打开的小红书页面。' },
  { id: 'sync', title: '5. 同步收藏', body: '打开小红书个人收藏页，打开拾叶插件并点击“重新扫描”，确认识别到收藏页面和帖子数量后点击“开始同步”。插件只同步收藏，不会自动调用 AI。' },
  { id: 'content', title: '6. 补全与中断正文', body: '默认只同步列表信息。勾选“补全收藏正文”后才逐篇打开详情页；补全过程中可以点击“停止补全正文”，已同步和已完成内容会保留。' },
  { id: 'qwen', title: '7. 配置 Qwen', body: '在知识库中打开 AI 设置，填写 Qwen API Key、Base URL 和 Model，点击保存并测试。API Key 不写入浏览器 localStorage，插件也不会保存 Qwen API Key。' },
  { id: 'ai', title: '8. 手动 AI 分类', body: '支持分类当前帖子、所选帖子和全部待整理帖子。只有用户点击后才会调用 Qwen；没有正文时仍可依据标题、作者、描述和标签分类。批量任务会显示进度，失败帖子可以再次处理。' },
  { id: 'category', title: '9. 分类管理', body: '打开分类管理，创建一级分类或子分类，按需重命名、排序和删除。AI 分类前建议先建立基本分类目录。这里不手动维护标签库。' },
  { id: 'search', title: '10. 标签与搜索', body: '顶部标签下拉默认展示热门标签，也可以输入关键词搜索全部标签。标签筛选可以和分类、所有内容、待整理内容以及关键词搜索组合使用；主搜索框负责标题、作者、描述和正文。' },
  { id: 'data', title: '11. 数据管理', body: '删除单篇帖子是物理删除。清空知识库需要输入确认词；清空不会删除分类、设置和 Qwen 配置。下一次同步可以重新导入已经删除的收藏。' },
  { id: 'troubleshooting', title: '12. 常见问题', body: '插件无法连接时，确认后端已启动、访问令牌正确、插件已重新加载且小红书页面已刷新。修改代码后需要重启或刷新前端，插件则需要在 chrome://extensions 点击重新加载。' },
]
</script>

<template>
  <el-dialog :model-value="modelValue" title="新手提示" width="min(820px, calc(100% - 24px))" class="help-dialog" @update:model-value="emit('update:modelValue', $event)">
    <div class="help-content">
      <section class="help-intro">
        <span class="eyebrow">SHIYE BEGINNER GUIDE</span>
        <h2>从启动，到稳定使用</h2>
        <p>拾叶把同步、正文补全、AI 整理和检索分开。按下面的顺序完成一次设置，就可以开始长期整理自己的收藏。</p>
      </section>
      <div class="help-status-grid">
        <div class="help-status"><span>后端服务</span><strong>{{ health ? '已连接' : '未连接' }}</strong></div>
        <div class="help-status"><span>AI 配置</span><strong>{{ aiConfigured === null ? '状态未加载' : aiConfigured ? '已配置' : '未配置' }}</strong></div>
      </div>
      <details v-for="section in guideSections" :key="section.id" class="guide-section" :open="section.id === 'environment'">
        <summary>{{ section.title }}</summary>
        <p>{{ section.body }}</p>
        <pre v-if="section.code"><code>{{ section.code }}</code></pre>
      </details>
      <div class="help-actions">
        <el-button plain :icon="Setting" @click="emit('openAiSettings')">打开 AI 设置</el-button>
        <el-button plain :icon="MagicStick" @click="emit('openTaxonomy')">打开分类管理</el-button>
      </div>
    </div>
  </el-dialog>
</template>
