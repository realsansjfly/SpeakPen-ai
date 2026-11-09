# SpeakPen 手机功能展示页

本页记录第一版交付。当前网页已根据用户要求完成 [第二版分块与动效改进](PRODUCT-SHOWCASE-V2-2026-10-31.md)，旧产物保留，构建脚本现在输出第二版目录。

介绍当前 Android 0.6.0-dev07，沿用绿白、Nunito、绿色用户消息与蓝色学习提示。静态页面独立于真实浏览器对话端，不增加模型调用、录音、配对或服务依赖，不更改 App 版本。

## 内容与交互

语音与文字对话、中文求助、440 条本地话题、六个场景与自建模板、目标依据与更正、自动结束和续聊、三档难度与独立配置、词库与导入、分层提示、表达优化与独立重说、实时纠错、话题语料、积累与收藏练习、固定／最新复盘与 Markdown 导出、成功反馈和次数记录。

主要功能用对话示意、换批、逐层提示和六场景切换展示，完整能力与边界使用七组原生 details 展开。明确示意内容、词库社区来源与数量、开发预览状态、联网／费用和浏览器差异。没有虚构在线练习入口、官方完整词库、云同步或硬件上市。

## 源码与构建

- 源码：`server/public/product.html`、`product.css`、`product.js`。
- 构建：`python scripts/build-product-showcase.py`。
- 检查：`python scripts/test-product-showcase.py`，沿用项目 `.tooling/preview-python` 的 Playwright 和本机 Edge。
- 输出：`deliverables/product-showcase-2026-10-31/`。
- `SpeakPen-功能介绍.html`：内嵌 CSS、脚本、Nunito 与字体许可的独立文件，可转发后用浏览器打开，不依赖 CDN。
- `index.html` 与 `SpeakPen-功能介绍-静态站点.zip`：纯静态托管入口。Cloudflare Pages 可选择静态资源上传并上传此 ZIP，或解压目录；无需构建命令和环境变量。只上传此目录中的站点文件，不上传仓库、`.local`、`.env` 或真实对话端。
- Nunito 使用原项目字体，SIL OFL 许可包含于 HTML 注释与 `OFL.txt`。

旧 `design/product-intro.*` 与旧功能介绍 HTML 保留。当前 Node 服务仅对白名单路径提供静态内容，故未宣称新增 `/product.html` 已被该服务提供，也未为此重启服务。

## 验证

浏览器通过 320、360、390、430、768、1440 宽度检查，没有横向溢出。换批十次相邻无重复；六场景选择、提示逐层展开与收起、详情打开、键盘焦点、下载地址检查通过。关闭 JavaScript 时主要功能说明仍可阅读，交互按钮隐藏；减少动态效果时背景静止。浏览器无脚本错误。截图和 `browser-verification.json` 位于交付目录。

Impeccable detector 执行一次，提示说明文字偏小和三个容器间距。说明文字已统一提升到 12px；后者结合实际画面复核：两个全宽背景内由 `.wrap.section` 提供留白，详情是 summary 自带内边距的分隔列表，不添加重复外框。手机首屏与桌面长页已目视检查。

上述是桌面 Edge 的视口与交互验证，不等同于 Android／iOS 真机、Safari 或微信内置浏览器验收。静态页面不需重新构建 APK，不调用收费 AI／ASR，也不复用上批 App 测试数量。

## 托管

已发布至免费 GitHub Pages：<https://realsansjfly.github.io/SpeakPen-ai/>。部署使用独立 `codex/product-showcase-pages` 分支，仅包含 `index.html`、字体许可及 `.nojekyll`，不修改 main、历史版本和现用服务。Cloudflare 的可上传静态包同步交付，未创建 Cloudflare 项目。

Pages 构建成功，公网 200；内容与本地 HTML 在换行标准化后完全一致。以手机触控视口检查换批、提示展开、场景切换和详情展开通过，无脚本错误，没有第三方资源请求；离线打开独立 HTML 后换批仍可用。核验见交付目录 `hosting-verification.json`。源码在本地保留待审，本批未把文档和开发源码提交至 main；仅发布所需的静态成品分支上传。
