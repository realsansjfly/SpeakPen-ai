# 版本记录

完整可下载版本见 [GitHub Releases](https://github.com/realsansjfly/SpeakPen-ai/releases)。每个 Release 记录本次新增、修改、修复、验证与未完成项，并附 APK、SHA-256、核验摘要和测试报告；源码仓库中的本目录保存对应详细说明，[CHANGELOG](../CHANGELOG.md) 提供简要浏览。

| 版本 | versionCode | 本次新增与修改 | 详细说明与下载 |
|---|---:|---|---|
| 0.6.0-dev07 | 14 | 修正表达提示的双语／填空输出要求，兼容成对占位符，区分失败原因 | [说明](v0.6.0-dev07.md) / [Release](https://github.com/realsansjfly/SpeakPen-ai/releases/tag/v0.6.0-dev07) |
| 0.6.0-dev06 | 13 | 440 条本地话题，每次进入随机抽样及起点，“换一批”，更快话题流及 Simplex 噪声渐变 | [说明](v0.6.0-dev06.md) / [Release](https://github.com/realsansjfly/SpeakPen-ai/releases/tag/v0.6.0-dev06) |
| 0.6.0-dev05 | 12 | 分层提示、优化后独立重说、可选实时纠错，固定／最新复盘、Markdown 导出、成功反馈及统计 | [说明](v0.6.0-dev05.md) / [Release](https://github.com/realsansjfly/SpeakPen-ai/releases/tag/v0.6.0-dev05) |
| 0.6.0-dev04 | 11 | 词库来源／许可、内置社区词形、TXT／CSV／粘贴导入预览，按需 AI 释义及相关候选接入 | [说明](v0.6.0-dev04.md) / [Release](https://github.com/realsansjfly/SpeakPen-ai/releases/tag/v0.6.0-dev04) |
| 0.6.0-dev03 | 10 | 场景首个 Android 增量，左侧抽屉、移除底栏、设置返回保存、会话草稿、本地话题流 | [说明](v0.6.0-dev03.md) / [Release](https://github.com/realsansjfly/SpeakPen-ai/releases/tag/v0.6.0-dev03) |
| 0.6.0-dev02 | 9 | 三档难度、会话独立配置、高级参数、双语话题语料及可折叠提示 | [说明](v0.6.0-dev02.md) / [Release](https://github.com/realsansjfly/SpeakPen-ai/releases/tag/v0.6.0-dev02) |

这些均为开发预发布，测试数字属于各版本当时的结果，不代表之后的修改已覆盖验收。dev03～dev07 的源码历史从留存构建镜像归档，APK 原件不重新打包；提交和发布发生在归档时，没有伪造先前的提交日期。版本标签对应各自的源码快照，`source-manifest.json` 记录构建输入及打包来源哈希。

APK 和完整生成报告保存在 Release 附件中，避免每次构建把二进制加入 Git 历史；本机历史文件全部保留。仓库保留说明、核验摘要及合成验证样例。已有跟踪的早期 APK 历史不重写。

后续每次发版：新增 `v版本号.md` → 更新本页与 CHANGELOG → 验证对应源码、APK 和签名 → 提交并打标签 → 发布带附件的 Release。继续区分自动化、收费真实接口、真机和未验证项。
