# SpeakPen 学习流程升级交付记录

日期：2026-11-09。源码已实现，语义检索与浏览器话题设置已部署。首次源码交付按要求未打包；用户后续要求生成 APK，已完成下述 dev11 构建。保留原有首页圆柱／抽屉手势修复、固定云配置和独立官方百炼 TTS。

## 已实现行为

1. **词库增强**：会话及新会话默认分别保存启用、当前回合／最近对话、克制／均衡／积极、4／8／12 候选。云端 multilingual-e5-small INT8 ONNX 使用 192 token、mean pooling、L2 归一化；SQLite 保存向量，NumPy 对约六千条做精确余弦召回，之后关键词、词级及所选词库重排。模型 113 MiB、向量约 9 MiB；不使用 PyTorch、Qdrant 或 BGE-M3。手机不下载模型／向量，只传受限的对话片段并按返回 ID 取本地词条。自建或不支持的词库安全回退本地筛选。
2. **场景入口**：点选场景先展示任务简报，包括场景名称、双方角色、目标和示范；先学习与直接开始是不同的显式操作。自建场景采用情境与角色、目标、对话安排的分组全页编辑器，保存前校验，未保存离开有确认。
3. **单词本**：列表突出大号单词、音标、首义和一条例句。全页词条详情按需展开完整多义、词形、其他例句及词典摘录；原始数据、来源和收藏时的完整释义不裁剪。筛选可换行，长词不设固定单行宽度。
4. **渐进跟读**：听示范→分句跟读→关键词替换→角色热身→正式对话。素材保留用户 DOCX 的五个完整场景，练习只摘选关键部分，每次 1–3 句；酒店多片段。第六个工作面试场景保留既有模板并标注应用自编示例。学习阶段、片段、录音、转写及反馈随会话保存；退出只关闭学习面，可从会话继续。转写失败可重试或手写，正式对话只能通过明确按钮启动。AI 只评价转写完整度、语义、关键表达，提示与解析均禁止发音／语调／流利度评分。
5. **话题语料**：表达提示的新入口与生成路径已移除；历史 SCAFFOLD 保留全部已保存层，不能重新生成。话题语料先设置用途（回答／追问／泛话题相关）、数量（4–10，默认 5）、难度（默认／高级表达／地道表达）。上下文只取最新完整用户＋AI 一轮；若最新用户未获回答、无转写或尚无用户消息，明确提示恢复并阻止生成，不退回旧轮。每条必须包含中文解释、英文表达、真实适用情境的示例及翻译；它们是学习示例，不冒充用户经历，不进入正式上下文。Android 与浏览器端协议一致。
6. **多段语音**：全局开关默认关闭，开启后录音仅进本机会话队列；支持试听、删除及指定片段重录，显示段数和总时长。最多 6 段、总计 180 秒，录音上限会结合剩余时长控制。只有提交才逐段 ASR；成功片段复用，失败保留整队，重试只处理未成功片段。全部成功后原子保存一条整合用户消息，再请求一次 AI；AI 失败保留该消息供重试。关闭开关、离页和切换会话保留各自队列；已保存队列随进程恢复。待处理队列阻止普通文字发送及回退上一轮，防止操作对象混淆。默认关闭时沿用原单段流程。

## 修改文件

下列为这批功能的主要实现及配套文件，不包含用户此前已有的首页、Anki 导入和 TTS 改动清单。

| 范围 | 文件（仓库相对路径） |
|---|---|
| 状态与持久化 | `app/src/main/java/com/speakpen/app/PracticeViewModel.kt`、`SettingsUpdates.kt`；`data/Models.kt`、`ConversationConfig.kt`、`ConversationSessions.kt`、`LocalStore.kt`、`SettingsEditorDraft.kt`、新增 `LearningUpgrade.kt`、`ScenarioMaterial.kt` |
| App 协议 | `network/AiClient.kt`、`LearningPrompts.kt`、`LearningFeatureProtocol.kt`、`VocabularyPrompts.kt`；新增 `LessonProtocol.kt`、`TopicBankProtocol.kt` |
| 原生界面 | `ui/ChatScreen.kt`、`Dialogs.kt`、`SettingsScreen.kt`、`DictionaryScreen.kt`、`WordDetailDialog.kt`、`LearningScreens.kt`；新增 `LearningUpgradeScreens.kt` |
| 语音及素材 | `speech/SpeechController.kt`；`app/src/main/assets/scenarios/material.json`；`scripts/import-scenario-material.py` |
| 服务端 | `server/src.mjs`、新增 `server/semantic.mjs`；`server/public/app.js`、`app.css`、`index.html`、`learning.js` |
| 检索服务 | `semantic/service.py`、`requirements.txt`、`test_service.py`；`scripts/fetch-semantic-model.py` |
| 部署 | `deploy/install-semantic.sh`、`refresh-semantic.sh`、`speakpen-semantic.service`、`verify-learning.py` |
| 测试 | 新增 `app/src/test/java/com/speakpen/app/LearningUpgradeTest.kt`；更新相关学习、导航、设置、配置与词库回归测试；新增 `server/test/semantic.test.mjs`、`topic-options.test.mjs`，更新 `learning-actions.test.mjs` |
| 预览与验收 | `design/learning-upgrade-2026-11-09/index.html`；`scripts/test-learning-upgrade.ps1`、`test-learning-preview.py`、`test-topic-panel.py`；`deliverables/learning-upgrade-2026-11-09/` |
| 文档 | 本记录、`docs/UI-COMPONENT-INVENTORY.md`、`UI-COMPONENT-DECISIONS.md`、`CHANGELOG.md`、`CLOUD-DEPLOYMENT.md` |

`data/`、`network/`、`ui/`、`speech/` 的简写均相对于 `app/src/main/java/com/speakpen/app/`。

## 验证结果

| 项目 | 实际结果 |
|---|---|
| Android | `scripts/test-learning-upgrade.ps1` 仅执行 `:app:testDebugUnitTest :app:lintDebug`，209 项 JVM 测试通过，lint 无问题。未运行 assemble、APK 打包或安装。|
| Node | `node --test server/test/*.test.mjs`：58 项通过，包括鉴权、输入限制、超时、服务回退、话题边界、三种用途和三种难度。|
| Python | 本地及云端各 6 项通过，包括排序、词库过滤／去重、输入限制、HTTP 鉴权、并发忙碌退避、资产校验、索引原子更新及模型版本失效。|
| 独立交互预览 | Playwright Edge 360／390／430 × 844px：任务简报、自建编辑／离开确认、聚焦卡、渐进学习／恢复、用途难度组合、失败队列／重试／重启、设置回退通过。增加 150% 字号、长词和长释义，未发现横向溢出／阻挡；控制台错误 0。|
| 实际浏览器端 | 模拟 AI 返回，360／390／430px：三种用途、数量滑块、设置弹层、完整轮边界、取消／未完成轮禁用、无额外用户消息及无旧轮泄入；不产生付费请求。|
| 样式检查 | Impeccable 预览机械检查未报问题；截图人工核对角色对比、学习辅助蓝色、信息密度和长内容。HTML 截图不能代表 Compose 真机布局。|
| 云端 | `speakpen`／`speakpen-semantic`／Nginx 均 active；health `ready:true,speech:true`；无授权检索 401，检索断开返回安全回退；配置及 HTTPS 配置指纹保持一致，额度文件保留。|
| 最小真实模型调用 | 仅 1 次短聊天、1 次短官方 TTS、使用该音频做 1 次 ASR；均成功。WAV RIFF 长度与实际字节匹配，ASR 识别到 juice。语义检索与索引本身不调用付费推理。|

JVM XML 及 lint 报告位于 `%LOCALAPPDATA%/Temp/SpeakPen-learning-check/app/build/`；可交付检查结果及截图位于 `deliverables/learning-upgrade-2026-11-09/`。素材提取保留 DOCX 的 SHA-256；文档正文只作为素材数据读取。

## 部署现状与回滚

- 公网服务：`https://47.118.51.5`，检索接口 `POST /v1/vocabulary/search`，通过既有应用令牌鉴权；Python 仅监听 `127.0.0.1:8791`。2.5 秒网关期限、3 秒 App 期限；检索不可用不会影响聊天／ASR／TTS。
- 索引版本：`fa84a48bec5ea9f7`，6,055 条（4,027＋1,652＋376），模型及 tokenizer 校验哈希纳入索引版本。模型文件不在源码和 App 包内。
- 实测查询：中文酒店订单问题召回 hotel／reserve／book／appointment；英文橙汁问题召回 orange／juice／drink／apple。两次 HTTPS 查询约 26–35ms；这只是小规模连通性／相关性检查，不代表全词表语义质量基准。
- 常驻 worker 内存实测约 454–455 MiB，systemd `MemoryMax=700M`、`CPUQuota=150%`，ONNX 两线程、BLAS 一线程、并发仅 1 次推理，关闭 CPU arena 与动态形状内存预分配。重索引仅暂时停止检索，聊天服务持续运行。
- 服务代码更新前快照：`/opt/speakpen-before-learning-20261109`。最近检索实现／索引／unit 快照：`/opt/speakpen-semantic/snapshots/20261109-172228`。未改写 `/etc/speakpen/speakpen.env`、供应商 Key／服务令牌、额度文件或 HTTPS 配置。
- 检索更新用 `deploy/refresh-semantic.sh`，失败会自动恢复该轮 code／index／unit；首次安装用 `install-semantic.sh`。运行步骤见 [部署文档](CLOUD-DEPLOYMENT.md)。

如需回滚检索，在服务器停止 `speakpen-semantic`，将上述 snapshot 中的 `service.py` 复制至 `/opt/speakpen-semantic/service.py`、`index.sqlite` 复制至 `/var/lib/speakpen-semantic/index.sqlite`、unit 复制至 `/etc/systemd/system/speakpen-semantic.service`，执行 `systemctl daemon-reload` 后启动。也可直接停用检索，App 会安全使用本地筛词。

如需撤回 Node 及浏览器变化，从 `/opt/speakpen-before-learning-20261109/server/` 恢复对应的源码／静态文件，然后重启 `speakpen`。不要恢复或覆盖 `/etc/speakpen`、Nginx 证书配置及 `/var/lib/speakpen` 的现有额度。

## 未做的真机验收

尚未在 Android 真机或模拟器上安装这一轮源码。麦克风授权、单段／多段实录、试听／重录、后台中断、强杀恢复、存储失败、系统返回／IME、TalkBack、字体缩放及小屏 Compose 实际画面需要后续设备验收。没有虚构发音评分；浏览器模拟语音和 AI 只用于交互预览，不冒充原生音频验证。

首次源码交付没有生成 APK；后续构建见下节。

## 后续 APK 交付

- 安装包：[SpeakPen-0.6.0-dev11-cloud-debug.apk](../deliverables/learning-upgrade-2026-11-09/apk/SpeakPen-0.6.0-dev11-cloud-debug.apk)，26,986,768 字节（约 25.7 MiB）。包名 `com.speakpen.app`，versionCode 18，最低 Android 8.0。
- 使用 `pwsh -File scripts/build.ps1 -JavaHome C:/EmojiJdk17 -Offline -Cloud` 构建；固定云端地址及应用令牌已私下核对，凭据不写入公开校验记录。沿用原 debug 签名，与 dev10 证书一致，满足覆盖安装签名条件。
- assembleDebug 成功、209 项 JVM 测试通过（失败／错误／跳过均为 0）、lint 无问题。使用 ASCII 临时路径检查 APK Manifest，核对交付副本哈希一致。
- SHA-256：`61c3ec151847f874c9da8b31d449a537e8fe0310f56328f0424ef46e23447ad6`。同目录保存 `verification.json`、`SHA256SUMS`、测试 XML 与 lint 报告。
- 本次仅构建客户端，云端保持上文部署版本与回滚点。未执行设备安装；真机验收项仍见上一节。
