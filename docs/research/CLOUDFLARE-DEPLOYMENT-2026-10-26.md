# SpeakPen：摆脱个人 PC 的后端部署选型

调研日期：2026-10-26。检索、官方页面读取使用 Anysearch MCP；结合本仓库 `server/src.mjs`、`scripts/start-service.ps1` 的实际实现评估。本文件是部署建议，未购买服务、上传密钥或执行线上部署。官方配额和价格以购买时页面为准。

## 结论

需要一个持续可访问的云端运行环境，但不一定要租一台传统云服务器。Cloudflare Workers 可以直接运行 SpeakPen 的 AI 代理接口，手机不必再经过你的 PC；通义模型仍由阿里云提供，Workers 负责鉴权、配额、转发、流式返回。

选择顺序取决于用户位置：

- 主要面向中国大陆同学、优先保证语音往返体验：优先评估阿里云大陆轻量服务器/ECS；愿意适配托管运行环境时评估阿里云函数计算 FC。
- 小规模内测、希望少维护服务器，且目标用户实测访问 Cloudflare 合格：Workers Paid 是合理方案，最低每月 5 美元，再加模型、域名及可能产生的其他服务费用。[S1]
- 不推荐继续把本机 Quick Tunnel 当作正式服务；换成固定域名 Tunnel 也不会把代码迁出 PC。[S4]
- 暂不优先选择 Cloudflare Containers：可以复用 Node 服务，但引入容器生命周期、外部持久化和额外计费；当前服务只是轻量 API 代理，无需完整 Linux 容器。[S10][S11]

“云端部署”解决 PC 关机后不可用的问题，不代表无故障、无限并发或所有网络零延迟。原先已经通过的真机验证不等于云端新链路的可用性与延迟验证。

## 1. 先区分 Cloudflare 的几种产品

| 方案 | 能否摆脱你的 PC | 对 SpeakPen 的实际意义 | 建议 |
| --- | --- | --- | --- |
| DNS / CDN | 单独使用不能 | 域名解析、静态缓存、入口安全；不运行现有 AI 后端 | 根据入口需要选用 |
| Quick Tunnel | 后端留在 PC 时不能 | 临时公网地址；进程停止即失效；域名可能变化；无运行时间保证、不支持 SSE、最多 200 个进行中的请求 | 只用于演示 |
| 固定域名 Tunnel | 后端留在 PC 时不能 | 稳定地址和连接方式，仍需要另一端机器持续运行 | 可用于访问已有云服务器，不能替代服务器 |
| Pages 纯静态托管 / Workers Static Assets | 单独使用不能 | 托管介绍页、网页 UI；密钥和动态 AI 请求仍需后端 | 可与 Workers 共用 |
| Pages Functions / Workers | 能 | Cloudflare 托管执行接口代码、调用外部 AI、返回 SSE | 适合改造后的本项目 |
| Containers | 能 | Cloudflare 上运行 Linux 容器，能继续使用 Node HTTP 服务 | 可行，但当前不是优先项 |

依据：[S3][S4][S9][S10][S11]。这里“不能”指该产品单独无法替你运行当前后端，并非 Cloudflare 整个平台没有计算能力。

## 2. Workers 对现有项目的兼容性

现有服务不是本地运行大模型，而是调用阿里云等外部模型。因此不需要 GPU，也不需要为模型准备大量内存。当前主要路径：

```text
Android / 网页
    → 自有 HTTPS 域名
    → Workers：鉴权、限流、预算、输入校验、请求转发
        → 阿里云：英文语音识别 / 对话 / 语音合成
    ← 转写文字 / SSE 回复 / 音频
```

| 当前实现 | Workers 评估 | 迁移动作 |
| --- | --- | --- |
| `http.createServer` | 已有 Node HTTP 兼容支持，不能再笼统说“不支持 Node 服务” | 开启 `nodejs_compat`，使用近期兼容日期及 `httpServerHandler`；或转成标准 `fetch` 入口 |
| `listen(port, host)` | Workers 中端口是路由标识，不是自有 TCP 监听端口；支持的签名不同 | 不直接照搬启动脚本；抽离本地启动逻辑 |
| 外部模型 `fetch` | 适合 Workers | 保留白名单、超时、错误映射、取消请求；核实实际出站地域与阿里云服务地域兼容性 |
| 聊天 SSE | 支持 Streams、可以边到边返 | 移除 Quick Tunnel 专用 `DISABLE_STREAMING=true`，端到端检查 Android 客户端消费 SSE |
| ASR 最大 6 MB 音频 | 低于常见 Cloudflare 100 MB 请求体上限 | 保留应用 6 MB 限制；逐段检查 multipart、base64 和 JSON 内存拷贝 |
| TTS 返回 WAV | 可返回二进制或流 | 保留下载域名白名单；不得把模型 Authorization 转发给音频下载地址 |
| `USAGE_FILE` 文件计数 | 不能作为持久存储直接搬迁 | `/bundle` 只读，`/tmp` 每请求临时且不持久；改用 Durable Objects 或 D1 原子更新 |
| 内存 `Map` 限流 / TTS 缓存 | 不保证跨实例、跨机房或实例重建后共享 | 配额必须外置；缓存允许丢失，跨用户共享前明确内容与权限边界 |
| `public/`、APK 文件下载 | 静态资源需打包或独立托管 | 使用 Static Assets；单文件超过 25 MiB 时用 R2/其他对象存储。当前约 17 MB APK 在大小范围内，但其鉴权与访问权限仍需设计 |

依据：[S2][S3][S5][S6][S7][S8][S9]。

两种实现路线都可行：保留 Node HTTP 适配器可减少接口层改动；改成原生 `fetch(request, env, ctx)` 更贴合 Workers。建议共享模型请求与校验逻辑，只替换入口、配额和文件资源层，保留本地 Node 开发方式。

### 为什么不建议直接用免费版承诺交付

Workers Free 每天 10 万次请求，但每次只有 10 ms CPU；付费版 HTTP 请求默认 CPU 限制 30 秒，可配置最高 5 分钟。二者内存上限都是 128 MB。等待阿里云网络响应不计 CPU，但 multipart 解析、base64、JSON 构造、鉴权等计入。[S1][S2]

6 MB 音频转 base64 后约 8 MB，原始字节、字符串、JSON 可能同时占内存。单个请求可能可行，但必须测并发内存和 CPU；不能只看“10 万次/日免费”就判定免费版稳妥。允许空闲 CPU 等待不代表模型响应没有延迟。

HTTP 触发 Worker 在客户端保持连接时没有固定墙钟时长上限，但断连、平台更新与资源压力会中断处理。保留应用自身 30/45 秒截止时间和取消机制，不把这个能力当作无限长任务保证。[S2]

## 3. 费用：托管费与模型费分开算

官方 Workers 标准付费版：[S1]

| 项目 | 当前公布额度 / 单价 |
| --- | --- |
| 基础月费 | 最低 US$5 / 账号 / 月 |
| 请求 | 包含每月 1000 万次；超出 US$0.30 / 百万次 |
| CPU | 包含每月 3000 万 CPU ms；超出 US$0.02 / 百万 CPU ms |
| Workers 本身出站带宽 | 不额外按带宽收费；其他产品各有计费规则 |

示例仅用于估算：100 个日活用户，每人每天 20 轮，每轮 ASR、聊天、TTS 各调用一次，一个月按 30 天算，共 18 万次后端请求。若平均每次 CPU 为假设的 30 ms，则为 540 万 CPU ms，二者均在付费包含额度内。这不是本项目性能测试结果；也未计重试、总结、学习表达等额外调用。

费用公式：

```text
总费用 = 运行平台基础费及超额费
       + 阿里云对话 token / 语音识别 / 语音合成费用
       + 域名、存储、数据库及日志等可能产生的费用
```

US$5 不是整套产品的费用封顶，也不是买了 Workers 就能免费无限调用通义。模型费用应根据实际转写时长、回复长度、合成字符/音频量及具体型号当前计费分别测算。按“轮数”粗略限制不足以控制成本，还要限制音频时长、上下文长度与输出量。

Containers 也不是固定 US$5 全包：需要 Workers Paid，并叠加运行时内存、实际 CPU、配置磁盘、网络以及 Workers/DO 等费用。其磁盘默认不持久，冷启动官方描述常见 1–3 秒，仍取决于镜像与入口执行；不适合未经测试就承诺语音首轮低延迟。[S10][S11]

## 4. 中国大陆访问：必须独立判断

普通 Cloudflare 全球网络与其 China Network 是不同的商业产品。Cloudflare China Network 官方说明是 Enterprise 的独立订阅，不能把 Workers 每月 5 美元理解成同时购买了大陆节点能力。[S12]

对普通 Workers，不能仅凭“全球边缘网络”就承诺大陆三网稳定，也不能用一篇旧论坛帖断定所有域名、运营商永久不可用。应使用最终自有域名，实测电信、联通、移动以及校园 Wi-Fi 的完整 ASR→聊天→TTS 路径。绑定自有域名提供稳定入口和证书管理，但不能保证解决跨境链路问题。[S13]

现有上游以阿里云为主；若用户在大陆，经过境外 Cloudflare 再访问模型服务可能引入跨境往返。大陆云部署更容易获得可预测的国内链路，但同样要测试。是否启用 CDN 代理也要独立验证；仅为域名解析使用 Cloudflare 与让所有 API 经过其代理不是同一件事。

Smart Placement 可优化 Worker 与上游间的总路径，官方也提供 region/host/hostname placement 配置；它不等于购买了中国大陆节点，也不保证某个上游一定更快，应比较启用前后的实测结果。[S14]

大陆轻量服务器、FC 的备案、运行方式与价格口径见配套调研 `DEPLOYMENT-CN-ALTERNATIVES-2026-10-26.md`。境外云计算与大陆公开服务的备案、APP 发布和个人信息要求也不能混为一谈，应按实际部署和发布范围核实。

## 5. 不论选哪家，都需要解决的项目问题

1. **替换全员共享访问令牌。** 目前是一个 `SERVICE_ACCESS_TOKEN`；它不是每人独立身份。内测可先使用独立邀请码换取可撤销、可过期的设备凭据；正式公开使用再做账号体系。不能把阿里云模型密钥打入 APK。Cloudflare 上将模型密钥配置为 Secrets，不写进源码、前端或普通 `vars`。[S15]
2. **持久化并发安全配额。** 目前默认全服务每天 500 个 API 请求；一次完整语音轮次通常消耗三个请求，不是 500 轮。改成每设备/用户配额、全站预算、并发数三层限制；请求前预留、失败按规则结算，使用幂等请求 ID，明确日界线时区。当前以 `request.socket.remoteAddress` 判断用户，经过代理后不可靠。客户端 IP 只作为辅助风控，转发头仅信任受控入口注入的值。
3. **不要用 Workers KV 简单读加写实现严格预算。** KV 是最终一致，多机房读可能延迟 60 秒或更久，不提供这种跨请求原子读改写保证。使用 DO 的强一致事务或 D1 原子条件更新；拒绝达到额度后的请求，存储不可用时禁止继续付费推理。[S7][S8]
4. **固定入口与签名连续性。** Android 使用自有 HTTPS API 域名，后续可更换托管商而不更换入口。云端迁移本身不要求更换 Android applicationId 或签名，更新 APK 应继续用原签名，避免卸载导致本地学习资料丢失。
5. **静态资源与接口权限分开。** 介绍页、公开 APK 下载可独立公开；推理接口必须鉴权。现有 `/app.apk` 位于鉴权之后，直接浏览器下载仍需单独安排。不要为了下载方便关闭所有 API 鉴权。
6. **保留可观察性与数据边界。** 记录匿名请求 ID、阶段耗时、错误码、使用量，默认不记录完整录音、对话和密钥。原始录音与学习库仍留在各人手机；单纯部署云后端不会增加跨设备同步，也不会自动混合各人学习资料。语音识别仍会把音频发送给实际处理的模型提供商；选择 Workers 后也会经过该托管处理链路。
7. **恢复流式并处理失败。** 文本 SSE、断线取消、短回复和缓存常用 TTS 可改善体验；当前 TTS 是拿到生成音频地址后再下载返回，单独打开聊天 SSE 不会自动变成边说边生成的语音系统。明确识别失败、余额不足、网络超时、服务繁忙时的可恢复界面，避免无上限重试。

## 6. 可执行的迁移顺序

### 路线 A：Workers Paid

1. 抽离模型请求、校验与错误处理；新增 Workers 入口及独立部署配置，保留本地 Node 服务。用模拟上游先验证接口，不先消耗付费模型额度。
2. 采用 DO 管理设备额度与全局预算，或用 D1 的原子 SQL 条件更新；调整音频缓冲和取消逻辑，保留 6 MB 限制。
3. 添加设备凭据发放、撤销与短期访问凭据；模型凭据存 Secrets；测试与正式环境分开。
4. 使用 Static Assets 托管网页；按权限安排 APK 下载；需要云端音频持久化时才加入 R2，当前无需为本地录音额外建云存储。
5. 正式域名指向 Worker，Android 改固定 API 地址，启用 SSE；关闭本地服务和 PC 验证后端仍工作。
6. 完成目标网络测试、成本观测和小范围放量，才作为公开默认入口。

### 路线 B：大陆轻量服务器 / ECS

1. 先以 2 vCPU / 2 GB 左右作为小规模试运行的候选规格，实际按并发和内存测量调整；无需 GPU，规格并不代表已验证容量。
2. 使用受支持的 Node LTS、进程守护与开机启动；HTTPS 反向代理。Node 若同机反代可继续只监听 `127.0.0.1`；若容器映射则按容器网络要求绑定，避免盲目开放业务端口。
3. 禁止对 SSE 做响应缓冲或缓存，配置合适的代理超时、上传限制、健康检查；安全组只开放必要端口。
4. 把计数文件先换成事务型持久存储；单实例可用 SQLite 持久卷，扩为多副本前切换共享一致配额方案。完成鉴权、日志和预算改造，与 Workers 路线要求相同。
5. 完成自有域名、TLS、适用的备案流程及备份恢复；APK 继续原签名发布，PC 关机测试。

路线 A 运维较少，但需要运行时适配；路线 B 最大程度复用当前 Node 服务，但需要维护系统、备份和安全更新。只为了服务几十名大陆同学，不必先上 Kubernetes 或复杂微服务。

## 7. 上线验收：把“随时可用”变成能检查的结果

- PC 关机、不开启动脚本，其他人的手机仍能完成语音录制、转写、对话、AI 音频重听。
- 至少覆盖大陆三家运营商与校园 Wi-Fi，分不同时段记录成功率和 p50/p95；健康检查正常不足以证明模型链路正常。
- 分别记录上传耗时、ASR 耗时、聊天首字/全文耗时、TTS 首个可播放音频耗时和整轮耗时。可先把“正常短语音结束至出现回复文本 p95 不超过 5 秒”作为讨论目标，不能当作现已达成指标。
- 并发小规模起测再提升；包含接近 6 MB 的允许音频、恶意超大请求、取消、切后台、断网重试；检查是否重复扣费、内存超限、串流串到另一用户。
- 无凭据、过期/撤销凭据请求被拒绝；A 设备不能使用 B 的身份或额度。模型密钥不进入 APK、前端与日志。
- 重启/重新部署/冷启动后额度不清零；同时请求不能越过全站预算；数据库失败不能继续无预算调用上游。
- 每人学习资料仍独立，更新 APK 不要求卸载；真正需要账号同步时另行设计云端用户数据模型。

## 官方资料

- [S1 Workers pricing](https://developers.cloudflare.com/workers/platform/pricing/)：套餐、请求与 CPU 计价。
- [S2 Workers limits](https://developers.cloudflare.com/workers/platform/limits/)：CPU、内存、请求体、HTTP 墙钟时长。
- [S3 Workers Streams](https://developers.cloudflare.com/workers/runtime-apis/streams/)：流式转发。
- [S4 Quick Tunnels](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/)：临时地址、生命周期、SSE 和并发限制。
- [S5 Node HTTP compatibility](https://developers.cloudflare.com/workers/runtime-apis/nodejs/http/)：HTTP 兼容标志、适配器和 `listen` 差异。
- [S6 Workers filesystem](https://developers.cloudflare.com/workers/runtime-apis/nodejs/fs/)：只读 `/bundle`、每请求临时 `/tmp`。
- [S7 KV consistency](https://developers.cloudflare.com/kv/concepts/how-kv-works/)：最终一致性及不适合原子操作的场景。
- [S8 Durable Object SQLite storage](https://developers.cloudflare.com/durable-objects/api/sqlite-storage-api/)：强一致、事务型存储。
- [S9 Static Assets](https://developers.cloudflare.com/workers/static-assets/)：静态资源托管，文件大小见 S2。
- [S10 Containers FAQ](https://developers.cloudflare.com/containers/faq/)：冷启动、磁盘、生命周期、当前扩缩容能力。
- [S11 Containers pricing](https://developers.cloudflare.com/containers/platform/pricing/)：运行资源和相关服务计费。
- [S12 China Network overview](https://developers.cloudflare.com/china-network/)：Enterprise 独立订阅。
- [S13 Workers Custom Domains](https://developers.cloudflare.com/workers/configuration/routing/custom-domains/)：自有域名、DNS 和证书。
- [S14 Workers Placement](https://developers.cloudflare.com/workers/configuration/placement/)：执行位置及路径优化限制。
- [S15 Workers Secrets](https://developers.cloudflare.com/workers/configuration/secrets/)：模型密钥等敏感配置。
- [S16 D1 Database API](https://developers.cloudflare.com/d1/worker-api/d1-database/)：批量语句的事务语义。

补充官方来源与大陆部署细节见配套调研文件。价格示例为工作负载假设，不是报价或实测承诺。
