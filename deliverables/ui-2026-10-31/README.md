# SpeakPen 0.6.0-dev03 Android 体验包

2026-10-31，versionCode 10，应用 ID `com.speakpen.app`。

安装包：[SpeakPen-0.6.0-dev03-debug.apk](https://github.com/realsansjfly/SpeakPen-ai/releases/download/v0.6.0-dev03/SpeakPen-0.6.0-dev03-debug.apk)。

本包包含已批准的左侧抽屉、单行聊天导航、独立设置页与返回自动保存、本地话题流和原生过渡。首页已移除“点话题，填入输入框”及暂停行。沿用原颜色、字体和签名；本地工作区的场景批次也包含在内。

使用手机安装器覆盖安装即可，不需要卸载。已有配对、学习记录和录音沿用设备数据。电脑连接已授权设备后也可执行：

```powershell
& 'C:\Users\Realsansjfly\AppData\Local\Android\Sdk\platform-tools\adb.exe' install -r `
  'F:\Agent project\ai口语对话笔\deliverables\ui-2026-10-31\SpeakPen-0.6.0-dev03-debug.apk'
```

本包使用 `-Unpaired`，没有预置现用服务地址或令牌。全新安装从“左侧抽屉 → 设置 → 服务连接”配对。

自动化验证、签名与 SHA-256 见 [verification.json](verification.json)，详细功能与边界见 [实施记录](../../docs/UI-IMPLEMENTATION-2026-10-31.md)。本轮没有连接 Android 设备，覆盖升级、实际布局、手势、录音、动效观感与存储异常尚未真机验证。真实服务状态检查通过；没有重测 AI／ASR 生成，没有重启服务或隧道。

原本地交付现已归档为独立源码标签及 GitHub 预发布；原包保持不变。
