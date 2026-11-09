# SpeakPen · 开口 — 0.7.0

0.7.0 将近期学习流程升级整理为一个公开版本：首页使用可拖动的 CSS 3D 话题圆柱，选定话题后由 AI 主动开场；场景练习升级为全页选择、任务简报和完整示范对话；默认词库替换为 Anki 四级、六级和雅思资料，并加入可选的云端语义检索增强。完整改动见 [v0.7.0 版本说明](docs/releases/v0.7.0.md)。

当前发布：[v0.7.0](https://github.com/realsansjfly/SpeakPen-ai/releases/tag/v0.7.0)。[全部 Releases](https://github.com/realsansjfly/SpeakPen-ai/releases) 提供历史版本说明和 APK；[在线功能展示](https://realsansjfly.github.io/SpeakPen-ai/) 已同步到 0.7.0。

公开 APK 不内置服务地址或配对令牌。已有设备可覆盖安装并沿用已保存的配对；首次安装后，在“设置 → 服务连接 → 重新连接”粘贴自己的私密配对入口。下文“直接使用”为 0.4.0 历史说明，当前版本请按版本说明安装。

0.7.0 的公开 APK 不内置服务地址或配对令牌。已有设备可覆盖安装并沿用已保存的配对；首次安装后，在“设置 → 服务连接 → 重新连接”粘贴自己的私密配对入口。固定云地址的私有构建与公开 Release 严格分开。

dev02 历史：设置区分“当前对话／新对话默认”，优化提示插入原句下方并可折叠，话题语料强化中英解释，见 [第一批调试记录](docs/DEBUG-FIRST-BATCH-2026-10-26.md)。dev03 起设置改为抽屉中的独立页面，返回自动保存、失败留页重试，已取代 dev02 的显式保存／撤销和离开确认。

真实聊天 `qwen3.7-flash-2026-07-15` 和语音识别 `qwen3-asr-flash` 已接通。云端朗读使用独立的官方百炼 `qwen3-tts-flash` 凭据；未配置时 App 自动回退到 Android 系统朗读。配置流程见 [云端语音](docs/CLOUD-TTS-2026-11-09.md)。

## 0.4.0 学习交互更新

“收藏好表达”改为空白手动积累；长按消息可识别、复制或优化/重新生成。下方新增话题语料，优化和语料均为蓝色提示，不进入对话上下文。完整操作和专用提示词说明见 `docs/UI-LEARNING-0.4.0.md`。

5 个开放资料候选供核查：`docs/resources/英语场景资料核查清单.md`。未导入第三方语料。

## 直接使用

1. 安装 `deliverables/SpeakPen-0.4.0-debug.apk`，可覆盖 0.2.0，保留已有对话与复习内容。
2. 正常打开 App 即连接已经运行的本机服务。默认退出旧版示例体验。
3. 按住说话、松开发送、上滑取消；语音先保存，再转写并请求 AI。点击自己的语音重听，AI 回复可切换语音块 / 英文原文。
4. 点击“收藏好表达”手动填写积累；或长按某个消息块“识别好表达”，确认 AI 候选后入库。选择目标可进入复习对话。
5. 手机无法直接拿到 APK 时，在电脑双击 `deliverables/打开真实对话.html`。手机扫码可使用网页真实对话、下载安装包，并点击“连接 App”更新连接地址。

服务在这台 Windows 电脑上，使用时电脑必须保持开机联网。默认不自动开机启动；下次开机后双击 `deliverables/启动口语服务.cmd`。停止用 `deliverables/停止口语服务.cmd`。

## 手机与本机连接

本次手机无法连接的原因及修复见 `docs/CONNECTION-FIX-2026-10-18.md`。启动脚本现在检查真实公网请求，失败会重建隧道；App 的“偏好 → 服务连接 → 重新连接”可粘贴新入口。

Node 仅监听 127.0.0.1:8787。Cloudflare 官方签名的 cloudflared 建立 HTTPS 临时连接，不开放 Windows 入站端口。App 只持有这个本机服务的配对令牌，阿里云 API Key 不进入 APK。

临时连接地址在 tunnel 重建后可能变化。启动脚本会生成新的入口；手机打开入口并点“连接 App”，即可重新配对，不必选模型、填 API Key 或重新安装。

当前转发路径采用短回答的 JSON 响应，以适配临时连接。服务的 SSE 能力仍保留，可在以后有稳定域名/服务器时开启。网络和模型都存在等待时间，不能保证零延迟。

## 本机管理

```powershell
# 幂等启动：不会重复创建已有服务或 tunnel
.\scripts\start-service.ps1

# 代码更新后，仅重启 Node，保留已建立的临时连接
.\scripts\start-service.ps1 -Restart

# 停止当前项目登记的服务和 tunnel
.\scripts\stop-service.ps1

# 构建可公开分享的未配对 APK，具体文件名按新版本调整
.\scripts\build.ps1 -JavaHome 'C:\path\to\jdk17' -Offline -Unpaired `
  -OutputDirectory '.\deliverables\my-build' -ApkName 'SpeakPen-debug.apk'
```

`server/.env` 是本机服务端配置，包含运营密钥，已排除版本控制。不要复制到 App 或公开文档。

`.local/connection.json`、`deliverables/打开真实对话.html` 含本机服务配对凭据，也已排除版本控制；不应公开分享。

`.local/server-error.log` 和 `.local/tunnel.log` 用于排查连接；程序不记录请求正文、音频或阿里云密钥。`.local/usage.json` 保存每日请求计数；默认每分钟 30 次、每日 500 次，重启不会重置当日计数。此计数涵盖聊天、总结、转写和语音生成的请求数，不是服务商账单金额。

## 真实验证

已通过：

- 阿里云凭据真实鉴权，指定聊天模型可调用。
- 公网 HTTPS 下自由对话、中文求助、复习提问、JSON 总结、WAV 语音转写和云端语音。
- 真实 M4A/AAC 录音上传与转写。
- 浏览器实际录音 → 转写 → 回复；原录音播放、刷新恢复；收藏与复习。
- Android 单元测试、后台测试、lint 与 APK 签名检查。

2026-10-26，项目负责人反馈现有版本已通过真机验证。此前自动化浏览器验证使用合成英文音频作为可重复麦克风输入；不等同于用户真实口音的准确率评测。历史测试详情与实测耗时见 `docs/TESTING.md`。组员 0.5.0 新增的变速与调档行为需单独验收。

## 结构

- `app/`：Kotlin + Compose Android App；Android 8.0+。
- `server/src.mjs`：阿里云聊天、Qwen ASR 协议适配、TTS、鉴权与额度。
- `server/public/`：已接真实接口的浏览器端；对话及复习存浏览器，录音存 IndexedDB。
- `scripts/start-service.ps1` / `stop-service.ps1`：隐藏后台进程与临时 HTTPS 连接管理。
- `server/smoke-live.mjs`：显式运行真实服务测试，会消耗少量服务额度。
- `scripts/test-live-web.py`、`test-asr-m4a.py`：可重复浏览器与音频链路测试。
- `docs/PROVIDERS.md`：历史选型记录；本版以阿里云实测接入为准。

Node 22+，JDK 17，SDK 35，Build Tools 35.0.0。后台无运行时第三方 npm 依赖。QR/浏览器测试工具安装在 gitignore 的 `.tooling`；cloudflared 运行前检查 Cloudflare 官方数字签名。

此交付适合当前个人使用的本机服务场景。电脑关机后服务不可用；需要全天候多人使用时，应迁移到稳定域名和持续运行的服务器，并增加独立用户账户。


