# SpeakPen 0.6.0-dev07

表达提示格式兼容修复，versionCode 14。与 dev06 同应用 ID 和签名，可覆盖安装，无需卸载；沿用旧记录、录音、草稿和设备配对。公开包保持 Unpaired，未嵌入现用入口或令牌。

[安装包](https://github.com/realsansjfly/SpeakPen-ai/releases/download/v0.6.0-dev07/SpeakPen-0.6.0-dev07-debug.apk) · [机器核验](verification.json) · [SHA-256](SHA256SUMS.txt) · [修复与边界说明](../../docs/releases/v0.6.0-dev07.md)

截图中的错误由分层提示回包校验触发，不代表用户句子为空。新版本加强三层中英文与填空句型的输出要求，并兼容常见成对占位符；不完整结果会显示具体缺失类型。不会自动追加收费请求，对话与原提示保留。

Android 170 项通过，lint 0，同签名／源码镜像／Unpaired／已知凭据扫描通过。本机和公网服务 status 正常，未调用收费 AI／ASR，无设备验收。缺少这次失败原始回包，修复后的实际模型稳定性仍需验证；不承诺消除所有格式失败。
