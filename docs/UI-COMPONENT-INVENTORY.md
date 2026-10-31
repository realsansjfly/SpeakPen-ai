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
| APP-01 | 页面框架与导航 | App shell＋左侧导航抽屉 → `PracticeNavigation`、`ModalNavigationDrawer`、`AnimatedContent` | 底栏已移除；75% 宽抽屉含本机身份、积累库、时间分组历史和底部设置；独立设置页／库页返回。录音与保存禁用竞争导航，设备返回与手势待验。 |
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
| CHAT-04 | 首次对话 | Empty state＋本地话题流＋“换一批” → `TopicSuggestions` | dev06 从 440 条本地话题抽取 18 条，进入／换批时重选及独立随机行起点，相邻两批无重复；26／32 dp 每秒。点选填草稿，换批不改草稿；触摸／焦点暂停所在行，输入／抽屉／后台停动，读屏或关闭动画时静态可横滑，小高度减少行数。IO 读取失败可重试。原生观感待验。 |
| CHAT-05 | 消息流 | Conversation list → `LazyColumn`＋`MessageCard` | 按消息 ID、操作锚点／最新项滚动；检查长列表、流式更新、用户阅读位置。 |
| CHAT-06 | 用户／AI 消息 | Message bubble＋Audio player＋Disclosure → `MessageCard` | 用户绿／AI 白，音频／文字，播放／准备／原文展开；转写 pending，失败后的重试／补充文字，用户“这里卡了”。检查操作对象与等待反馈。 |
| CHAT-07 | 学习辅助消息 | Inline learning card＋Disclosure → `MessageCard` 蓝色 `Surface` | 优化／话题语料／分层提示／纠错，持久折叠与长按复制／重生成；初始关键词，逐层揭示骨架与参考句。旧上下文禁止继续揭示；优化可进入独立重说。无重复手势说明，不进主上下文，不自动朗读纠错。 |
| CHAT-08 | AI 等待／流式回复 | Inline loading／Streaming content → `CircularProgressIndicator`、文本、取消按钮 | busy／streamingText；检查是否重复显示等待信息、取消后状态与流式滚动。 |
| CHAT-09 | 未收到回复 | Inline recoverable error → 提示＋“重试” `TextButton` | `needsReply` 且未锁定时显示；关键恢复动作不能因简化文案丢失。 |
| CHAT-10 | 学习操作栏 | Action toolbar＋`DropdownMenu` → 表达提示／话题语料／复盘／更多 | 更多含回退上一轮、空白收藏、练习录音；历史录音不自动发送。复盘打开阅读面，生成由另一个明确动作触发。 |
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
| SCENE-04 | 选择场景 | Picker dialog → `AlertDialog`、滚动列表、`OutlinedButton` | 预设＋自建模板、直接开始、自建入口；按场景 ID 显示已完成次数。检查列表增长、设备滚动和大字体。 |
| SCENE-05 | 自建场景 | Multi-field editor dialog → `AlertDialog`＋滚动表单 | 名称、AI 角色、我的角色、目标、开场、结束条件及附加轮数上限；移除“六项配置”重复说明。检查校验、输入法与草稿。 |

## Android：复习库、积累与练习记录

源码：[LibraryScreen.kt](../app/src/main/java/com/speakpen/app/ui/LibraryScreen.kt)、[Dialogs.kt](../app/src/main/java/com/speakpen/app/ui/Dialogs.kt)。

| ID | 模块／位置 | 语义类型 → 当前实现 | 变体／状态与检查点 |
|---|---|---|---|
| LIB-01 | 库页头与主入口 | Metadata＋CTA → 计数、添加按钮、复习按钮 | 空／非空、待复习计数；移除重复标题与装饰眉题，页标题由导航承载。 |
| LIB-02 | 搜索／分类 | Search field＋Filter chips → `OutlinedTextField`、`FilterChip` | 全部／类型、搜索内容或释义。检查长类别文案、清空和无匹配。 |
| LIB-03 | 结果列表／空态 | Content list／Empty state → `LazyColumn`、条件内容 | 真空库和筛选无结果使用不同标题，但共用添加引导；检查提示是否匹配当前空态。 |
| LIB-04 | 复习卡片 | Review card → `Card`、元信息、内容、编辑／删除、两种复习结果按钮 | 释义／例句／来源按有无显示，已到期／未到期。检查信息层级、长文本与按钮触摸区域。 |
| LIB-05 | 删除收藏 | Destructive confirmation → `AlertDialog` | 显示目标内容、删除／保留；核对可恢复性和操作后果后再评估撤销方案。 |
| LIB-06 | 手动新增／编辑积累 | Shared editor → `ReviewEditor` 的 `AlertDialog` | 类型 `FilterChip`＋内容、释义、例句输入；从复习库、聊天积累和候选编辑复用。检查共享变更影响及长文本。 |
| LIB-07 | 练习目标选择 | Multi-select picker → `PracticeDialogs` 中 `AlertDialog`＋`Checkbox`＋列表 | 空库、选 1–5 项、计数、上限、取消／去添加。到上限后不再新增勾选；是否需要解释必须结合设备验证。 |
| LIB-08 | AI 提取候选 | Review／Multi-select dialog → `AlertDialog`＋复选列表 | 空／非空、编辑单项、选中计数、收藏；内部再开 `ReviewEditor`。检查叠层焦点、选择保留和重复说明。 |
| LIB-09 | 历史练习 | 左侧抽屉的分组 LazyColumn | 真实记录与非空草稿／未完成场景可恢复；当前项选中；新建不丢原草稿；在途生成时提示先完成或取消。旧 History Dialog 保留代码但聊天入口不再调用。 |

## Android：设置与对话参数

源码：[SettingsScreen.kt](../app/src/main/java/com/speakpen/app/ui/SettingsScreen.kt)、[ConversationSettings.kt](../app/src/main/java/com/speakpen/app/ui/ConversationSettings.kt)。

| ID | 模块／位置 | 语义类型 → 当前实现 | 变体／状态与检查点 |
|---|---|---|---|
| SET-01 | 设置页结构 | 独立页面＋分组列表 → `SettingsScreen` | 顶部返回与保存状态；按范围／显示播放／服务／体验组织，移除卡片嵌套及底部操作栏。 |
| SET-02 | 当前／默认设置 | Scope tabs → `TabRow`＋`Tab`＋当前对象说明 | 当前对话／新对话默认；作用范围影响用户决策，保留可理解的范围信息。 |
| SET-03 | 难度／回复长度 | Single-choice picker → 点击行＋`SettingsChoiceDialog` 的 `AlertDialog`／`RadioButton` | 选中即回调并关闭，另有取消按钮；三个难度与长度选项按数据生成，检查说明长度与重复选择步骤。 |
| SET-04 | 高级参数 | Disclosure＋Slider → `ConversationControls`、展开行、`Slider` | 温度、回复长度、重置；服务能力不足禁用，保存中受控。保留温度的必要解释，不强制所有字段配描述。 |
| SET-05 | 显示、自动播放、示例体验 | Preference switch → `SettingSwitch`＋`Switch` | checked／enabled；显示开关映射 `!voiceReplies`。检查标签与开关方向、重复解释和任务中禁用。 |
| SET-06 | 连接状态与操作 | Service status＋Actions → 状态文本、检查／重新连接按钮 | 连接立即生效，与其他设置的保存机制不同；关键差异需要可发现。 |
| SET-07 | 配对入口 | Connection form dialog → `AlertDialog`＋密码显示字段＋粘贴按钮 | 完整链接、非空可连接、取消清空；检查键盘、错误恢复及敏感内容呈现。 |
| SET-08 | 设置保存 | 自动保存状态 → `SettingsNavigation`、ViewModel 保存路径、编辑恢复记录 | 返回先保存后离开，后台尝试保存；保存中禁用，失败留页重试，未报成功前保持失败状态；恢复不复制配对凭据。设备存储异常仍待验。 |
| SET-09 | 设置离开 | `BackHandler`＋统一 page 请求 | 常规离开确认和手动保存／撤销已移除；键盘先收起，系统／顶部返回使用保存路径；失败可重试。 |

## Android：词库增强（dev04）

| ID | 位置 | 实际组件与行为 | 状态与验证边界 |
|---|---|---|---|
| VOCAB-01 | 设置词库行 | Scoped preference row → 当前／默认范围独立选择一套或关闭 | 不改难度；返回自动保存沿用 dev03。 |
| VOCAB-02 | 选择／详情 | 全高任务面 → VocabularyScreen 全屏 Dialog、RadioButton、LazyColumn、搜索 | 最新版本列表；手动选用，不自动切换。 |
| VOCAB-03 | 文件／粘贴 | OpenDocument、OutlinedTextField、FilterChip | TXT／CSV／UTF-8／2 MiB／20,000 条；名称及来源必填；取消未保存导入确认，大文本不进 Bundle，强杀不保证恢复。 |
| VOCAB-04 | 解析预览 | 计数、逐行错误、条目 LazyColumn、确认按钮 | 结构错误禁止保存；其他行错明确“仅保存有效项”，失败保留重试，完整原文及来源保留。 |
| VOCAB-05 | 来源／版本 | AlertDialog＋只读 LazyColumn | 数量、来源、许可、版本与哈希；明确非官方完整；许可原文可查。导入原文只展示前段，完整本地保存。 |
| VOCAB-06 | AI 补充 | 明确请求及结果确认的两步 AlertDialog | 告知单条上传及请求消耗；可取消、失败重试；确认另存 AI 字段和新版本，不覆盖原文。 |

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
| WEB-01 | 顶栏／服务入口 | Header、`role=status`、下载 App／连接 App／复制入口按钮；隐藏的连接区＋密码输入 | 状态更新、连接错误和密钥不外露；入口文案与当前原生导航对应。 |
| WEB-02 | 模式／库入口 | 普通 `button`＋selected class，自由聊／练收藏／库／新对话 | 选择状态与键盘语义需浏览器核验；不能视为原生场景模式的完整对应实现。 |
| WEB-03 | 全局反馈／忙碌 | `#feedback[role=alert]`、`feedback()`；`setBusy()` 禁用操作 | 普通完成反馈与错误共用 alert 区；检查播报强度和重试可达性。 |
| WEB-04 | 欢迎／消息／学习提示 | 空态、动态 `article.message`／`.bubble`、蓝色 hint；消息容器 `aria-live=polite` | 实际生成内容来自 `render()`；检查提示重复说明与动态播报。 |
| WEB-05 | 音频／转写 | 原生 `<audio controls>`、播放按钮、`<details><summary>` | 用户录音／AI 语音、英文原文／转写展开；检查媒体控制与焦点。 |
| WEB-06 | 消息操作 | 模态 `<dialog id=message-actions>`＋`showModal()` | 长按／右键／键盘菜单触发；识别、复制、重写；检查关闭后焦点及消息对象。 |
| WEB-07 | 输入与录音 | `<form>`／文字输入、中文求助、autoplay checkbox、停止、hold 手势按钮、重试 | 录音／上滑取消／权限、busy；Web 媒体能力单独验收。 |
| WEB-08 | 库与条目 | `#library-panel`、`renderLibrary()` 动态 item＋练这一项／删除 | 空／非空；删除直接执行，原生为确认 Dialog，属于已观察的平台差异，非已批准统一方案。 |
| WEB-09 | 手动积累 | `<dialog id=manual-entry>`＋表单、`select`、input、textarea | required 内容、保存／取消；`openManualEntry()` 设初始焦点。 |
| WEB-10 | AI 候选 | `<dialog id=candidates>`＋`renderCandidates()` 复选／输入编辑 | 空／非空、已选保存；与原生内层编辑 Dialog 不同，需分别维护。 |

## 原型与介绍产物

| 产物 | 覆盖内容 | 使用边界 |
|---|---|---|
| [design/preview.html](../design/preview.html) | 三个手机画面：聊天气泡／音频展开／录音；库卡片／筛选外观；偏好卡片／显示切换／开关 | 自标 v0.2，部分交互示意，无 AI 或录音；搜索等外观不证明完整功能。用于风格对照。 |
| [design/product-intro.source.html](../design/product-intro.source.html) 与 CSS | 产品介绍排版 | 宣传展示产物，不纳入 App 组件实例；本次未对介绍页逐节点登记。 |
| `deliverables/` 中 HTML／报告／APK | 交付或验证产物 | 不作为当前源码 UI 清单的权威来源。 |

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
