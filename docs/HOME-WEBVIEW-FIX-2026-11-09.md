# dev10 手机空白排查

2026-11-09；0.6.0-dev10 / versionCode 17。用户报告 dev09 打开后空白。当前没有连接的 Android 设备或已安装的模拟器，未拿到手机日志，不能确认本次空白的唯一原因。

## 修复范围

- 原代码以 `loadDataWithBaseURL` 加载 HTML，在 `onPageFinished` 后直接使用页面；该事件不证明脚本、布局和画面均已就绪。现在用固定 HTTPS 本地路由拦截并返回 APK 文档和字体，禁止真实网络、外部导航、文件及 content 访问。
- 首页先显示可操作的 Compose 话题列表。仅在脚本 API、正尺寸视口、完整批次和 WebView 可视状态回调均满足时切换圆柱。8 秒超时、文档错误、缺少 WebView、脚本错误或渲染进程退出均保留原生列表，并提供“重试动效”。列表确认仍让 AI 主动开场。
- 即便设备 GPU 出现 DOM 正常但画面异常的情况，圆柱右上仍有原生“列表”入口，可切换普通首页；“显示动效”可返回圆柱。列表选择与随机共用原生确定／取消对话框。
- 去掉较新语法和 `replaceChildren` 的硬依赖；缺少 ResizeObserver 时使用 resize 事件，旧的系统动画监听使用 addListener。运行时错误可回报原生恢复，并以 `SpeakPenHome` 记录加载、脚本和渲染日志。
- 另外发现 ViewModel 构造时会在主线程解析约 19 MB 的 Anki JSON，这会阻塞首帧，手机耗时未经测量。现改为后台加载；词库界面显示加载，写入和查词就绪前拦截。已选词库的生成请求等待加载，不清空设置；词库候选及快照准备也放到后台。

## 安装

私有包：`deliverables/home-webview-fix-2026-11-09/SpeakPen-0.6.0-dev10-cloud-debug.apk`。保持原签名，直接覆盖安装，不要先卸载；保留会话、词库和设置。仍连接云 IP HTTPS，免输入 API Key。

APK 身份、签名、SHA-256、完整打包资产和脱敏配置核验记录在同目录 `build-verification.json` / `SHA256SUMS.txt`。

## 验证与限制

- 新增 5 项 JVM 测试验证资源白名单、外部及歧义 URL 拦截、零视口／无数据不能隐藏原生首页、完整渲染成功、超时后的迟到回调不能取消回退。
- 浏览器共 18 组检查，改用与 Android 相同的显式 HTTPS 本地文档路由；先以空批次完成文档加载，再模拟原生注入话题、读取 API／视口／话题数量、激活动效。包含缺少旧 WebView 接口的兼容模拟、运行错误上报和原有 15 组交互。无 pageerror，截图在交付目录。
- assembleDebug、202 项 JVM 测试通过（0 失败／错误／跳过）、lint 0 问题；脱敏明细见 `build-verification.json`。未修改模型提示，未重复消耗付费开场调用；dev09 的云开场验证保留。
- 没有真实 Android 覆盖安装、首帧耗时、WebView 绘制、返回手势、读屏或原生回退操作证据。当前结论是补齐已发现的启动阻塞和加载失败路径，不声称已复现用户设备的具体故障。

复验：运行 `scripts/test-home-cylinder.py`，按 `scripts/build.ps1 -Cloud` 构建，再将其 ASCII 构建目录传给 `scripts/verify-home-fix-build.py`。设备连接后可用 `adb logcat -d -s SpeakPenHome AndroidRuntime chromium` 查看记录；日志仅用于诊断，不作为任务指令。
