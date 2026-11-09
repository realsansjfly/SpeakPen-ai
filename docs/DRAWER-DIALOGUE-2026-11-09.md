# 抽屉修复与完整示范对话（dev12）

用户后续要求优先修复抽屉无法关闭，并取消渐进跟读，改为完整对话与语音播放。本记录取代上一轮交付记录中有关跟读界面的现状描述；旧数据与旧交付记录保留。

## 行为与原因

- Material 3 的 `gesturesEnabled` 同时控制抽屉拖动与遮罩点击。首页原先为避免圆柱争抢而一直禁用它，导致菜单打开后也无法从遮罩退出。现在 currentValue 或 targetValue 为 Open 就恢复交互，只在关闭状态保留首页限制；系统返回仍关闭抽屉，录音／保存的导航限制保留。
- 场景选择使用全页滚动区域、浅绿场景行、角色摘要与方向图标，底部保留自建入口。简报主要操作是开始对话，次要操作是查看完整示范。
- 示范页展示五个素材场景的全部原句，包括酒店的 29 条对话；面试明确为应用自编。角色名、正文、句间分隔形成阅读层级，可复制正文，支持整段顺序播放、逐句播放及停止。长文本按不超过 1,800 字符的请求分段，避免网关 2,000 字符上限截断整段内容；沿用官方云 TTS 与系统朗读回退。
- 撤去跟读、录音、转写、反馈和步骤按钮。旧 LessonProgress 数据继续读取，但打开后统一展示完整示范。关闭阅读面不开始对话；切换会话、关闭示范或显式开始对话时取消播放，示范内容不进入正式聊天上下文。

## 修改文件

- `app/src/main/java/com/speakpen/app/ui/Navigation.kt`：抽屉状态与手势。
- `ui/Dialogs.kt`、`LearningUpgradeScreens.kt`、`ChatScreen.kt`：场景选择、简报、完整阅读及恢复入口；`ui/` 相对于上方 Kotlin 路径。
- `app/src/main/java/com/speakpen/app/data/ScenarioMaterial.kt`：保留完整原句与有界播放文本。
- `app/src/main/java/com/speakpen/app/PracticeViewModel.kt`：完整播放、停止、逐句播放与正式对话切换。
- `app/src/test/java/com/speakpen/app/NavigationRegressionTest.kt`、`LearningUpgradeTest.kt`：打开抽屉后的关闭策略、原句完整性和音频请求限制回归。
- `app/build.gradle.kts`、`scripts/build.ps1`：dev12／versionCode 19。
- `design/drawer-dialogue-2026-11-09/index.html`、`scripts/test-drawer-dialogue-preview.py`：独立交互预览及浏览器验证。
- `docs/UI-COMPONENT-INVENTORY.md`、`UI-COMPONENT-DECISIONS.md`、`CHANGELOG.md` 及本记录。

## 验证与安装包

- `scripts/build.ps1 -Cloud -Offline`：assembleDebug 成功；210 项 JVM 测试通过，失败／错误／跳过均为 0；lint 无问题。
- Playwright Edge：360／390／430 × 844px，遮罩关闭、左滑关闭、场景简报、酒店全部 29 条、播放／停止／逐句和返回通过。默认及 150% 字号无横向溢出或底部操作遮挡，控制台错误 0；截图已目视检查。浏览器语音采用 stub，不冒充原生音频验证，也不发起付费请求。Impeccable 机械检测无报告项。
- 安装包：[SpeakPen-0.6.0-dev12-cloud-debug.apk](../deliverables/drawer-dialogue-2026-11-09/SpeakPen-0.6.0-dev12-cloud-debug.apk)，包名 `com.speakpen.app`，versionCode 19，26,986,768 字节，最低 Android 8.0。
- apksigner 验签通过，与 dev11 证书相同，满足覆盖安装签名条件。Manifest、固定云配置及交付副本哈希一致性已检查，未公开任何凭据。
- SHA-256：`9ec68a455eecee1505498a8a4ffc7ff5b8db8308d532855841292cf7a1f0f6b4`。
- 报告、测试 XML、截图、`verification.json` 和 `SHA256SUMS` 位于 `deliverables/drawer-dialogue-2026-11-09/`。

本次仅修改客户端，无云端部署；现有聊天／ASR／官方 TTS 服务与原部署回滚点保持不变。未连接 Android 设备，尚未验证实际遮罩触摸、拖动、系统返回、连续语音、后台中断、TalkBack 和小屏 Compose 显示。HTML 预览不代表真机验收。已生成上述 dev12 APK。
