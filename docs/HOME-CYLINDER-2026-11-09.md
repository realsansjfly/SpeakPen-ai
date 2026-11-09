# 首页圆柱话题与 AI 开场

日期：2026-11-09。版本：0.6.0-dev09 / versionCode 16。本轮在已同步组员代码、Anki 词库和已部署云服务基础上修改 App 首页，未推送 GitHub。

## 安装与查看

- 私有 APK：`deliverables/home-cylinder-2026-11-09/SpeakPen-0.6.0-dev09-cloud-debug.apk`，约 25.85 MiB。
- 离线交互预览：`deliverables/home-cylinder-2026-11-09/首页交互预览.html`，直接用浏览器打开。页面使用 APK 打包的同一份组件，嵌入本地字体和原有已审阅话题；确认后只提示预览选中，不调用模型，也不模拟 AI 回复。
- APK SHA-256：`c21655deb95d15824fc82d05d0b7432a65dfce27c19137dbbe307e947eb15d6a`。
- 签名证书 SHA-256：`7b0d36c386c7b1db1799d73fa41d67d80fce832d126e9a3ac31d8b1f223dff9f`，与上一版相同，可以覆盖安装，不用卸载。

仍固定连接 `https://47.118.51.5/v1`，不需要填写 API Key 或手动连接。Anki 四级／六级／雅思词库及多义、例句、摘录保留。平台 Key 不在 APK；应用访问令牌保留在原生配置，不传给首页 WebView。请将这个私有包仅发给获准使用的人。

## 最终交互

中央“今天，想聊点什么？”轻微上下浮动；18 个当前话题沿 4 层圆柱排列，通过 CSS perspective / translate3d、前排切线方向、深度透明度与模糊产生近实远虚。沿用浅色绿白与 Nunito，没有复用参考的深色品牌或装饰文案。

默认每秒 6°，约一分钟一圈。水平拖动直接改变角度，松手保留速度，再按时间积分的阻尼曲线平滑回落到自转速度。左右拖动均支持；拖动超过阈值捕获手势并抑制点击，系统取消触摸时复位。键盘方向键可调整角度，空格或暂停按钮控制自转。

点击前排话题会停转、模糊背景、放大完整内容，下方只有“开启对话？”和“确定／取消”。确认前不写剪贴板、不改草稿、不请求 AI。取消、背景点击、Escape 或 Android 系统返回恢复浏览。焦点限制在确认层，背景不接受操作。

“随便！”从当前批次随机选择一个话题，走相同确认流程。“换一批”仍从原有 440 条话题抽取 18 条，相邻两批无重复；正常动效模式下 900ms 快速旋转一圈，中段换内容，然后恢复慢速。读取失败保留重试入口。

确认后由原生 `PracticeViewModel` 保存话题上下文并请求 AI 主动提出一个重新组织的英文问题；会话只新增真正的 assistant 首条消息，不伪造用户发言，也不消耗用户草稿。提示遵循当前难度、词库与回复设置。取消生成或失败时保留话题，提供“重试开场”和“返回选话题”；开场未完成前禁用发送和录音，首条回复重新生成也继续带所选话题。

读屏、系统关闭动画、字体大于 1.35 倍或首页区域过矮时，使用可滚动的静态话题列表，所有话题和确认操作仍可访问。输入、打开抽屉、离开首页或切后台时暂停；只显示前排可见文字的键盘焦点，避免焦点跑入背面或屏幕外。

## 实现边界

`TopicSuggestions.kt` 保留 Compose 页面结构，用本地 WebView 承载 `app/src/main/assets/home/topic-cylinder.html`。这是用户要求 CSS 透视、又保留原生会话和录音能力的局部实现；没有将整个 App 改成网页。

页面完全离线：禁用网络、文件、content、外部导航和弹窗，只拦截并返回随 APK 打包的两个字体文件。原生桥仅接受换批、示例入口、选择状态和当前批次的话题 ID；确认时由 Kotlin 查找实际话题，不信任页面传来的任意文本。未加入第三方 JavaScript 运行库。

提供的 `Downloads/index.html` 和 `Downloads/orbit.html` 仅作为用户指定的实现参考。取前者的清晰前后景深与正向后排文字，取后者的按时间计算惯性、后台暂停和手势捕获方式；源文件未修改，未执行其中的外部依赖。此次没有修改独立浏览器服务的首页或部署服务器代码，云端已提供所需聊天接口。

## 验证

- Android：assembleDebug、197 项 JVM 测试通过（0 失败／错误／跳过），lint `No issues found.`；新版同签名核验通过。
- 新增 5 项开场测试覆盖话题与旧草稿存储、助手首轮、提示数据边界、取消／切换后的过期请求、生产提示导出；原有测试一并通过。
- Chromium / Edge 运行 APK 打包的同一份 HTML，15 组检查通过：慢速转动、点选暂停、完整文本与模态背景、取消恢复、随机和单次确认、拒绝恢复、惯性与误触保护、30/60/120Hz 等价积分、快转换批、后台停止、换批错误、320/390/430/768 宽度与小高度、减少动效、大字体、130 字长话题与焦点限制。无 pageerror；截图已目视检查。
- Impeccable 对组件的机械检查返回空列表；它不代替原生设备验收。
- 使用 JVM 导出的实际生产提示对云端真实流式调用 2 例，中英文话题均收到重新组织的问题。例如 `Do you have a morning ritual?` → `What is the first thing you do when you wake up?`；中文“小店”话题 → `What is the name of that new shop?`。实际结果位于 `topic-opening-live-results.json`。模型输出仍存在随机性，这两例不代表全部话题的生成质量保证。
- 验证 APK 内首页 HTML 与所测源码逐字节一致，Anki manifest 与当前资产一致，固定服务地址和令牌匹配。脱敏结果在 `build-verification.json`。

当前没有连接的 Android 设备，也没有已安装的模拟器，**未完成真机覆盖安装、WebView 渲染／字体、TalkBack、系统返回手势和 60fps／功耗验收**。浏览器组件通过不等同于真机效果通过。推荐覆盖安装后依次测试：点选取消 → 拖动松手 → 换一批 → 随便确认 → AI 首问 → 录音回复 → 断网开场重试 → 系统返回 → 大字体／减少动画。

## 本地复验

```powershell
python scripts/test-home-cylinder.py
./scripts/build.ps1 -JavaHome 'C:\EmojiJdk17' -Offline -Cloud `
  -OutputDirectory './deliverables/home-cylinder-2026-11-09' `
  -ApkName 'SpeakPen-0.6.0-dev09-cloud-debug.apk'
```

浏览器检查使用当前电脑已安装的 Edge 与 `.tooling/preview-python` 中的 Playwright。真实开场检查会消耗两次调用，需本地 `.local/cloud-connection.json`，并传入构建导出的生产提示文件：

```powershell
python scripts/test-topic-opening-live.py `
  ./deliverables/home-cylinder-2026-11-09/topic-opening-live-requests.json
```
