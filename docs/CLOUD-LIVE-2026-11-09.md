# SpeakPen 云端实装记录

日期：2026-11-09（Asia/Shanghai）。按用户授权通过本机 SSH 公钥连接阿里云 ECS，使用现有平台 API Key，不在聊天中输出凭据。

## 可直接使用的结果

- 云服务：`https://47.118.51.5`；健康检查：`https://47.118.51.5/health`；App 固定地址：`https://47.118.51.5/v1`。
- 私有安装包：`deliverables/cloud-private/SpeakPen-0.6.0-dev08-cloud-debug.apk`，版本 `0.6.0-dev08` / versionCode 15。
- APK SHA-256：`48566406587361cd0cdc514b8f71a831a14233c86faf27705f32e933bb6959d5`。
- 签名证书 SHA-256：`7b0d36c386c7b1db1799d73fa41d67d80fce832d126e9a3ac31d8b1f223dff9f`，与此前检查包一致。

将该 APK 传到手机后覆盖安装，保留原 App 数据，不用卸载。启动后直接使用云服务，设置中不出现“服务连接”及重新配对入口；旧临时隧道配置和外部配对链接不能覆盖云地址。若之前主动开启过“示例体验”，在设置中关闭它以使用真实 AI。

先发一条文字消息，再测试录音识别和回复朗读。最后将电脑关机，用手机移动网络重试，验证手机本身到云服务的链路。没有连接的 adb 设备，未声称已完成手机覆盖安装、麦克风、系统朗读或后台体验验收。

## 服务器现状

现有 Ubuntu 24.04、Node 22、Nginx、`speakpen.service` 均保留。Node 仍只监听 `127.0.0.1:8787`；Nginx 接收公网 HTTPS 443。HTTP 80 对 ACME 验证路径提供文件，其他请求跳转到 HTTPS。服务及 Nginx 已启用开机自启。

没有购买或配置域名，也没有给 App 放开 HTTP 或关闭 TLS 证书校验。使用 Let's Encrypt 正式签发的 IPv4 证书，签发者 YE1，SAN 为 `IP Address:47.118.51.5`，本轮证书有效期截至 2026-11-15 15:18:10（北京时间）。公网通过正常 CA 链校验，证书 SHA-256 为 `e95b0ac0b8c1d4ad9bde4850739bd9181251dfeef7f578b62084c9aba60b5119`。

新增 `deploy/configure-ip-https.sh` 可重复部署这一配置。Certbot 5.9 / ACME 5.9 使用隔离环境 `/opt/speakpen-certbot`，证书配置 `/etc/speakpen/acme`，工作目录 `/var/lib/speakpen-acme`，日志 `/var/log/speakpen-acme`。不使用 Ubuntu apt 的 Certbot 2.9 管理这张 IP 证书。

`speakpen-certbot-renew.timer` 每 6 小时（带最多 5 分钟随机延迟）检查是否需要续期，续期成功执行 Nginx 配置检查与 reload。带 deploy hook 的 `renew --dry-run` 已通过，正式证书未被测试证书替换。短期证书依赖该定时器及公网 80 保持可用。

Nginx 原配置备份位于 `/etc/nginx/sites-available/speakpen.before-ip-https-*`。平台 Key、原服务令牌、额度文件未重置；没有重新安装应用服务，也没有修改云控制台安全组。公网 80 / 443 实测可达。

## 凭据与模型

平台 Base URL 保持 `https://maas.qianwenaiapi.com/compatible-mode/v1`，聊天模型 `qwen3.7-flash-2026-07-15`，识别模型 `qwen3-asr-flash`，已使用现有 Key 真实请求验证。

平台 Key 始终留在服务器 `/etc/speakpen/speakpen.env`；本地只经 SSH 获取独立的应用访问令牌，保存于 Git 忽略的 `.local/cloud-connection.json`，再通过 `scripts/build.ps1 -Cloud` 写入私有 APK。该 APK 只分发给获准使用者；没有上传到公开 GitHub Release。

当前聊天与识别继续走自定义平台，回复朗读使用独立的官方百炼中国内地地域 Key。服务端不会把自定义平台 Key 发到官方地址；`/v1/audio/speech` 已真实返回 `audio/wav`。百炼流式 WAV 的占位长度会在服务端规范化，实测为 24 kHz、16-bit、单声道，3.04 秒，便于 Android 播放器正确判断结束位置。若官方接口失败，App 仍按现有实现回退到 Android 系统朗读。

服务鉴权保持开启。默认额度仍为整个服务合计每日 500 次、每分钟 30 次；本次真实检查使用了少量请求。电脑关机不影响云服务，手机历史、录音和积累仍保存在手机。

## 本轮验证

| 项目 | 结果 |
|---|---|
| 公网 HTTPS 与证书 IP SAN | 正常 CA 校验通过，无需忽略证书错误 |
| `/health` | 200，`ready:true` |
| 未授权 `/v1/status` | 401 |
| App 令牌 `/v1/status` | 200 |
| 真实文字聊天 | 200，收到 `Hello from SpeakPen.` |
| 真实流式聊天 | 200，SSE 类型正确且包含 `[DONE]` |
| 英文 WAV 真实转写 | 200，识别出 coffee shop 测试句 |
| 独立官方百炼 TTS | 200 `audio/wav`；RIFF 长度已规范化，标准解析时长 3.04 秒 |
| IP 证书续期模拟与 deploy hook | 全部成功 |
| Android 构建 | assembleDebug 通过；192 项 JVM 测试，0 失败 / 错误 / 跳过；lint 无问题 |
| 云配置与签名 | FIXED_SERVICE=true，IP HTTPS 地址及应用令牌匹配，APK 签名通过 |

公网验证的脱敏结果保存在 `deliverables/cloud-private/cloud-smoke-verification.json`；Android 报告在同目录 `reports/` 和 `test-results/`。构建沿用前一轮组员代码和 Anki 多义、例句、摘录等修改，未推送 GitHub。

## 管理命令

在服务器终端执行：

```bash
systemctl status speakpen nginx --no-pager
systemctl list-timers speakpen-certbot-renew.timer --no-pager
journalctl -u speakpen-certbot-renew.service -n 30 --no-pager
curl -fsS https://47.118.51.5/health
```

手动验证续期（无需每天手动执行）：

```bash
/opt/speakpen-certbot/bin/certbot renew --cert-name speakpen-ip \
  --config-dir /etc/speakpen/acme --work-dir /var/lib/speakpen-acme \
  --logs-dir /var/log/speakpen-acme --dry-run
```

续期维护 80 端口可达；不要禁用 `speakpen-certbot-renew.timer`。如果今后更换公网 IP，需要重新签发并更新 App 的固定服务地址。完整重建流程见 [部署教程](CLOUD-DEPLOYMENT.md)，IP 证书来源与版本要求见 [核验记录](research/IP-HTTPS-2026-11-09.md)。
