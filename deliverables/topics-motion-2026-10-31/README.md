# SpeakPen 0.6.0-dev06

本地开发包，versionCode 13，应用 ID `com.speakpen.app`。与 dev05 同签名，可直接覆盖安装；不要求卸载，沿用设备中的记录、录音与配对。新安装为 Unpaired，需从抽屉的设置内配对。

本次改动：440 条本地话题、每次进入随机换批与随机起点、“换一批”按钮、更快的话题流，以及 Simplex 噪声控制的柔和局部渐变。不增加模型请求或费用。

- 安装包：[SpeakPen-0.6.0-dev06-debug.apk](https://github.com/realsansjfly/SpeakPen-ai/releases/download/v0.6.0-dev06/SpeakPen-0.6.0-dev06-debug.apk)
- 核验：[verification.json](verification.json)，哈希：[SHA256SUMS.txt](SHA256SUMS.txt)
- Android 测试：[reports/tests/testDebugUnitTest/index.html](reports/tests/testDebugUnitTest/index.html)，169 项通过；lint 0。
- Node：[node-tests.txt](node-tests.txt)，46 项通过。
- 浏览器方案：[独立预览](../../design/topics-motion-2026-10-31/index.html)，[预览核验](preview-verification.json)。仅为布局与动效方向，不是 Android 截图。
- [实施与未验证项](../../docs/TOPICS-MOTION-2026-10-31.md)、[素材来源](../../docs/research/topics-motion-2026-10-31/SOURCES.md)。

服务只读取 status，本机／公网均通过，未重启；未调用收费 AI／ASR。adb 无设备，尚未进行覆盖升级、原生布局、读屏、大字体、动效观感、帧率／电耗验证。

在设备上建议先确认覆盖安装后的旧记录与录音，再反复返回空白自由聊天、点新建和“换一批”，检查内容及起点变化、草稿保留；播放或录音时检查运动暂停，关闭系统动画后检查静态话题可横滑。最后观察浅色场的变化是否适度，消息是否始终清晰。
