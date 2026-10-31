# dev04 本地安装与验收

[SpeakPen-0.6.0-dev04-debug.apk](https://github.com/realsansjfly/SpeakPen-ai/releases/download/v0.6.0-dev04/SpeakPen-0.6.0-dev04-debug.apk)，包名 `com.speakpen.app`，versionCode 11，与 dev03 同签名。可直接覆盖安装，不要卸载或清除数据。旧包、旧报告全部保留；本次未提交、推送或发布。

```powershell
& 'C:\Users\Realsansjfly\AppData\Local\Android\Sdk\platform-tools\adb.exe' install -r `
  'F:\Agent project\ai口语对话笔\deliverables\vocabulary-2026-10-31\SpeakPen-0.6.0-dev04-debug.apk'
```

公开包没有预置服务入口和令牌。已有配对继续沿用；全新安装通过抽屉 → 设置 → 服务连接配对。

## 词库体验

1. 设置选择“当前对话”或“新对话默认” → 词库增强。默认关闭，任选一套，返回设置再返回聊天自动保存。难度保持原档。
2. “查看”可搜索词语、查看来源／版本／许可，内置社区标签词形不是官方完整大纲。
3. 导入 TXT／CSV 或粘贴，填写名称和来源，解析预览，核对错误与去重后确认。保存不会自动启用，点击“使用此版本”才修改当前范围。
4. 无释义条目可请求 AI 补充，消耗一次模型请求；预览后确认另存，取消不写入，原文不覆盖。示例模式不伪造 AI 释义。
5. 主回复、优化与语料使用少量相关候选，不强制词汇任务。关闭词库不改变难度。已发起回复和历史重生成保留旧版本与候选。

## 本轮证据与待验

Android 137 项、Node 46 项通过，lint 0；15 组离线候选、同签名／Unpaired／已知凭据扫描通过。[verification.json](verification.json)、[候选样例](vocabulary-offline-evaluation.json)、[方案截图](ui-flow-preview.png)。71 个源码输入与构建镜像一致。

真实服务 status 本机与公网均正常，未调用收费 AI／ASR；adb 无设备。覆盖升级后的旧记录／音频、Compose 布局、大字体／TalkBack、文件选择／旋转、输入法及设备文件系统故障仍待验。方案截图是 HTML 流程，不是 Android 截图。未确认导入仅进程内保留，强杀后需重新导入；确认保存后保留完整原文与来源。

SHA-256：`8d9bcad926a89b9c62335cb11d98e84fdb2d575981e73d24f188782f5164b738`。

第 4、5 批分层提示、优化重说、实时纠错、新复盘／导出、奖励动画／统计展示未在本批完成。完整范围见 [词库实施](../../docs/VOCABULARY-BATCH-2026-10-31.md)。
