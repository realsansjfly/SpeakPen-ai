# 空白话题与局部渐变动效增量

2026-10-31，本地 **0.6.0-dev06／versionCode 13**，基于 dev05 继续增量修改。分支 main、HEAD 4e705415d770fbb1a3d5851ea3bb2c583c4c6381；本批及此前改动仍在工作区，未提交、推送或发布。

## 话题内容与随机方式

话题从原先少量固定内容扩充到 **440 条**：240 条筛选后的 CC0 英文破冰问题，加 200 条原创中文话题。优先调查已有开放素材，采用来源和许可明确的部分；未把混合第三方出处的 MIT 问题集合直接整库搬入。来源、许可、原文行号、固定版本与哈希见 [资源核查](research/topics-motion-2026-10-31/SOURCES.md)。本地资源不请求模型，不增加接口费用。

`assets/topics/catalog.json` 是实际打包文件，`speakpen-zh.txt` 保留易维护的中文文本；`scripts/build-topic-catalog.py` 可从固定英文原文和中文文本重建。解析检查数量、长度、ID 与文字去重。英文条目保留原文和来源行号，中文有独立来源标记，不把这些话题称作英语等级教材。

`data/TopicCatalog.kt` 的 `TopicSampler` 以系统 `SecureRandom` 产生随机数，洗牌后逐批取 18 条，每行 6 条；相邻两批没有重复。三行起点分别随机选择，不使用会话 ID、日期或固定种子。这里的“随机”指系统熵初始化的安全随机实现，不承诺物理随机源或永久不重复。进程内抽样器共享洗牌队列；进程重建后重新随机。

`PracticeViewModel` 延迟从 assets 读取目录；`TopicSuggestions` 在空白页进入、抽屉关闭／返回前台、空白会话切换或新建时刷新。新建复用空白会话时仍通过 `topicVisitVersion` 触发新批次。刷新在 IO 协程执行，防止重复点击；读取失败显示错误并可重试。“换一批”只换本地建议，不清空草稿或发送消息。点话题仍然填入草稿，由用户发送。

话题流从固定总时长改为恒定 **26／32 dp 每秒**，长短话题的速度一致。触摸或焦点暂停所在行，输入、后台、抽屉等状态暂停；关闭系统动画或开启触摸探索时使用可手动横滑的静态行，重复副本不进入无障碍语义。小高度仍减少行数，页面可纵向滚动。不加回话题说明和暂停整行。

## 渐变变化

`ui/AmbientNoise.kt` 将 Ashima Arts／stegu 的 MIT `noise2D.glsl` 适配为 Kotlin 标量 Simplex 噪声，原始源码及完整许可证留存，并将许可证打包到 `assets/licenses/webgl-noise-MIT.txt`。没有增加新的动画运行库或要求特定 GPU shader API。

`ConversationBackdrop` 在原来的纸面、浅绿、浅蓝中叠加四片柔和径向渐变。连续噪声分别控制局部位置、半径和透明度，替换机械的固定往返。每次绘制只采样少量噪声，不计算逐像素噪声；状态在绘制层读取，以约 30 Hz 更新，避免每帧重组聊天消息。30 Hz 是更新节奏目标，不是设备帧率实测结果。

离开页面、抽屉、后台、录音、播放、复盘／重说阅读面和场景选择期间暂停，恢复延续时间，避免跳动。`AmbientMotion` 监听系统动画开关及触摸探索设置，关闭后保持静态。保留现有字体、绿白消息、蓝色学习提示和导航。

## 验证与边界

- 本批 Android assemble／单元测试／lint 通过：169 项测试，0 失败、错误、跳过，lint 0；含新增 7 项话题／噪声测试。覆盖连续 1,000 批去重与随机起点、全库可达、最小池重填、资源和噪声连续性。
- 本批 Node 46 项通过。浏览器预览检查 50 批可遍历全部 440 条、相邻无重复、保留草稿、320／390／1000 宽度无横向溢出、减少动画模式和背景变化，无 JS 错误；目视检查预览截图，保持低对比及清晰消息层级。HTML 不等于 Android 渲染或帧率验收。
- 87 个构建输入与 ASCII 镜像一致，11 个打包资源逐字节一致；同应用 ID、与 dev05 同签名、Unpaired 字段为空、已知凭据扫描通过。[机器核验](../deliverables/topics-motion-2026-10-31/verification.json)。
- 本机及现用公网 status 均 200、ready、同一实例；未重启服务／隧道，未调用收费 AI／ASR。
- adb 无设备。实际覆盖安装与旧数据、原生触摸／焦点、TalkBack／大字体、动效观感、低端机帧率／电耗未验收；用户对最终动效的审美确认仍待实际体验。

## 复现

```powershell
python scripts/build-topic-catalog.py
.\scripts\build.ps1 -JavaHome 'C:\EmojiJdk17' -Offline -Unpaired `
  -OutputDirectory '.\deliverables\topics-motion-2026-10-31' `
  -ApkName 'SpeakPen-0.6.0-dev06-debug.apk'
python scripts/test-topics-motion-preview.py
# 传入本次 build.ps1 输出的 ASCII 构建目录
python scripts/verify-topics-motion-build.py <build-mirror>
```

最终交付见 [dev06 说明](releases/v0.6.0-dev06.md) 和 [安装说明](../deliverables/topics-motion-2026-10-31/README.md)。历史 APK、报告、队友文件及研究全部保留。
