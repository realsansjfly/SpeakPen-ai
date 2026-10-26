# SpeakPen · 开口 — 0.6.0-dev02 开发预览版

当前发布：[v0.6.0-dev02](https://github.com/realsansjfly/SpeakPen-ai/releases/tag/v0.6.0-dev02)。Release 提供同签名 Debug APK、SHA-256 校验文件和测试报告；本版为预发布，新增交互尚待真机验收。更新内容与安装说明见 [版本说明](docs/releases/v0.6.0-dev02.md)，历史记录见 [CHANGELOG](docs/CHANGELOG.md)。

公开 APK 不内置服务地址或配对令牌。已有设备可覆盖安装并沿用已保存的配对；首次安装后，在“设置 → 服务连接 → 重新连接”粘贴自己的私密配对入口。下文“直接使用”为 0.4.0 历史说明，当前版本请按版本说明安装。

2026-10-26 开发进度：已实现本轮第 1 批会话配置、三档表达难度和高级参数；开发包为 0.6.0-dev01，其余新功能仍待后续批次。实现范围、验证证据和独立构建／安装命令见 [本轮实施记录](docs/IMPLEMENTATION-2026-10-26.md)。下文保留现有 0.4.0 使用说明；现用服务和旧安装包没有被替换。

首次体验修复已更新为 **0.6.0-dev02**：设置页区分“当前对话／新对话默认”，优化提示插入原句下方并可折叠，话题语料强化中英解释。新包、提示词入口和测试证据见 [第一批调试记录](docs/DEBUG-FIRST-BATCH-2026-10-26.md)。

真实 AI 已接通：聊天 `qwen3.7-flash-2026-07-15`，语音识别 `qwen3-asr-flash`，云端朗读 `qwen3-tts-flash`。

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

# 编译：自动读取 .local/connection.json 的本机服务地址和配对令牌
.\scripts\build.ps1 -JavaHome 'C:\path\to\jdk17' -Offline
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


