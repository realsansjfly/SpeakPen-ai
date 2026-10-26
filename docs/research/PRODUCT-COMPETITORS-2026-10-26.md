# SpeakPen：英语口语 App 竞品模块调研

调研基准日：**2026-10-26（Asia/Shanghai）**。范围：Duolingo Max、Speak、ELSA Speak、Praktika、Loora，共 5 款。交付仅为产品规划与资料研究；本任务未修改应用代码、制作 UI、安装或实测竞品、购买套餐、执行部署。

SpeakPen 当前能力按本次任务说明采用：Android 语音对话、自由/复习、选词收藏、单条识别表达、优化表达提示、话题语料提示；词汇难度与自定义场景为拟新增能力。未将这些描述当作代码审计结论。模型参数、词汇资源、代码迁移由父代理负责，本研究不重复检索。

## 1. 给 SpeakPen 的结论

**建议优先验证“自定义场景 → 完成一次对话任务 → 选一处表达重说 → 加入下次复习”的练习流程。** 这是基于现有产品能力和下列官方模块所作的产品判断，不是竞品效果实测，也不是已经批准的开发排期。

| 产品 | 最值得研究的模块 | 对 SpeakPen 的增量价值 | 建议顺序与成本判断 |
| --- | --- | --- | --- |
| Duolingo Max | 有任务的 Roleplay、短时 Video Call、互动后的反馈 | 为自由对话增加清晰目标；把连续说话与事后解释分开 | P0：场景目标与简短复盘，低—中；动画通话体验，高 |
| Speak | Tutor、自建场景、Made For You 个性化练习 | 从“给提示”推进到“把这次错误变成下一次练习” | P0：自定义场景，中；P1：定向练习，中 |
| ELSA Speak | 发音指导、情景对话、长段讲话分析 | 区分“表达是否合适”和“声音是否清楚”，避免混用评分 | P0：分清反馈类型，低；专门发音评分，高 |
| Praktika | 角色场景、个性化计划/记忆、提示与纠错强度 | 让用户决定练什么、纠正到什么程度、下次接着聊什么 | P0：纠错偏好，低—中；P1：可编辑记忆，中 |
| Loora | 真实场景/自由会话、即时反馈、个人进度 | 让语音练习随时可开始，并保留下一次练习的依据 | P0：轻量目标与活动记录，低；能力趋势评分，中—高 |

事实依据分别见各产品章节；上表的优先级与成本全部为本研究判断。P0/P1 表示候选规划顺序，不代表已有需求或发布承诺。

### 三个会影响判断的边界

1. **Duolingo 的旧套餐描述不能直接照搬。** Explain My Answer 已有 2026-01-01 官方免费公告；Video Call 向部分 Super 用户开放的官方股东信检索摘要，也与“Max 独占”的简单说法不一致。当前账户和课程的完整开放矩阵仍未知。[D4][D5]
2. **Speak 的两类个性化课程不能混为一项配额。** Premium 的 Made For You 每日最多 3 次；Tutor Personalized Lessons 只写有每日上限，未给具体次数；Premium Plus 为无限。Made For You 专属页面与普通课后 Takeaways 的入口权限也有区别。[S1][S2][S4]
3. **文本纠错不能直接充当发音诊断。** ELSA 官方分别描述声音层面的发音反馈与语法/词汇反馈。对 SpeakPen 的建议是明确反馈依据；在未验证声音评测能力前，不宣称提供音素级诊断或权威口语等级。[E1][E2][E3] 后半句是产品判断。

## 2. 研究方法与证据等级

- 搜索与网页正文读取使用 **Anysearch MCP 的 search / batch_search / extract**；只把官网、官方帮助中心和开发者发布的商店介绍作为产品事实依据。
- 不使用社交渠道。检索结果中混入的社交帖子、第三方评测、商店用户评论均未用来证明模块、价格或效果。
- **A：官方正文已读取。B：仅取得官方来源的搜索摘要。未知：正文未给出、读取不足或无法确认。** B 级材料只用于提示待核实边界，不升级为完整事实结论。
- 页面带明确日期时检查其不晚于 2026-10-26；无明确发布日期的常青页面按“本次读取状态”使用，不推断功能上线日。旧文章可能更新正文，因此不把页面最早日期当作所有当前功能的发布日期。
- 截图/演示交付为**官方承载网页链接**。网页中的产品图、示意图与商店截图是官方展示素材，不是本研究截取的实机画面；未断言其与任意地区 Android 最新版本逐项一致。
- 没有运行竞品，也没有验证学习提升、识别准确率、延迟、留存或付费转化。官网里的效果百分比、用户感言和“更流利”等宣传语不作为效果证据。
- 原始检索、正文与后台代理输出保存在操作系统临时目录；仓库只新增本笔记。后台代理的独立联网环境受限，采集由本任务可用的 Anysearch 会话补齐，再提供官方原文进行后台整理。

## 3. Duolingo Max

### 3.1 三个具体模块

| 模块 | 官方资料可以确认的行为 | SpeakPen 可借鉴点 | 成本与限制判断 |
| --- | --- | --- | --- |
| **Roleplay：给定情境的角色对话** | 以咖啡点单、购物等具体情境练习；由课程专家编写情境和开场，并使其匹配课程阶段；用户回答会影响后续对话。[D1][D2] | 自定义场景不只保存一个标题，至少明确双方角色、用户想完成的事和开场；预设场景可作为创建起点 | **低—中**：场景内容编审、目标是否完成的判断、跑题处理；持续成本主要是场景维护与会话用量 |
| **Video Call with Lily：短时口语会话** | Lily 发起话题，用户可自由延伸；可以要求重复、放慢；官方介绍强调通话中不逐句纠正语法；另一官方页面说明通话后可看转录，且会记住先前讨论内容。[D1][D3] | 给自由练习一个轻量目标；保留“继续说”和“稍后看反馈”的节奏；不必先做虚拟视频形象 | **中**：连续会话状态与上下文管理；若复刻角色动画、同步声音和自然打断，成本**高**，且会放大实时体验要求 |
| **反馈：Roleplay 复盘与按需解释** | Roleplay 结束后反馈回答的准确性、复杂度并给建议；Explain My Answer 是课内答案的语法/词汇解释，可由用户主动查看，不等于通话逐句点评。[D1][D4] | 在已有单条表达优化之外增加简短会后复盘，并允许对一个问题追问“为什么”或再说一遍 | **低—中**：额外总结/解释用量、原句与改写对应关系、反馈可信度检查；避免对同一错误重复输出长报告 |

**官方展示网页：**

- [Duolingo Max 功能介绍](https://blog.duolingo.com/duolingo-max/)：包含 Roleplay 情境、反馈和 Video Call 的官方界面展示。页面仍保留早期 Explain My Answer 图片，不能据图片推断当前付费边界。[D1]
- [Video Call 介绍](https://blog.duolingo.com/video-call/)：展示课程入口及 Lily 通话画面。[D3]
- [Explain My Answer 免费公告](https://blog.duolingo.com/explain-my-answer-now-free/)：展示正确/错误答案之后的解释界面。[D4]

### 3.2 可用性与套餐边界

- **平台：**帮助中心确认 Max 为 iOS/Android 移动端功能，不能在网页购买或使用；Video Call 条目对 Android 标注 13+。不是所有课程都支持所有 AI 功能。[D2]
- **英语学习者的限制：**Roleplay 列出的英语课程来源语言包括西班牙语、葡萄牙语、日语、德语和法语；该列表没有列出中文母语学英语。不能据此保证 SpeakPen 所面向用户能直接获得同样体验，也不能反过来断言其他课程永久不可用。[D2]
- **Max 权益：**帮助中心列出 Super 权益加 Roleplay、Video Call；提供个人和家庭方案，家庭方案可与另外 5 人分享。[D2]
- **解释功能：**2026-01-01 官方公告确认 Explain My Answer 向免费学习者开放，但仍存在课程覆盖限制；不应继续列为 Max 独占。[D4]
- **Video Call 的 Super 边界：**Q2 2026 官方股东信的搜索摘要写明多数新 Super 订阅者已经获得 Video Call。原始 PDF/SEC 正文在本环境读取失败，证据为 **B 级**；结合仍写 Max 的帮助中心，只能确认需进一步核对，**截至本日所有 Super 用户是否可用、各地区/课程配额未知**。[D2][D5]
- **价格、试用时长、中国大陆购买/网络可用性：未知。** 本研究未进入账号对应的结算或试用流程。

## 4. Speak

### 4.1 三个具体模块

| 模块 | 官方资料可以确认的行为 | SpeakPen 可借鉴点 | 成本与限制判断 |
| --- | --- | --- | --- |
| **Speak Tutor：问答与生成口语练习** | 可问语法、词汇、发音问题，练习对话，并要求 Tutor 为指定主题生成口语课程；生成后的课程可保存并再次访问。[S2] | 把已有“优化表达”接成“解释这句话—用这句话再练”，减少从对话跳到泛知识问答的距离 | **中**：需要区分解释、改写和练习意图，保存可重用的练习内容；持续成本来自生成与重练 |
| **Free Talk / Create Your Own：自定义场景** | 用户指定情境、选择角色；官方沉浸模式说明包含按需提示/纠错、角色场景、打断以及从纠错组件重试最近一轮。[S3] | 自定义“我是谁、对方是谁、我要办什么事”；对失败的一轮允许重新尝试，而不是只能开始全新聊天 | **中**：场景约束、重试与会话分支的一致性、提示不要提前泄露完整答案；动画、生成背景、沉浸实时链路属于更高成本扩展 |
| **Made For You：从错误与兴趣生成专项练习** | 包含课程错误触发的 Targeted Practice、Free Talk/Roleplay 错误触发的 Takeaways，以及基于兴趣、词汇熟练度和误用模式的练习。[S4] | 让复习入口知道“这次为什么练这句话”；优先接入用户确认过的错误和收藏表达，再观察重复练习价值 | **中**：错误归因、重复去除、练习触发条件、历史状态管理；内容生成与后续使用产生持续成本 |

**官方展示网页：**

- [What is Speak Tutor?](https://help.speak.com/en/articles/11396739-what-is-speak-tutor)：包含 Tutor 入口、创建个性化练习、回看历史课程的官方截图。[S2]
- [Free Talk – Immersive Roleplay](https://help.speak.com/en/articles/13182402-free-talk-immersive-roleplay)：包含沉浸角色练习的图文展示，以及 Create Your Own、Retry 等操作说明。[S3]
- [Made For You custom lessons](https://help.speak.com/en/articles/11565779-what-are-made-for-you-custom-lessons)：包含个性化练习页面截图。[S4]

### 4.2 可用性与套餐边界

| 项目 | Premium | Premium Plus |
| --- | --- | --- |
| 课程、Situational Roleplay、Ask Speak Tutor、Smart Review | 包含 | 包含 |
| Made For You / Custom Lessons | 每日最多 3 次 | 无限 |
| Tutor Personalized Lessons | 有每日上限，具体次数**未知** | 无限 |
| 个性化学习计划、针对高频错误 | 套餐对比表未包含 | 包含 |
| Made For You 专属汇总页面 | 不包含；课后仍可获得部分 Takeaways | 包含 |

依据：官方套餐表与 Made For You 功能说明。[S1][S4] “无限”是官方套餐用词，不代表已验证不存在合理使用或技术限制。

- 官网提供 iOS 和 Android 下载入口；官网写明 **7 天免费试用，之后继续使用需付费**，价格随套餐、地区及优惠变化，精确价格以应用内为准，本研究未确认具体金额。[S5]
- Pronunciation Coach 在套餐表中标明仅供学习英语的用户使用。[S1]
- 2025-12-18 的沉浸模式说明仍写首阶段需要点击发送，且优先在 Free Talk 中提供；因此不能把“可打断”推断为所有设备都已完全免手操作，也不能把后续计划写成已上线功能。[S3]
- **Android 与 iOS 的所有模块是否完全一致、中国大陆账户的完整权益：未知。** 官网存在地区页面不足以证明所有功能均可使用。

## 5. ELSA Speak

### 5.1 三个具体模块

| 模块 | 官方资料可以确认的行为 | SpeakPen 可借鉴点 | 成本与限制判断 |
| --- | --- | --- | --- |
| **发音训练：Pronunciation Coach / Dictionary** | 官方说明包括颜色区分的发音反馈、舌位/口形/气息指导；词典可查词、录音并得到发音评分；另有跟读节奏、重音、语调练习。[E1] | 清楚区分“识别出的文字”“表达建议”“基于音频的发音建议”；收藏词可以成为以后录音重练的候选入口 | 仅做好反馈类型区分为**低**；可靠音素/重音评分为**高**，需要专门声音评测、设备/口音验证，不能由文字改写能力直接推出 |
| **ELSA AI：情景口语对话** | 集成在 ELSA Speak 中，可进行开放式语音对话与日常情境练习；官方另介绍面试、演讲等工作场景教练。[E1][E3] | 围绕场景选择一个训练目标，例如追问、表达观点或说明原因；让反馈对应这个目标 | **中**：场景内容、目标与反馈标准、错误解释质量；更复杂的职场材料会增加内容维护成本 |
| **Speech Analyzer：长段讲话分析** | 官方网页描述在浏览器开麦、获取转录并查看分析，覆盖发音、语调、流利度、语法、词汇；支持面试、演讲等练习场景。[E2] | 在对话之外，规划“独白/回答一个问题”的练习模式；反馈后能回到对应的表达，而不只是给一个总分 | 文本层面复盘为**中**；若包含可靠的语速/语调/发音分析，则为**高**，还涉及音频处理、长内容定位与重练成本 |

**官方展示网页：**

- [ELSA AI English Tutor 官方介绍](https://elsaspeak.com/en/news-events/ai-english-tutor)：2026-10-19 发布，含发音、对话和个性化学习等功能图文；这些是官网展示图，不冒充本研究实机截图。[E1]
- [ELSA Speech Analyzer 官网](https://speechanalyzer.elsaspeak.com/)：包含演讲、会议、面试等使用场景展示图与操作流程。[E2]
- [ELSA 官方 Google Play 页面](https://play.google.com/store/apps/details?id=us.nobarriers.elsa&hl=en_US&gl=US)：包含 Android 官方商店截图和开发者介绍。[E3]

### 5.2 可用性与套餐边界

- **Android：**官方开发者商店页存在，读取到的更新日期为 2026-10-23；Speech Analyzer 官网同时链接 iOS/Android 主应用。[E2][E3]
- **Speech Analyzer 与 Android 的关系：**所读官方操作说明是浏览器产品，不能据此认定上述所有分析能力都已原样集成进 Android；**移动端完整功能及账号权益是否互通未知**。[E2]
- **已确认的 Premium 边界：**Android 官方介绍把考试准备、专项课程列入 Premium，并列出月度、年度会员和 7 天试用。[E3] 官网订阅页也显示 7 天试用入口。[E4]
- **未知：**Pro、Premium、Professional 的完整权益对应关系；各类对话/分析的免费次数、分钟数；Speech Analyzer 是否随特定移动套餐附送；本账户/地区实际价格。订阅页正文提取未获得完整价格卡片，不能用搜索摘要金额或旧评测补齐。
- 官方 FAQ 的“14 天、5—10 个试用账号”描述的是**学校试用**，不应混入个人版试用说明。[E5]
- 不将官网展示的预测考试分数、用户自述进步或宣传百分比当作 SpeakPen 可复制的测量能力或效果承诺。

## 6. Praktika

### 6.1 三个具体模块

| 模块 | 官方资料可以确认的行为 | SpeakPen 可借鉴点 | 成本与限制判断 |
| --- | --- | --- | --- |
| **角色与情境会话** | 可与不同 AI 导师练习，支持不同话题和实际角色场景；官网展示自由对话与导师选择，官方 Android 介绍列出工作、旅行等主题。[P1][P3] | 场景模板与对话者角色可以分开；同一目标可以换一种对话者风格练习 | 简单角色设定为**低—中**；拟真形象、动画和多口音一致性为**高**，不宜作为首轮验证前置条件 |
| **个人学习计划与导师记忆** | 官网称学习计划适配目标、兴趣和节奏；帮助中心将记忆学习进度的 Tutor memory 列为 Premium 权益。[P1][P2] | 保存用户主动确认的练习目标和上次未完成任务；下一次对话有理由接续 | **中**：记忆的准确性、编辑/删除、过时信息处理；长期上下文与练习规划增加持续开销 |
| **情境提示与可调纠错** | 官网展示自然表达建议、即时发音/语法/选词纠正，以及 Soft、Balanced、Strict 三档语气/强度；页面有原句、较好说法和解释的展示。[P1] | 在现有优化表达提示上增加用户纠错偏好，并区分妨碍理解的错误与可选润色 | **低—中**：偏好设置、反馈筛选与一致性；持续成本来自评估与重练，避免每轮都输出同样详细的点评 |

**官方展示网页：**

- [Praktika 官网](https://praktika.ai/)：有导师形象、功能步骤、纠错示例和 Soft / Balanced / Strict 展示。[P1]
- [Praktika 官方 Google Play 页面](https://play.google.com/store/apps/details?id=ai.praktika.android&hl=en_US)：含 Android 官方截图及角色/练习功能说明，读取时更新日期为 2026-10-26。[P3]

### 6.2 可用性与套餐边界

官方帮助中心 2026-09-01 的说明给出了较明确的免费与付费区分：[P2]

| 项目 | 免费版 | Premium |
| --- | --- | --- |
| AI 导师 | 3 个 | 全部 |
| 口语练习 | 有限，具体时长/次数**未知** | 无限 |
| 反馈 | 基础 | 高级即时反馈 |
| 翻译帮助、拟真导师声音、导师记忆、每日口语挑战 | 套餐表未包含 | 包含 |
| 练习模式 | 有限 | 全部 |

- **平台：**官网提供 App Store 和 Google Play，Android 商店存在正式页面。[P1][P3]
- 方案包括周、月、季度、年度；帮助中心要求到应用或网页结算看地区价格。本研究未验证结算金额。[P2]
- 官网展示约 8 美元/月的宣传比较，但未在同一展示中说明完整地区、计费周期和资格，**不作为通用月付报价**。[P1]
- 免费版不等同于固定天数的付费试用；**所有账户的试用时长、中国大陆下载/网络可用性未知**。
- 官网列出的用户评论与星级没有被用于证明产品效果。

## 7. Loora

### 7.1 三个具体模块

| 模块 | 官方资料可以确认的行为 | SpeakPen 可借鉴点 | 成本与限制判断 |
| --- | --- | --- | --- |
| **自由会话与真实场景练习** | 官网介绍任意话题对话，以及商务会议、面试、演讲等具体练习；Android 开发者介绍称会话匹配用户水平与生活话题。[L1][L2] | 保留自由模式，同时提供一个“今天想完成什么”的轻量入口；让场景模板帮助用户开始说 | **低—中**：目标记录、模板内容和匹配逻辑；持续成本主要随对话时长增长 |
| **即时反馈与表达改写** | 官网明确列出发音、语法和 rephrasing；Android 开发者介绍列出实时翻译、识别及发音/语法反馈。[L1][L2] | 已有表达优化可强化“原句—更合适的说法—原因—重说”之间的关联；少做泛泛鼓励 | 文本表达反馈为**低—中**；若追求声音层面的可靠发音诊断为**高**，不能仅凭转录推断 |
| **个性化与进度追踪** | 官方商店介绍列出个人化会话、学习趋势、每日流利度分数与每周目标；它们是官方描述的产品机制，未公开完整评分方法。[L2][L3] | 首先记录实际练习活动、完成任务、已重练表达；将这些可解释记录用于下次推荐 | 活动记录为**低**；能力评分与趋势解释为**中—高**，需要验证可比性，避免把“说得更多”直接等同于“能力更高” |

**官方展示网页：**

- [Loora 官网](https://www.loora.com/)：真实情境、Instant English Evaluation & Feedback、任意话题等区域有官方产品展示图。[L1]
- [Loora 官方 Google Play 页面](https://play.google.com/store/apps/details?id=com.loora.app&hl=en_US)：包含 Android 截图与开发者功能介绍。[L2]
- [Loora 官方 App Store 页面](https://apps.apple.com/us/app/speak-english-with-loora-ai/id1552708303)：可作为 iOS 截图及开发者订阅说明入口。[L3]

### 7.2 可用性与套餐边界

- **平台：**有 Android 和 iOS 官方商店页面；不采用旧评测中的“仅 iOS”结论。[L2][L3]
- **免费与试用：**官网确认可免费下载并提供试用，试用后订阅；所读官方页面没有给出统一试用天数或可免费完成多少次会话，均标为**未知**。[L1]
- **订阅：**iOS 开发者说明为自动续费和无限访问，列有年、三个月和月度方案。此处只证明 iOS 展示；不能据此保证 Android/网页的周期、价格、取消流程完全相同。[L3]
- **未知：**当前 Android 精确价格、地区优惠、长期免费额度、“无限访问”下各个练习模块的细分限制、中国大陆购买和网络可用性。
- “每日流利度分数”不等于可比较的 CEFR/IELTS 等级；本研究没有取得评分有效性证据。[L2] 后半句为证据边界判断。

## 8. 候选规划：只写计划，不进入开发

以下成本是**基于任务给定现状的相对判断**，不是竞品成本披露，也不是代码评估后的工时报价。低：可围绕现有内容与交互规则扩展；中：需要新的持久状态、生成内容或跨练习流程；高：涉及声音评测、实时音视频或复杂内容体系。实际费用应由父代理后续结合真实调用量测算。

| 候选项 | 规划内容及借鉴依据 | 开发/维护成本 | 持续运行成本 | 后续验证标准，尚未执行 |
| --- | --- | --- | --- | --- |
| **P0 自定义场景最小版** | 双方角色、场景、目标、开场；可以保存并复用。[D1][S3] | 低—中：内容边界、场景编辑与重用、目标判断 | 会话用量与少量场景生成；避免每轮重复生成背景设定 | 用户能创建并完成一个自己需要的场景；对话能保持角色与目标 |
| **P0 单条表达重练** | 将已有优化表达接到“解释—重新说”；只重练一处关键问题。[S2][S3][L1] | 低—中：原句与版本关系、重新录音后的对照 | 额外一次语音与评估用量，随用户主动触发 | 用户知道改了什么，且重练能回到原先意图，而非另起无关话题 |
| **P0 纠错偏好与会后小结** | 允许偏重流畅交流或细致纠错；小结只列少量最相关的问题。[D1][D3][P1] | 低—中：偏好保持、关键问题筛选 | 一次会后总结或按需解释 | 用户能理解并控制打断程度；报告中的问题均能追溯到本次表达 |
| **P1 从错误生成下一次练习** | 给已有复习入口增加“为什么今天练这项”的依据。[S4] | 中：错误去重、确认、触发与完成状态 | 专项内容生成、短练习与历史保存 | 推荐能对应真实错误；已掌握/不认可的问题可以移出 |
| **P1 可编辑的学习记忆** | 保存目标、兴趣及未完成任务；用户可修正、删除。[P1][P2] | 中：过时信息处理、可解释接续 | 小量上下文与存储开销，随使用增长 | 接续准确；删除后不继续使用被删偏好 |
| **后置：音素评分、拟真导师、沉浸实时会话** | 参考 ELSA、Duolingo、Speak、Praktika 的较重能力。[E1][D3][S3][P1] | 高：专业测量、音频设备差异、实时同步、素材维护 | 声音处理、实时会话与多媒体资源用量 | 只有在明确需求、可验证准确性和可承受成本后单独立项 |

词汇难度沿用父代理规划提供的能力边界；本笔记不指定等级词表、数据来源、模型参数或实现方案。上述标准是未来验证计划，**没有填入虚构的测试结果**。

## 9. 待确认事项

1. Duolingo：2026-10-26 各账号/课程中 Super 与 Max 的 Video Call 实际差异，中文母语学英语的覆盖。
2. Speak：Premium 的 Tutor Personalized Lessons 具体每日上限、沉浸模式当前 Android 覆盖与免手状态。
3. ELSA：完整 Pro/Premium/Professional 权益表、Speech Analyzer 与移动订阅的关系、免费使用额度。
4. Praktika：免费口语额度、地区试用与结算价格；官网约月费的对应购买条件。
5. Loora：Android/网页付费周期、试用天数与模块细分上限。
6. 五款产品均未完成大陆设备/网络/账号实测；官方商店存在不等于本地安装、支付和服务链路已验证。

## 10. 来源索引

除特别标注 B 级，其余为官方正文已读取；统一访问日期为 **2026-10-26**。

| 编号 | 官方来源与链接 | 日期/证据说明 |
| --- | --- | --- |
| D1 | [Introducing Duolingo Max](https://blog.duolingo.com/duolingo-max/) | 页面标 2023-03-14，正文与图片存在更新；A |
| D2 | [What is Duolingo Max? — Help Center](https://www.duolingo.com/help/what-is-duolingo-max) | 常青帮助页；A |
| D3 | [Video Call with Lily](https://blog.duolingo.com/video-call/) | 页面标 2024-09-24，采用本次读取正文；A |
| D4 | [Explain My Answer is now free](https://blog.duolingo.com/explain-my-answer-now-free/) | 2026-01-01；A |
| D5 | [Duolingo Q2 2026 Shareholder Letter](https://investors.duolingo.com/static-files/3c8277ee-bc94-4f5d-9b77-0db3e46f88b8)；[同份 SEC HTML](https://www.sec.gov/Archives/edgar/data/1562088/000162828026053299/q2fy26duolingo6-30x26share.htm) | **B：Anysearch 官方来源检索摘要；正文访问失败**。只用于标记 Super 开放范围有待核对 |
| S1 | [Premium vs Premium Plus](https://help.speak.com/en/articles/5358417-what-s-the-difference-between-premium-and-premium-plus) | 页面为相对更新时间，不推断绝对上线日；A |
| S2 | [What is Speak Tutor?](https://help.speak.com/en/articles/11396739-what-is-speak-tutor) | 2025-12-18；A |
| S3 | [Free Talk – Immersive Roleplay](https://help.speak.com/en/articles/13182402-free-talk-immersive-roleplay) | 2025-12-18；描述包括阶段性限制；A |
| S4 | [Made For You custom lessons](https://help.speak.com/en/articles/11565779-what-are-made-for-you-custom-lessons) | 2025-12-18；A |
| S5 | [Speak 官网及 FAQ](https://www.speak.com/) | 常青页面；A |
| E1 | [AI English Tutor: ELSA AI](https://elsaspeak.com/en/news-events/ai-english-tutor) | 2026-10-19，早于截止日；A |
| E2 | [ELSA Speech Analyzer](https://speechanalyzer.elsaspeak.com/) | 浏览器产品说明；A |
| E3 | [ELSA Speak — Google Play](https://play.google.com/store/apps/details?id=us.nobarriers.elsa&hl=en_US&gl=US) | 开发者正文；更新 2026-10-23；A |
| E4 | [ELSA subscription](https://elsaspeak.com/en/elsa-subscription) | 已读到 7 天试用与功能介绍；完整价格卡片未提取，金额不采纳 |
| E5 | [Is there a trial period available?](https://elsaspeak.com/en/faqs/is-there-a-trial-period-available) | 正文明确是学校试用，不能外推个人版；A |
| P1 | [Praktika 官网](https://praktika.ai/) | 常青页面；A |
| P2 | [Praktika free vs Premium](https://intercom.help/praktika-ai/en/articles/12008580-is-there-a-free-version-of-the-app-available-or-do-i-need-a-subscription) | 2026-09-01；从官网 Support 链接可确认帮助中心归属；A |
| P3 | [Praktika — Google Play](https://play.google.com/store/apps/details?id=ai.praktika.android&hl=en_US) | 开发者正文；更新 2026-10-26；A |
| L1 | [Loora 官网及 FAQ](https://www.loora.com/) | 常青页面；A |
| L2 | [Loora — Google Play](https://play.google.com/store/apps/details?id=com.loora.app&hl=en_US) | 只使用开发者介绍，排除评论；A |
| L3 | [Loora — App Store](https://apps.apple.com/us/app/speak-english-with-loora-ai/id1552708303) | 只使用开发者介绍，订阅边界限于该 iOS 展示；A |

## 11. 检索与反馈记录

Anysearch 共成功 **15 个 query**（批量查询按子 query 单独计数），分两批提交 score：10 条、5 条；响应均为 queued，rejected = 0。queued 表示服务已接收排队，不宣称已落库。每项均记 3 分，因为仍需读取正文或补充查询才能满足模块、展示页及套餐边界的完整研究要求。extract 不计入成功 search 数量。没有遗漏或重复提交 request_id。

| # | Query | request_id | score |
| --- | --- | --- | --- |
| 1 | site.duolingo.com Max Roleplay Video Call feedback Android subscription | 18a3187c-fb5c-4ea1-b319-a0334f34e6ff | 3 |
| 2 | site.speak.com Speak Tutor custom lessons personalized Premium Plus | 18002ae4-bf0f-4546-ad77-7fa4f46a2cdd | 3 |
| 3 | site.elsaspeak.com AI roleplay pronunciation Speech Analyzer Premium plans | bf202f07-49a9-4e62-b28b-da917408fe9c | 3 |
| 4 | site.praktika.ai app roleplay personalized lessons subscription Android | 7b81a231-51a8-4861-bdbe-3295854cabe8 | 3 |
| 5 | site.blog.duolingo.com "Explain My Answer" "free" | 805b9504-8cd3-4875-a94e-569a0a59316c | 3 |
| 6 | site.duolingo.com "Video Call" "Super" "2026" | 24468183-e17c-4ed1-8a1f-d183f9096fda | 3 |
| 7 | site.elsaspeak.com "Premium" "Roleplay" "Free" | 9ca649e1-a227-49c3-830b-81203135cf24 | 3 |
| 8 | site.intercom.help/praktika-ai "free" "premium" | c7d3ab04-3e11-40fe-b288-97081dca07bc | 3 |
| 9 | site.help.speak.com custom scenarios roleplay create | e2bd8ec8-99a5-4d2c-a5ec-37772c930e5a | 3 |
| 10 | site.investors.duolingo.com "2026" "Super" "Video Call" | 3465a386-98ae-48bd-b87c-047a288d1086 | 3 |
| 11 | site.investors.duolingo.com "most new Super" "2026" | dddcedb8-e834-480a-9b87-70fe2abd277d | 3 |
| 12 | site.elsaspeak.com "ELSA AI" "roleplay" pronunciation | 401b5dd1-2e6d-4337-a580-93bd7d02afb7 | 3 |
| 13 | site.elsaspeak.com "ELSA Pro" "ELSA Premium" subscription | 06352157-b2a1-4475-8f23-efdae212b54b | 3 |
| 14 | site.loora.ai English conversation roleplay grammar feedback Android | 1e7facb2-e4b4-4699-8755-1b0ce1a17713 | 3 |
| 15 | site.loora.ai subscription free trial daily unlimited | a2738d05-4044-492f-96e0-92166e428836 | 3 |

原始材料位置：`%TEMP%/speakpen-competitors-2026-10-26/`。检索与正文归档：`anysearch-materials.json`；后台整理依据：`loora-official-evidence.md`。临时材料不作为第二份正式交付。
