# XHS Knowledge Base

一个本地优先的个人知识库：通过 Chrome 扩展采集用户主动访问的小红书收藏和帖子，在独立网站中完成搜索、分类、标签和笔记整理。

当前处于 MVP 开发阶段。第一阶段只支持小红书，后续通过来源适配器扩展到普通网页、GitHub、B 站和知乎等平台。

## 当前能力

- Spring Boot 健康检查 API
- Vue 3 + Element Plus 独立网站骨架
- Chrome Manifest V3 扩展骨架，可检查后端连接并打开知识库
- SQLite 本地持久化和数据库自动初始化
- 令牌保护的小红书批量导入、幂等去重和 CARD→DETAIL 升级
- 条目分页查询、手工摘要/笔记、归档、回收站和恢复 API

下一步：当前帖子 DOM 剪藏、收藏页批量索引、知识库管理页面和可选 Qwen 自动整理。

## 技术栈

- 扩展：Chrome Extension Manifest V3、原生 JavaScript
- 前端：Vue 3、TypeScript、Vite、Element Plus
- 后端：Java 21、Spring Boot、MyBatis-Plus
- 数据：SQLite
- AI：Qwen（可选；核心功能不依赖 AI）

首版不使用 Redis、消息队列、向量数据库、RAG 或 Agent。

## 本地开发

### 1. 后端

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

默认监听 `http://127.0.0.1:8080`，健康检查：`GET /api/v1/health`。

导入接口默认使用开发令牌 `dev-local-token`。实际使用前请通过环境变量 `XHS_EXTENSION_TOKEN` 改成随机值，并在扩展 Popup 中填写相同令牌。

### 2. 前端

```powershell
cd frontend
npm install
npm run dev
```

默认地址为 `http://127.0.0.1:5173`。开发服务器会把 `/api` 代理到后端。

### 3. Chrome 扩展

1. 打开 `chrome://extensions`。
2. 开启“开发者模式”。
3. 点击“加载已解压的扩展程序”，选择仓库中的 `extension` 目录。
4. 点击扩展图标，保存后端地址和知识库地址。
5. 点击“检查连接”。

扩展仅申请访问小红书和本地后端所需权限，不读取或上传 Cookie。

## 仓库结构

```text
backend/    Spring Boot API
frontend/   Vue 独立网站
extension/  Chrome Manifest V3 扩展
docs/       架构和开发说明
fixtures/   脱敏 DOM 测试样本（后续加入）
```

接口说明见 [docs/api.md](docs/api.md)，架构边界见 [docs/architecture.md](docs/architecture.md)。运行全部验证：

```powershell
.\scripts\verify.ps1
```

## 产品边界

- 本地删除不会取消小红书收藏。
- MVP 不调用小红书私有接口，不绕过登录、验证码或风控。
- 收藏列表批量导入只保证卡片级信息；完整正文由当前帖子剪藏获得。
- 内容默认仅供个人使用，不提供公开转载功能。

## License

[MIT](LICENSE)
