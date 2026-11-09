# SpeakPen 当前 UI 组件清单

核对日期：2026-10-31。依据当前工作区源码，包含尚未提交的代码；不是某个已发布 APK 的快照。dev05 新增学习辅助、复盘与渐变背景，自动化证据见本批交付，未进行 Android 设备体验验收。其他条目沿用原扫描范围。

本页供查阅“哪里用了什么、哪些地方值得检查”。设计基准见 [DESIGN.md](../DESIGN.md)，如何选择／记录组件见 [组件决定](UI-COMPONENT-DECISIONS.md)，执行步骤见 [工作流](UI-DESIGN-WORKFLOW.md)。

## 阅读约定

- 按用户任务聚合组件，不逐个罗列 Row、Text、Spacer 等布局节点。每行说明语义类型、实际 API／函数、状态或使用变体，以及适用性检查点。
- “检查点”是待验证问题，不是已证实的缺陷，也不是批准的改造。当前实现以所列源码为准；发生漂移时先更新受影响行。
- `AlertDialog` 是当前 Compose 实现 API；其中的内容可能承担选择、编辑或动作菜单，不统一判为“危险确认框”。
- Android 和 Web 各自列示。原型／介绍页属于参考与展示产物，不等同于当前 App 功能。

## Android：全局框架与共享组件

源码：[MainActivity.kt](../app/src/main/java/com/speakpen/app/MainActivity.kt)、[Theme.kt](../app/src/main/java/com/speakpen/app/ui/Theme.kt)、[VoiceComponents.kt](../app/src/main/java/com/speakpen/app/ui/VoiceComponents.kt)。

| ID | 模块／位置 | 语义类型 → 当前实现 | 变体／状态与检查点 |
|---|---|---|---|
| APP-01 | 页面框架与导航 | App shell＋左侧导航抽屉 → `PracticeNavigation`、`ModalNavigationDrawer`、`AnimatedContent` | 75% 宽抽屉；话题圆柱显示且抽屉关闭时禁用侧滑打开，菜单仍可打开。抽屉已打开或正在打开时恢复 gesturesEnabled，支持右侧遮罩点击、左滑及系统返回关闭；录音／保存仍限制导航操作。原生真机手势待验。 |
| APP-02 | 页面顶部全局反馈 | Inline alert／Status notice → `InlineNotice` | 错误容器／蓝色普通提示、可关闭；error 优先于 notice。检查是否应更贴近出错区域、读屏更新与持续时间。 |
| APP-03 | 非聊天页的在途任务 | Inline operation status＋Cancel → `Row`、`TextButton` | busy 非空且不在聊天页时出现。取消操作需要始终可达，避免与其他反馈重复。 |
| SHARED-01 | 主题、字体、形状 | Design tokens → `SpeakPenTheme`、`MaterialTheme` | 浅色绿白、Nunito、语义色与圆角；更新时检查消费者，不把 Token 值重复写成组件约束。 |
| SHARED-02 | 小型分类／眉题 | Eyebrow／Metadata label → `Eyebrow` | 复习页眉、分类、待复习日期、当前练习等用途。装饰眉题与有用元数据分别评估。 |
| SHARED-03 | 语音波形 | Audio visualization → `Waveform`／`Canvas` | 静态、active＋level；录音和消息使用。图形是辅助，不代替状态语义或真实音频内容。 |
| SHARED-04 | 按住录音 | Gesture button → `HoldToTalk` | 英文／中文、禁用、录音、上滑取消；支持 semantics 点击开始／结束。检查权限、松手、取消和触摸目标。 |

## Android：聊天、消息与输入

源码：[ChatScreen.kt](../app/src/main/java/com/speakpen/app/ui/ChatScreen.kt)，相关共享组件见上一节。

| ID | 模块／位置 | 语义类型 → 当前实现 | 变体／状态与检查点 |
|---|---|---|---|
| CHAT-01 | 聊天顶部 | 单行 Header → `ChatHeader` | 左抽屉、中间模式、右新对话；标题与正常连接常驻说明移除，异常仍用 InlineNotice。 |
| CHAT-02 | 模式选择 | 互斥 Tab＋滑动选中背景 → `ChatHeader` | 自由聊天／场景；收藏练习移至积累库入口，恢复旧练习显示目标；AI 回复原文开关在设置。 |
| CHAT-03 | 当前任务摘要 | Context metadata → 收藏练习目标文字；场景摘要 | 难度移入设置不再常驻；必要练习目标和场景进度仍保留。 |
| CHAT-04 | 首次对话 | 本地 CSS 3D 圆柱话题＋聚焦确认 → `TopicSuggestions` / `home/topic-cylinder.html` | dev10：显式 APK 文档路由，验证脚本／正尺寸视口／完整话题／可视状态后展示；加载时可用原生列表，超时、页面／脚本／渲染失败保留原生选择与动效重试。保留原生“列表”切换入口，处理 DOM 正常但设备合成异常时的手动恢复。兼容旧 WebView 的尺寸观察与系统动画监听。440 条中抽 18 条，4 层柱面，近实远虚，中央标题上下浮动；6°/秒自转，水平拖动、帧率无关的惯性与平滑回落；“换一批”900ms 快转后换批。“随便！”与点选共用完整话题放大、背景模糊和确定／取消；取消恢复，确认才经原生桥发起 AI 首问。源码后续修复：圆柱按下后父层不截获触摸，话题页关闭抽屉侧滑；抽屉可见性变化只暂停，不换批。键盘、读屏／减少动效、字体 >1.35 倍及小高度转可滚动列表；系统返回取消，抽屉／输入／后台暂停。WebView 仅本地白名单资源，禁网络／文件／内容访问，无 API 凭据。浏览器组件检查通过；最新 Android 手势修复未打包，真机待验。 |
| CHAT-14 | 话题开场与恢复 | Assistant-first opening＋重试 → `TopicOpening` / `PracticeViewModel` / `ChatScreen` | 确认保存话题上下文，不写用户消息或剪贴板；AI 重组为英文首问，遵守难度与词库配置；失败／取消保留话题并显示重试／返回选话题，禁止抢发用户消息；首条回复可重新生成，旧草稿保留。存储与请求快照检查由 JVM 测试覆盖，真实模型首问另有实测记录。 |
| CHAT-05 | 消息流 | Conversation list → `LazyColumn`＋`MessageCard` | 按消息 ID、操作锚点／最新项滚动；检查长列表、流式更新、用户阅读位置。 |
| CHAT-06 | 用户／AI 消息 | Message bubble＋Audio player＋Disclosure → `MessageCard` | 用户绿／AI 白，音频／文字，播放／准备／原文展开；转写 pending，失败后的重试／补充文字，用户“这里卡了”。检查操作对象与等待反馈。 |
| CHAT-07 | 学习辅助消息 | Inline learning card＋Disclosure → `MessageCard` 蓝色 `Surface` | 优化／话题语料／纠错可持久折叠及长按操作；历史 SCAFFOLD 直接显示已保存的全部层，只读且不能重生成。优化可进入独立重说；学习内容不进入主上下文，不自动朗读纠错。 |
| CHAT-08 | AI 等待／流式回复 | Inline loading／Streaming content → `CircularProgressIndicator`、文本、取消按钮 | busy／streamingText；检查是否重复显示等待信息、取消后状态与流式滚动。 |
| CHAT-09 | 未收到回复 | Inline recoverable error → 提示＋“重试” `TextButton` | `needsReply` 且未锁定时显示；关键恢复动作不能因简化文案丢失。 |
| CHAT-10 | 学习操作栏 | Action toolbar＋`DropdownMenu` → 话题语料／复盘／更多 | 话题语料打开全高设置面，固定最新完整用户＋AI 一轮，用途回答／追问／泛话题相关、数量 4–10（默认 5）、难度默认／高级／地道。未完成一轮时显示恢复提示并禁用生成。更多含回退、收藏、练习录音；表达提示入口已移除。 |
| CHAT-11 | 输入区 | Compact composer → 输入方式切换、输入主体、中文求助 | 语音单行、文字时中文求助另行；有播放时可停止；手势提示仅语音时显示。会话文字草稿独立保存，设备输入法和录音待验。 |
| CHAT-12 | 长按消息 | Contextual action menu → 内容为动作列表的 `AlertDialog` | 识别／复制／优化用户消息或重新生成其他消息；无文字时禁用。比较菜单／动作面板前先确认目标标识、长按替代入口。 |
| CHAT-13 | 补充／修改转写 | Short editor dialog → `AlertDialog`＋多行 `OutlinedTextField` | 本地草稿、非空且未锁定可保存、取消关闭。检查键盘、取消后的草稿、长内容。 |

## Android：场景练习

源码：[ChatScreen.kt](../app/src/main/java/com/speakpen/app/ui/ChatScreen.kt) 的 `ScenarioPanel` 与 [Dialogs.kt](../app/src/main/java/com/speakpen/app/ui/Dialogs.kt) 的 `ScenarioPicker`／`ScenarioEditor`。

| ID | 模块／位置 | 语义类型 → 当前实现 | 变体／状态与检查点 |
|---|---|---|---|
| SCENE-01 | 对话中的目标面板 | Inline progress panel＋Disclosure → 蓝色 `Surface`、目标行、展开按钮 | 展开／折叠、目标达成、判定理由／消息证据；这是任务信息，不按“说明文字”一概去除。 |
| SCENE-02 | 场景阶段／判定失败 | Inline outcome／Recovery → 阶段文本、继续对话／场景复盘、错误＋重试 | ACTIVE／ENDED／CONTINUED、检查失败；目标达成才奖励和计数，到限未达成只结束。结束后固定复盘可稍后生成，续聊不改原结算。 |
| SCENE-03 | 更正目标进度 | Correction form dialog → `AlertDialog`＋依据字段＋两种标记动作 | 依据非空才可提交；检查动作层级、返回和错误恢复，不能误当两按钮“确认／取消”。 |
| SCENE-04 | 选择场景与任务简报 | 全页 `LearningSurface`＋滚动场景行 → `ScenarioBrief` | 选择页使用大标题、浅绿场景行、角色信息和方向图标，底部自建入口；简报保留深色角色块与目标，主按钮开始对话，次按钮查看完整示范。沿用六个场景，不提前发起正式对话。 |
| SCENE-05 | 自建场景 | 全页 `LearningSurface`＋分组滚动表单 | 情境与角色／任务目标／对话安排分组，顶部保存；字段校验、IME Insets、未保存返回确认。已有模板保留编辑入口；编辑器草稿尚未承诺进程恢复。 |
| SCENE-07 | 完整示范对话 | 全页阅读面＋整段／逐句播放、角色名、可选择文本 | 按用户新要求撤去跟读、录音、转写、反馈与步骤导航；五场景展示素材全部原句，酒店共 29 条，第六场景为应用自编。长内容分段播放避免 TTS 截断；停止、退出、切换会话或开始对话时取消播放。旧学习数据保留兼容，返回不启动正式对话。原生音频及真机返回待验。 |

## Android：复习库、积累与练习记录

源码：[LibraryScreen.kt](../app/src/main/java/com/speakpen/app/ui/LibraryScreen.kt)、[Dialogs.kt](../app/src/main/java/com/speakpen/app/ui/Dialogs.kt)、[DictionaryScreen.kt](../app/src/main/java/com/speakpen/app/ui/DictionaryScreen.kt)、[WordDetailDialog.kt](../app/src/main/java/com/speakpen/app/ui/WordDetailDialog.kt)。

| ID | 模块／位置 | 语义类型 → 当前实现 | 变体／状态与检查点 |
|---|---|---|---|
| LIB-01 | 库页头与主入口 | Metadata＋CTA → 计数、添加按钮、复习按钮 | 空／非空、待复习计数、查词失败等服务错误用 `InlineNotice` 显示在页头下方；移除重复标题与装饰眉题，页标题由导航承载。 |
| LIB-02 | 搜索／分类 | Search field＋Filter chips → `OutlinedTextField`、`FilterChip` | 全部／类型、搜索内容或释义（含 AI 补充的释义）。检查长类别文案、清空和无匹配。 |
| LIB-03 | 结果列表／空态 | Content list／Empty state → `LazyColumn`、条件内容、查词入口 `Button` | 真空库、筛选无结果、输入了可查的英文表达三种形态；前两种共用添加引导，第三种给出「查一下「…」」入口，先看 AI 释义再收录。检查提示是否匹配当前空态与长查询。 |
| LIB-04 | 复习卡片 | Review card → `Card`、元信息、内容、编辑／删除、查词入口、两种复习结果按钮 | 释义／例句／来源按有无显示；手填释义优先，AI 补充单独标注来源；无释义且内容可查时显示「不知道意思？查一下」。已到期／未到期。检查信息层级、长文本与按钮触摸区域。 |
| LIB-05 | 删除收藏 | Destructive confirmation → `AlertDialog` | 显示目标内容、删除／保留；核对可恢复性和操作后果后再评估撤销方案。 |
| LIB-06 | 手动新增／编辑积累 | Shared editor → `ReviewEditor` 的 `AlertDialog`（可选 `state`／`vm`／`autoLookup`） | 类型 `FilterChip`＋内容、释义、例句输入；内容可查且从未有释义时出现「不知道意思？让 AI 查一下」与行内建议块（收下／填入输入框／丢弃），示例模式的建议块标明「示例释义（未接入服务）」。`autoLookup` 由库页空态或卡片入口触发一次自动查询；查词失败时在对话框内直接显示错误原因。从复习库、聊天积累和候选编辑复用。检查共享变更影响、建议块叠层与长文本。 |
| LIB-07 | 练习目标选择 | Multi-select picker → `PracticeDialogs` 中 `AlertDialog`＋`Checkbox`＋列表 | 空库、选 1–5 项、计数、上限、取消／去添加。到上限后不再新增勾选；是否需要解释必须结合设备验证。 |
| LIB-08 | AI 提取候选 | Review／Multi-select dialog → `AlertDialog`＋复选列表 | 空／非空、编辑单项、选中计数、收藏；内部再开 `ReviewEditor`。检查叠层焦点、选择保留和重复说明。 |
| LIB-09 | 历史练习 | 左侧抽屉的分组 LazyColumn | 真实记录与非空草稿／未完成场景可恢复；当前项选中；新建不丢原草稿；在途生成时提示先完成或取消。旧 History Dialog 保留代码但聊天入口不再调用。 |
| LIB-10 | 单词本 | Anki 聚焦列表 → `DictionaryScreen`、FlowRow 筛选、字母分组 LazyColumn | 大号单词、音标、首要释义及一条例句，其余进入详情。后台加载保留；使用四级 4,027／六级 1,652／雅思 376 条并合并同词完整数据；旧 ECDICT 隐藏保留历史引用。真机性能／长词换行／读屏待验。 |
| LIB-11 | 词条详情 | 全高 `LearningSurface`＋重点释义、例句和 Disclosure → `WordDetailDialog` | 首义／一条例句突出，全部义项、词形、其他例句与完整词典摘录按需展开，来源常规显示；收藏保留完整释义及原例句，无例句才生成。取代小居中窗口，原生小屏与大字体待验。 |


## Android：设置与对话参数

源码：[SettingsScreen.kt](../app/src/main/java/com/speakpen/app/ui/SettingsScreen.kt)、[ConversationSettings.kt](../app/src/main/java/com/speakpen/app/ui/ConversationSettings.kt)。

| ID | 模块／位置 | 语义类型 → 当前实现 | 变体／状态与检查点 |
|---|---|---|---|
| SET-01 | 设置页结构 | 独立页面＋分组列表 → `SettingsScreen` | 顶部返回与保存状态；按范围／显示播放／服务／体验组织，移除卡片嵌套及底部操作栏。 |
| SET-02 | 当前／默认设置 | Scope tabs → `TabRow`＋`Tab`＋当前对象说明 | 当前对话／新对话默认；作用范围影响用户决策，保留可理解的范围信息。 |
| SET-03 | 难度／回复长度 | Single-choice picker → 点击行＋`SettingsChoiceDialog` 的 `AlertDialog`／`RadioButton` | 选中即回调并关闭，另有取消按钮；三个难度与长度选项按数据生成，检查说明长度与重复选择步骤。 |
| SET-04 | 高级参数 | Disclosure＋Slider → `ConversationControls`、展开行、`Slider` | 温度、回复长度、重置；服务能力不足禁用，保存中受控。保留温度的必要解释，不强制所有字段配描述。 |
| SET-05 | 显示、自动播放、示例体验 | Preference switch → `SettingSwitch`＋`Switch` | checked／enabled；显示开关映射 `!voiceReplies`。检查标签与开关方向、重复解释和任务中禁用。 |
| SET-06 | 连接状态与操作 | 普通开发包保留服务状态和检查／重新连接；固定云构建不渲染此模块 | `BuildConfig.FIXED_SERVICE` 由 `build.ps1 -Cloud` 注入；网络失败保留相关区域错误。旧隧道设置不能覆盖固定云地址。 |
| SET-07 | 配对入口 | 开发包保留密码字段 Dialog；云构建无入口且忽略配对深链 | 固定 HTTPS 地址及应用服务令牌来自本地忽略配置；百炼 API Key 不进入 App。 |
| SET-08 | 设置保存 | 自动保存状态 → `SettingsNavigation`、ViewModel 保存路径、编辑恢复记录 | 返回先保存后离开，后台尝试保存；保存中禁用，失败留页重试，未报成功前保持失败状态；恢复不复制配对凭据。设备存储异常仍待验。 |
| SET-09 | 设置离开 | `BackHandler`＋统一 page 请求 | 常规离开确认和手动保存／撤销已移除；键盘先收起，系统／顶部返回使用保存路径；失败可重试。 |
| SET-11 | 多段语音设置与队列 | 全局 Switch＋聊天页 `VoiceQueuePanel` | 默认关闭；逐段本机保存，最多 6 段／180 秒，试听／重录／删除、段数和总时长。显式提交才 ASR，再整合一条用户消息并请求一次回复；失败整队保留。关闭／离页／切会话保留队列，回退上一轮前要求处理。原生权限／强杀／音频待验。 |

## Android：词库增强（dev04）

| ID | 位置 | 实际组件与行为 | 状态与验证边界 |
|---|---|---|---|
| VOCAB-01 | 设置词库行 | Scoped preference row → 当前／默认范围独立选择一套或关闭 | 不改难度；返回自动保存沿用 dev03。 |
| VOCAB-02 | 选择／详情 | 全高任务面 → VocabularyScreen、RadioButton、搜索与 LazyColumn | 后台加载完成前显示进度并禁用选择／导入；默认展示 Anki 和用户导入的最新版本，旧 ECDICT 从可选列表移除；历史引用继续可用，词表详情可开例句／摘录。切换词库仍由用户明确选择。 |
| VOCAB-03 | 文件／粘贴 | OpenDocument、OutlinedTextField、FilterChip | TXT／CSV／UTF-8／2 MiB／20,000 条；名称及来源必填；取消未保存导入确认，大文本不进 Bundle，强杀不保证恢复。 |
| VOCAB-04 | 解析预览 | 计数、逐行错误、条目 LazyColumn、确认按钮 | 结构错误禁止保存；其他行错明确“仅保存有效项”，失败保留重试，完整原文及来源保留。 |
| VOCAB-05 | 来源／版本 | 只读 AlertDialog | Anki 包名、原笔记数、数量、版本、哈希及真实来源说明；第三方词典内容不标为 ECDICT MIT。原始 APKG 保留在电脑，App 使用已清理的纯文本资产。 |
| VOCAB-06 | AI 补充 | 明确请求及结果确认的两步 AlertDialog | 告知单条上传及请求消耗；可取消、失败重试；确认另存 AI 字段和新版本，不覆盖原文。 |
| VOCAB-07 | 语义增强选项 | 会话／默认范围的 Switch＋换行选择 Chip → `EnhancementControls` | 开关、当前回合／最近对话、克制／均衡／积极、4／8／12 候选；显示可用或本地回退及只读版本。手机无嵌入模型／向量索引；本地导入词库不上传全文，未知词库安全本地筛选。 |

以上已构建并通过 JVM 验证，原生文件选择／返回／键盘／旋转／大字体／TalkBack 仍待设备验收。HTML 流程仅为方案证据，见 [词库实施](VOCABULARY-BATCH-2026-10-31.md)。

## Android：学习辅助、复盘与反馈（dev05）

| ID | 位置 | 实际组件与行为 | 状态与验证边界 |
|---|---|---|---|
| LEARN-01 | 优化后的重说 | 全高 `LearningSurface`＋滚动原句／参考句、录音、回听、可编辑转写 | 每次录音保留，仅显式发送追加正式对话；已发送只读，重复发送受阻；返回不发送。转写失败可重试或补写，录音中禁止关闭。设备权限／音频仍待验。 |
| RECAP-01 | 随时复盘 | 全高 `Dialog`、场景／最新 Tab、`SelectionContainer`、Markdown 排版 | 打开不生成，生成不结束对话、不加收藏；取消／失败保留旧文档。固定只取结束边界，最新成功后替换；长文明确容量错误。系统返回与大字体待验。 |
| RECAP-02 | 导出 | `CreateDocument("text/markdown")` 系统文件选择器 | UTF-8 Markdown，不需广泛存储权限；保留待导出文档 ID，失败保留本机文档，导出文件不自动回写。原生导出与进程恢复待验。 |
| SCENE-06 | 首次结束反馈 | 短暂勾选缩放＋完成卡，随后短 `AlertDialog` 询问固定复盘 | 只有目标达成显示奖励；到限未达成不奖励／计成功。已展示标记持久化，重开／续聊不重复询问。JVM 检查状态去重，动画观感待设备。 |
| SET-10 | 纠错与学习记录 | 会话／默认范围内纠错 `Switch`；成功总次数／不同场景数 | 默认关闭，说明每轮额外一次 AI 请求；开关不禁用手动优化／复盘。统计不依赖生成文档。 |
| CHAT-14 | 对话背景 | `drawBehind` 四片径向浅绿／浅蓝渐变＋MIT Simplex 噪声 | dev06 以连续噪声控制局部位置、半径、透明度，绘制层约 30 Hz 更新；不可见、后台、录音／回听、学习阅读面与场景选择暂停。系统关闭动画或触摸探索时静态。HTML 只示意方向，帧率／电耗／原生视觉待验。 |

## Android：可选参考组件

当前已有 Material 3 导航 Drawer 及聊天更多操作的 `DropdownMenu`；仍未发现 `ModalBottomSheet`、Tooltip、Skeleton、`SnackbarHost` 的实例。它们是可选参考词表，不是必须补齐的组件配额。当前加载主要为局部圆形进度；当前持久反馈主要为 `InlineNotice` 和区域内文本。

当前原生浮层多数由 `AlertDialog` 承载。单一 API 不足以证明体验有问题，应按内容体量、模态性、输入法、退出和任务频率逐项判断。

## Web：独立浏览器实现

源码：[index.html](../server/public/index.html)、[app.js](../server/public/app.js)、[app.css](../server/public/app.css)。`learning.js` 提供学习内容规则，不是额外的 UI 模块。

| ID | 模块 | 类型与当前实现 | 检查点 |
|---|---|---|---|
| WEB-01 | 顶栏／服务入口 | Header、status、下载／连接 App／复制入口；连接区＋密码输入 | 入口及错误使用通用服务文案，移除“电脑需开机”说明；390／1280px 模拟鉴权／聊天和 fragment 清理通过。浏览器仍需完整入口，APK 下载另行提供。原有移动顶栏 3 处 10px 警告未改。 |
| WEB-02 | 模式／库入口 | 普通 `button`＋selected class，自由聊／练收藏／库／新对话 | 选择状态与键盘语义需浏览器核验；不能视为原生场景模式的完整对应实现。 |
| WEB-03 | 全局反馈／忙碌 | `#feedback[role=alert]`、`feedback()`；`setBusy()` 禁用操作 | 普通完成反馈与错误共用 alert 区；检查播报强度和重试可达性。 |
| WEB-04 | 欢迎／消息／学习提示 | 空态、动态 `article.message`／`.bubble`、蓝色 hint；消息容器 `aria-live=polite` | 实际生成内容来自 `render()`；检查提示重复说明与动态播报。 |
| WEB-05 | 音频／转写 | 原生 `<audio controls>`、播放按钮、`<details><summary>` | 用户录音／AI 语音、英文原文／转写展开；检查媒体控制与焦点。 |
| WEB-06 | 消息操作 | 模态 `<dialog id=message-actions>`＋`showModal()` | 长按／右键／键盘菜单触发；识别、复制、重写；检查关闭后焦点及消息对象。 |
| WEB-07 | 输入与录音 | `<form>`／文字输入、中文求助、autoplay checkbox、停止、hold 手势按钮、重试 | 录音／上滑取消／权限、busy；Web 媒体能力单独验收。 |
| WEB-08 | 库与条目 | `#library-panel`、`renderLibrary()` 动态 item＋练这一项／删除 | 空／非空；删除直接执行，原生为确认 Dialog，属于已观察的平台差异，非已批准统一方案。 |
| WEB-09 | 手动积累 | `<dialog id=manual-entry>`＋表单、`select`、input、textarea | required 内容、保存／取消；`openManualEntry()` 设初始焦点。 |
| WEB-10 | AI 候选 | `<dialog id=candidates>`＋`renderCandidates()` 复选／输入编辑 | 空／非空、已选保存；与原生内层编辑 Dialog 不同，需分别维护。 |
| WEB-11 | 话题语料设置 | 原生 `<dialog id=topic-options>`＋用途／难度 select、数量 range | 固定最新完整用户＋AI 一轮，4–10 条双语 JSON 校验，失败保留旧结果；取消不生成，学习语料不进聊天上下文。独立 HTML 新流程预览见下方交付记录。 |

## 原型与介绍产物

| 产物 | 覆盖内容 | 使用边界 |
|---|---|---|
| [design/preview.html](../design/preview.html) | 三个手机画面：聊天气泡／音频展开／录音；库卡片／筛选外观；偏好卡片／显示切换／开关 | 自标 v0.2，部分交互示意，无 AI 或录音；搜索等外观不证明完整功能。用于风格对照。 |
| [design/product-intro.source.html](../design/product-intro.source.html) 与 CSS | 产品介绍排版 | 宣传展示产物，不纳入 App 组件实例；本次未对介绍页逐节点登记。 |
| `deliverables/` 中 HTML／报告／APK | 交付或验证产物 | 不作为当前源码 UI 清单的权威来源。 |

## 2026-10-31 当前功能展示页

2026-11-09（0.7.1）：SHOWCASE-01 的两个 APK 入口与版本记录链接同步到云连接修正版；下载说明改为安装后自动连接现有云服务、无需配对。沿用原有按钮与 details，不新增组件或状态。

| ID | 模块 | 当前实现与范围 | 验证 |
|---|---|---|---|
| SHOWCASE-01 | 手机优先介绍页 | `server/public/product.html/css/js`；五个独立功能分块与 sticky 章节导航、阅读进度；展示 0.7.0 的话题圆柱与 AI 开场、可配置话题语料、语义增强、全页场景学习与完整示范、Anki 词库；保留六场景互斥切换、原生 details 和真实 APK 链接 | 0.7.0 发布前重新执行 320／390／768／1440 Chromium 视口、键盘与交互检查；无脚本内容可读。未验真机 Safari／微信。 |
| SHOWCASE-02 | 动效与静态分发 | 话题连续漂移、首屏浅色渐变、模拟画面差异化滚动出现；全局暂停、后台／离屏暂停和系统减少动效；HTML 内嵌字体与脚本，静态 ZIP 可托管 | 实测移动、暂停／恢复、离屏停止、动态切换减少动效通过；未录音或调用 AI。v2 独立产物及边界见 [改版记录](PRODUCT-SHOWCASE-V2-2026-10-31.md)。 |

## 如何持续维护

1. 修改 UI 前按模块找 ID，打开该行对应源码，检查现状是否仍一致；只做文案／间距时不必读取全清单。
2. 按组件决定文档的检查表判断任务、容器、状态、返回／输入法与文字必要性。发现问题时给具体理由；没有问题就保留已有实现。
3. 改了种类、入口、变体、关键状态或共享消费者，同步更新受影响行；新增模块新增稳定 ID，删除模块移除并在相关决定中记录去向。ID 不因排序变化重编号。
4. 清单只记已落地事实和未验证检查点。方案及原因写进决定文档；通用视觉规则写进 DESIGN；操作步骤写进工作流。
5. 收尾区分源码核对、编译／测试、设备／浏览器验证；只更新实际验证过的条目及其证据，不将本页整体改成“全部通过”。

此维护由项目 `AGENTS.md` 要求执行，属于 AI 工作约定，尚未配置文件写入 Hook 或自动语义审查。组件是否适合场景仍需结合任务与实际画面判断。

## 2026-10-31 新方案参考（未实施）

[聊聊／抽屉／设置方案](UI-PROPOSAL-2026-10-31.md)及[独立交互预览](../design/chat-ui-review-2026-10-31/index.html)覆盖 APP-01、CHAT-01/02/04/10/11、LIB-09、SET-01/02/08/09 和场景进度摘要。只验证 HTML 原型，以上 Android 现状条目仍以源码为准，待用户审阅后再实施并更新。

## 2026-10-31 Android 落实

用户批准导航方案并取消首页话题说明／暂停行，现已落实为原生源码；见 [实施记录](UI-IMPLEMENTATION-2026-10-31.md)。本页对应现状条目已更新。前述“未实施”方案段仅保留当时审阅阶段历史，不代表当前进度。
