# SpeakPen 0.6.0-dev05

第 4、5 批本地开发包，2026-10-31。应用 ID `com.speakpen.app`，versionCode 12。

安装 [SpeakPen-0.6.0-dev05-debug.apk](https://github.com/realsansjfly/SpeakPen-ai/releases/download/v0.6.0-dev05/SpeakPen-0.6.0-dev05-debug.apk) 时选择覆盖安装，不卸载旧版。包名和调试签名与 dev04 保持一致，现有学习记录、词库、录音和服务配对沿用。公开包不内置服务地址或凭据；首次安装通过现有连接入口配对。真实升级与数据迁移仍需设备验收。

本包包含分层提示、优化后重说、默认关闭的旁路纠错、最新／固定场景复盘、Markdown 导出及成功奖励／统计，并完成文案清理和低对比慢速渐变。功能入口与行为见 [实施记录](../../docs/LEARNING-RECAP-BATCH-2026-10-31.md)。未提交、推送或发布；历史安装包均保留。

## 证据

最终验证：Android 162 项通过，lint 0 项，Node 46 项通过；76 个源码输入与构建镜像一致。同包名同签名、Unpaired 及已知凭据扫描通过，详见机器核验。

- [机器核验](verification.json)：包身份、签名、源码／镜像一致性、Unpaired、已知凭据扫描及测试汇总。
- `reports/`、`test-results/`：本包 Android 单测及 lint 证据。
- [合成生产请求](learning-recap-synthetic-requests.json)：分层、优化、纠错、复盘四个请求，不含真实对话或运营凭据；仅供后续单独付费联调。
- [Markdown 样本](recap-format-sample.md)：生产格式化器生成，证明结构与引用校验，不代替设备导出。
- [移动布局预览](ui-flow-preview-mobile.png)及 [HTML](../../design/learning-recap-2026-10-31/index.html)：布局方向，不是 Android 截图。
- [服务状态](service-status.json)：本批本机／公网鉴权 status；没有发起收费 AI／ASR，没有重启服务或隧道。

## 复现构建

在项目根目录运行：

```powershell
.\scripts\build.ps1 -JavaHome 'C:\EmojiJdk17' -Offline -Unpaired `
  -OutputDirectory '.\deliverables\learning-recap-2026-10-31' `
  -ApkName 'SpeakPen-0.6.0-dev05-debug.apk'
```

构建脚本使用独立 ASCII 镜像、执行 assembleDebug／testDebugUnitTest／lintDebug，并复制结果。执行后将输出的镜像路径传给 `python scripts/verify-learning-recap-build.py <镜像路径>`。Node 测试在 `server/` 执行 `npm test`，不访问收费接口。

## 待验收

没有连接手机，也没有已安装的模拟器镜像。覆盖安装和旧数据读取、麦克风／回听、系统文件导出、旋转／键盘、大字体／TalkBack、背景帧率与设备存储故障未验证。真实模型的提示质量、原意保留、纠错准确率、复盘内容及端到端延迟未验证；不得用本地协议测试替代这些结论。

长对话超出复盘容量会报错并保留原文档；没有静默丢弃历史或自动增加多次付费调用。容量及完整限制见实施记录。
