# 从阿里云 Workbench 部署 SpeakPen

适用：截图中的阿里云 ECS、Ubuntu 24.04、2 核 2G。截图显示 SSH 已连接，`root@…:~#` 后面就是输入命令的位置。阿里云 ECS 是运行应用的电脑；千问／百炼 API 是模型服务，两者分别开通和计费。服务器不在本地运行大模型。

2026-11-09 已通过 SSH 完成该 ECS 的 IP HTTPS 配置，实际服务地址为 `https://47.118.51.5`，无需先购买域名。真实聊天、流式回复和英文语音转写已验证。部署结果、APK 和管理命令见 [云端实装记录](CLOUD-LIVE-2026-11-09.md)。以下步骤保留供重建或迁移服务器参考。

## 连接关系

手机 App／浏览器 → HTTPS 公网 IP 或域名 → Nginx → 本机 Node 127.0.0.1:8787 → 配置的千问兼容 API。

App 的录音、对话、收藏仍存手机；浏览器数据仍存浏览器。搬到云上的是 API 转发和浏览器页面，不是把手机历史上传或自动跨设备同步。电脑关机后，云服务继续运行。

## 第一步：在阿里云控制台放行端口

1. 保留现在这个 Workbench 页面。另开阿里云控制台 → ECS → 实例，找到这台服务器。
2. 进入“安全组”→ 已绑定安全组 → 入方向，添加 TCP **80**、**443**。需要手机随处访问时，来源填 `0.0.0.0/0`。
3. 保留原有的 SSH／Workbench 登录规则；不要删除原规则。**8787 不需要开放**，由 Nginx 在本机转发。
4. 在实例详情确认“公网 IP”。截图里是 `47.118.51.5`，如果控制台显示不同，以控制台为准。`172.17.5.108` 是内网地址，不用于手机公网访问。

如果自己启用过 UFW，再在服务器终端执行 `ufw status`；只有状态为 active 时，按已有 SSH 端口先允许 SSH，再执行 `ufw allow 80/tcp`、`ufw allow 443/tcp`。本教程不新开启防火墙。

## 第二步：上传本轮代码

本轮新脚本尚未推送 GitHub。请使用本地生成的部署 ZIP，单纯 `git clone` 只会拿到远端组员版本。

本地文件：`deliverables/SpeakPen-cloud-deploy.zip`。它只含服务源码、四个网页运行文件、部署脚本及本教程，没有 API Key、连接令牌、APK 或 Anki 原包。重新打包可在**电脑 PowerShell**运行：

```powershell
Set-Location 'F:\Agent project\ai口语对话笔'
.\scripts\package-cloud.ps1
```

在 Workbench 左侧找“文件”／文件管理图标，进入 `/root`，选择“上传文件”，上传这个 ZIP。若该入口无法上传，也可在**电脑 PowerShell**执行下面命令（按控制台实际 IP 修改；SSH 密码在本机提示时输入）：

```powershell
scp '.\deliverables\SpeakPen-cloud-deploy.zip' root@47.118.51.5:/root/
```

然后回到**截图里的服务器终端**，逐行执行：

```bash
apt-get update
apt-get install -y unzip
mkdir -p /opt/speakpen
unzip -n /root/SpeakPen-cloud-deploy.zip -d /opt/speakpen
cd /opt/speakpen
bash deploy/install-ubuntu.sh
```

初次安装用 `unzip -n`，避免误盖已有配置。安装脚本会安装 Node 22、Nginx、证书工具，创建 `speakpen` 系统用户和 systemd 服务。需要安装 Node 时使用 NodeSource 的签名 APT 仓库；不会运行 `curl | bash`。

如果下载超时，保留错误文字后重试这一条脚本。不要把失败时的后续步骤当成已成功。看到“安装完成”后进入下一步。

## 第三步：配置平台 API Key 与接口地址并启动

使用平台提供的 API Key 和配套 Base URL，二者必须对应；不根据 Key 是否以 `sk-` 开头判断有效性。默认配置是中国内地百炼 `https://dashscope.aliyuncs.com/compatible-mode/v1`。如果使用阿里云百炼／Model Studio，确认 Key 地域及模型调用额度。

在**服务器终端**执行：

```bash
cd /opt/speakpen
bash deploy/set-api-key.sh
```

如果使用你提供的千问兼容平台地址，改为运行：

```bash
bash deploy/set-api-key.sh 'https://maas.qianwenaiapi.com/compatible-mode/v1'
```

这个命令同时写入平台 Key 和配套 Base URL。可选第二个参数设置该平台实际支持的聊天模型 ID，例如 `bash deploy/set-api-key.sh 'https://maas.qianwenaiapi.com/compatible-mode/v1' '平台模型ID'`；不要直接照抄占位模型。未提供时保留现有模型。

如果旧脚本曾提示“格式不正确”，先重新上传更新后的部署 ZIP，执行 `unzip -o /root/SpeakPen-cloud-deploy.zip -d /opt/speakpen`，再运行上面的命令。无需重装依赖、重跑安装脚本或重置应用服务令牌。

粘贴 Key，按回车。输入时没有字符和星号是正常的。Key 保存到 `/etc/speakpen/speakpen.env`，只有 root 能读；不要把 Key 发到聊天或放进 APK。

接着执行：

```bash
systemctl status speakpen --no-pager
curl -fsS http://127.0.0.1:8787/health
```

成功标准：服务为 `active (running)`，健康检查含 `"ready":true`。这表示已配置模型凭据，不代表已真实调用模型。用**电脑或手机浏览器**打开 `http://47.118.51.5/health`（替换实际 IP），应得到同类 JSON。

暂时没有域名也可以做到这里。HTTP 健康检查用于排障；浏览器麦克风和正式 App 使用下一步的 HTTPS 地址。

模型设置沿用项目已有配置：聊天 `qwen3.7-flash-2026-07-15`、识别 `qwen3-asr-flash`、官方百炼朗读 `qwen3-tts-flash`。聊天和识别可以继续使用自定义兼容平台；朗读必须配置独立的官方百炼中国内地地域 Key。服务器执行 `bash /opt/speakpen/deploy/configure-official-voice.sh` 后按隐藏提示粘贴 Key，详见 [云端语音](CLOUD-TTS-2026-11-09.md)。如果平台不支持聊天或识别模型，在服务器执行 `nano /etc/speakpen/speakpen.env`，按平台控制台可调用的模型改对应行，Ctrl+O、回车保存，Ctrl+X 退出，再 `systemctl restart speakpen`。

自定义平台的聊天兼容性不代表支持同样的语音接口。聊天及识别会请求配置的 Base URL；识别还要求平台支持 Qwen ASR 的 input_audio 协议。自定义平台 Key 永远不会发送给官方百炼 TTS 地址；只有单独保存的 `DASHSCOPE_TTS_API_KEY` 会用于朗读。未配置或生成失败时，Android 继续使用系统朗读回退。

## 第四步：配置 HTTPS（公网 IP 或域名任选）

### 没有域名：直接给公网 IP 配置 HTTPS

Let's Encrypt 现已正式支持公网 IP 证书，不需要域名。服务器目前已完成此步骤，下面的命令仅供重建参考。在已安装 SpeakPen 的 Ubuntu 24.04 服务器执行：

```bash
cd /opt/speakpen
bash deploy/configure-ip-https.sh 47.118.51.5
```

脚本在独立 Python 环境安装 Certbot 5.9（系统自带的 2.9 不支持 IP），申请短期证书，配置 Nginx 和每 6 小时检查一次的 `speakpen-certbot-renew.timer`。HTTPS 443 和 ACME 验证用的 HTTP 80 都须保持公网可达；不需要开放 8787。证书目录为 `/etc/speakpen/acme`，不与系统版 Certbot 混用。

验证续期需使用这个新版 Certbot：

```bash
/opt/speakpen-certbot/bin/certbot renew --cert-name speakpen-ip \
  --config-dir /etc/speakpen/acme --work-dir /var/lib/speakpen-acme \
  --logs-dir /var/log/speakpen-acme --dry-run
systemctl list-timers speakpen-certbot-renew.timer --no-pager
```

然后打开 `https://47.118.51.5/health`，应无证书警告且 `ready` 为 true。App 地址使用 `https://47.118.51.5/v1`。IP 证书有效期约 160 小时，依赖上述自动续期；更换公网 IP 后需重新签证书并构建 App。官方依据见 [IP HTTPS 核验记录](research/IP-HTTPS-2026-11-09.md)。

### 已有域名：使用原有域名流程

如果选择域名，需要一个你控制的域名，例如 `speakpen.example.com`；示例不能直接照抄。截图是杭州节点，中国内地服务器通过域名提供网站服务通常需要完成 ICP 备案，请先在阿里云核查该域名的备案和接入状态。已经使用 IP HTTPS 时，不必再执行下面的域名脚本，也无需关闭 App 证书校验。

1. 阿里云域名／云解析 DNS → 你的域名 → 添加记录。
2. 记录类型 **A**，主机记录如 `speakpen`，记录值填服务器公网 IP；保存。没有服务器 IPv6 地址时，不要另加 AAAA 记录。
3. 在电脑执行 `nslookup speakpen.你的域名.com`，确认解析结果指向这台服务器。
4. 确认安全组已开放 80／443，在**服务器终端**执行（换成自己的域名及邮箱）：

```bash
cd /opt/speakpen
bash deploy/configure-https.sh speakpen.你的域名.com 你的邮箱@example.com
```

脚本申请 Let's Encrypt 证书，启用 HTTP → HTTPS 跳转和自动续期。域名解析、80 端口或备案接入未就绪时，申请可能失败；按报错处理后重试，已有证书配置不会被脚本覆盖。

接着执行：

```bash
certbot renew --dry-run
```

在手机浏览器打开 `https://你的实际域名/health`。必须没有证书警告，且 `ready` 为 true。

## 第五步：验证鉴权和真实千问调用

部署脚本已经随机生成应用服务令牌，和百炼 API Key 分开。服务器默认全体设备合计每分钟最多 30 次付费接口请求、每天最多 500 次；使用量保存在 `/var/lib/speakpen/usage.json`，重启不清零。

先在**服务器终端**执行以下整段（不输出令牌）：

```bash
set -a
. /etc/speakpen/speakpen.env
set +a
curl -fsS -H "Authorization: Bearer $SERVICE_ACCESS_TOKEN" http://127.0.0.1:8787/v1/status
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8787/v1/status
```

第一条应返回服务 JSON；第二条应为 **401**，说明未授权请求无法使用接口。

再执行一次真实聊天（会消耗一次请求及少量百炼额度）：

```bash
curl -fsS http://127.0.0.1:8787/v1/chat/completions \
  -H "Authorization: Bearer $SERVICE_ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"Say hello in one short English sentence."}],"stream":false}'
unset DASHSCOPE_API_KEY SERVICE_ACCESS_TOKEN
```

成功时返回 `choices`，其中包含英文回复。`/health` 为 true 而这里失败，通常检查 Key 地域、额度、模型名称和服务器到百炼的网络。

## 第六步：构建无需“连接 AI”的 App

当前代码有两种构建：普通开发包保留手动配对；**`-Cloud` 构建隐藏设置里的“服务连接”和重新配对入口，直接使用固定云地址**。覆盖安装时旧的临时隧道地址不会覆盖云地址，外部配对链接也不再改写连接。

在**服务器终端**只查看应用服务令牌：

```bash
grep '^SERVICE_ACCESS_TOKEN=' /etc/speakpen/speakpen.env
```

只复制 `=` 后面的 64 位字符。它是允许 App 使用你服务的令牌，不是 `DASHSCOPE_API_KEY`。随后在**电脑 PowerShell**执行：

```powershell
Set-Location 'F:\Agent project\ai口语对话笔'
.\scripts\configure-cloud-app.ps1 -BaseUrl 'https://47.118.51.5/v1'
```

提示时粘贴刚才的 64 位令牌，回车。再构建：

```powershell
.\scripts\build.ps1 -JavaHome 'C:\EmojiJdk17' -Offline -Cloud `
  -OutputDirectory '.\deliverables\cloud-private' `
  -ApkName 'SpeakPen-0.6.0-dev08-cloud-debug.apk'
```

上面 JDK 路径适用于本项目当前电脑，换电脑按实际 JDK 路径调整；Gradle 依赖未缓存时去掉 `-Offline`。本机配置写入已忽略的 `.local/cloud-connection.json`，不会覆盖原来的电脑隧道配对文件。

将新 APK 覆盖安装到原 App（保持原签名），不用卸载。首次打开应直接连接，设置中不再出现服务连接模块。检查：文字聊天 → 录音识别 → 回复朗读 → 断网错误提示 → 恢复网络重试；最后将电脑关机，再用手机移动网络重测。

云 APK 携带应用服务令牌，应只分发给获准使用的人；不内置百炼 Key。当前是小组共用一个访问令牌和服务额度，尚无独立用户登录／计费。不要把这个私有包作为公开下载附件。

浏览器端打开 `https://你的实际域名/#token=64位应用服务令牌` 即可连接，页面会读取并从地址栏移除令牌。也可在网页连接框粘贴这个完整入口；不能只粘贴裸令牌。浏览器端保留配对 UI。部署包不含 APK，所以网页下载按钮暂不可用；通过电脑直接分发已构建的私有 APK。

## 日常管理、更新和回退

### 2026-11-09：轻量语义检索

已部署量化 multilingual-e5-small ONNX＋SQLite／NumPy 检索，索引 6,055 条现有 Anki 数据。模型／索引只驻留服务器；无 Qdrant、PyTorch 或 BGE-M3。`speakpen-semantic` 仅监听 127.0.0.1:8791，Node 的 `/v1/vocabulary/search` 复用已有应用鉴权并限制输入、候选、词库和超时；失败返回本地筛词回退标记。详细测试、哈希来源及快照见 [学习流程交付记录](LEARNING-UPGRADE-2026-11-09.md)。

首次安装需要将 `semantic/`、`deploy/` 和 Anki JSON／manifest 放在 `/opt/speakpen/` 对应位置（词表到 `semantic/assets/`），运行 `bash deploy/install-semantic.sh`。模型无法直接下载时，在工作站运行 `python scripts/fetch-semantic-model.py`，验证固定 SHA-256 后通过已有 SSH 上传到 `/opt/speakpen-semantic/model/`。依赖固定于 `semantic/requirements.txt`。现有部署更新运行：

```bash
cd /opt/speakpen
bash deploy/refresh-semantic.sh
python3 deploy/verify-learning.py
```

更新脚本锁定重建任务，先保存 code／index／unit 快照；短暂停止检索后原子更新，失败自动回滚。不要运行两份索引进程，不要覆盖 `/etc/speakpen/speakpen.env`。`verify-learning.py` 不打印凭据；默认检查 health／鉴权／真实语义召回且不调用付费模型。只有明确需要聊天／ASR／TTS 联测时才加 `--paid`（各一次）。`--fallback-probe` 会短暂停止检索并验证安全回退，随后在 finally 中恢复并等待 worker 就绪。

当前 Node 更新前快照 `/opt/speakpen-before-learning-20261109`，最近检索快照 `/opt/speakpen-semantic/snapshots/20261109-172228`。回滚只恢复服务代码／索引／unit，保留令牌、Key、额度文件和 HTTPS。设备端本轮未生成 APK。

查看／重启：

```bash
systemctl status speakpen --no-pager
journalctl -u speakpen -n 60 --no-pager
systemctl restart speakpen
```

开机自动启动已启用。系统更新导致重启后可用上面的状态命令确认。`nginx -t` 检查反向代理；`certbot certificates` 检查证书。

更新时先在电脑重新生成部署 ZIP，上传新文件，再在服务器执行：

```bash
cp -a /opt/speakpen /opt/speakpen-backup-$(date +%Y%m%d-%H%M%S)
unzip -o /root/SpeakPen-cloud-deploy.zip -d /opt/speakpen
systemctl restart speakpen
curl -fsS http://127.0.0.1:8787/health
```

ZIP 不含 `/etc/speakpen`、`/var/lib/speakpen` 或 Nginx 的已签证书配置，因此正常代码更新保留密钥、额度及 HTTPS。不必每次重跑安装脚本。需要同步修改过的 systemd 配置时，重新安装 `deploy/speakpen.service` 到 `/etc/systemd/system/`，执行 `systemctl daemon-reload` 后再重启。

回退时将实际备份目录里的 `server/src.mjs` 和 `server/public/` 复制回 `/opt/speakpen/server/`，然后重启服务。密钥仍保留在 `/etc/speakpen/speakpen.env`。

| 现象 | 检查 |
|---|---|
| 公网 health 打不开，本机可以 | 公网 IP、ECS 安全组 80／443、已有 UFW、Nginx 状态 |
| HTTPS 申请失败 | 域名解析是否生效、80 是否可达、是否有错误 AAAA、备案接入、证书频率限制 |
| `ready:false` | Key 是否保存，是否已重启，使用的配置是否为 `/etc/speakpen/speakpen.env` |
| 401 | App 的服务令牌和服务器是否一致；服务令牌不能填成百炼 Key |
| 429 | 项目日／分钟限额，或百炼账户额度；按实际需要调整后重启 |
| 502 | 百炼 Key 地域、模型权限／名称、额度、服务器出口网络 |
| 网页能打开但录不了音 | 使用 HTTPS，并给浏览器麦克风权限 |
| 覆盖安装后还连旧隧道 | 确认安装的是 `-Cloud` 构建，并检查安装包版本 |

本轮自动化验证结果及设备／云端未验收项目见 `docs/CLOUD-ANKI-2026-11-08.md`。
