# SpeakPen：五个项目在 Codex 中的 UI 工作流与使用教程

核对日期：2026-10-31。适用现有 Kotlin＋Jetpack Compose 手机 App，并区分 HTML 原型和浏览器端。

## 当前安装状态（2026-10-31）

五个 Skill 已全局安装到 `C:/Users/Realsansjfly/.codex/skills/`，下一轮对话可用；若技能列表未刷新，重开会话或应用。

| 调用名 | 安装形式 |
|---|---|
| `$impeccable` | 官方 Skill，Windows 引擎 0.1.18 已启动并校验 |
| `$ui-ux-pro-max` | 上游 Skill，搜索脚本路径已适配全局 Codex；Compose 检索已验证 |
| `$shadcn` | 上游 Skill，Claude 上下文注入已改为 Codex 显式检测 |
| `$ux-ui-agent-skills` | 本地参考适配版，含组件规格、状态、Tokens 与 Compose 参考 |
| `$ui-layouts` | 本地参考适配版，含组件索引、MDX 文档与选定浮层源码 |

每个目录中的 `SOURCE.json` 记录固定上游版本和适配差异。未启用项目 Hook；未安装 React/npm 运行时依赖。下文的项目级安装命令保留作为其他环境的接入参考，本机无需重复执行；本机调用脚本应使用上述全局目录。

示例：`$ux-ui-agent-skills 保留当前原型风格，比较录音失败适合 inline alert、snackbar 还是 alert dialog，列出状态和关闭规则，先不改代码。`

## 0. 先看结论

推荐日常链路：

**现有设计基准 → 一个明确问题 → 组件选型与状态规格 → Compose 局部实现 → 设备验收 → 记录决定。**

五个资源分工协作，每轮按问题调用需要的部分。无需每改一个间距就加载五个仓库，也无需把 App 改造成 React 项目。

| 资源 | 在 Codex 中的形式 | 本项目负责什么 |
|---|---|---|
| Impeccable | 本身就是 Skill；当前支持 Android 原生指导 | 设计分析、组件任务规划、局部打磨、文案和原生审计 |
| UI UX Pro Max | Skill＋Python 数据检索 | 检索目标问题的 UX 和 Jetpack Compose 指南 |
| UX/UI Agent Skills | 官方 Codex 路线是 AGENTS.md＋规格／Tokens／检查工具 | 参考组件 anatomy、variants、states、场景与 Compose 适配 |
| shadcn/ui | 官方 Skill＋文档／源码 Registry＋可选 MCP | 提供组件语义、组合与可扩展源码参考；当前原生端主要读取参考 |
| UI Layouts | 组件文档、示例与源码 Registry | 参考 Bottom sheet、Drawer、Modal 等变体和过渡方式 |

注意：UI Layouts 不是必须安装成 Skill 才能用。普通文档和源码也能被 Codex 阅读、比较并用于实现；需要明确告诉它读取什么、用于什么。

## 1. 本项目现在的基准

- Android App 在 `app/`，使用 Kotlin、Jetpack Compose、Material 3。
- `Theme.kt` 是当前主题的事实来源，项目内 Nunito、绿白配色、绿色用户气泡和蓝色辅助提示已有实现。
- `design/preview.html` 是自标 v0.2 的原型，保留风格参考价值，功能可能落后于当前版本。
- `server/public/` 是浏览器实现，与原生 App 分开构建和验收。
- 本轮新增根目录 `DESIGN.md`，把“保留现有风格”的约束写下来；它尚未经过设备视觉验收。
- `UI-COMPONENT-DECISIONS.md` 记录当前组件候选，所有候选均未实施。

视觉依据先看用户本轮指定的参考，其次看匹配当前版本的设备画面和主题代码。原型与当前 App 冲突时说明差异；涉及产品方向的冲突让用户选择，不能自行将旧原型恢复到当前代码。

## 2. 首次接入：哪些装，哪些读

本节是可选项目级安装说明；当前机器已全局安装，见顶部安装状态。本轮安装未添加 npm 依赖，未修改 App 实现。

第三方安装器执行前，先检查版本、安装脚本、写入路径与现有文件差异。首次实施可让 Codex准备安装检查清单和差异，再执行已授权的安装。准备教程、读取 README 和参考源码不需要执行安装器。

### 2.1 Impeccable：项目级安装 Skill

在项目根目录的 PowerShell 中，官方安装入口支持：

```powershell
npx impeccable install --providers=codex --scope=project
```

项目级 Skill 应落在 `.agents/skills/impeccable/`，安装器还可能创建配置和 Hook。核对实际写入内容，保留本项目已有文档。若当前 Codex 版本提供 `/hooks`，根据安装器说明查看和信任项目 Hook；Hook 不等于原生 APK 的自动视觉验收。

在 Codex 对话框输入下面的内容，而不是在 PowerShell 输入 `$impeccable`：

```text
$impeccable init
项目是 Android Jetpack Compose App，主要任务是口语练习。
沿用根目录 DESIGN.md 的现有风格和证据来源。
读取 README 与已有产品范围文档，补充产品上下文；涉及事实无法确定时标注待核实。
保留现有 DESIGN.md，本轮只建立上下文，不修改界面。
```

`init` 用于形成 `PRODUCT.md`；`document` 用于记录设计系统。已有 `DESIGN.md` 时，让它核对、补充并说明差异，不需要每次重新生成整套规范。

常用指令均在 Codex 对话框：

| 指令示意 | 用途 |
|---|---|
| `$impeccable critique 消息长按操作` | 从用户任务和可用性分析问题 |
| `$impeccable shape 目标选择流程` | 先规划交互与状态 |
| `$impeccable layout 设置页的难度与高级设置区域` | 调整局部层级和间距 |
| `$impeccable clarify 场景编辑中的保存和取消文案` | 明确动作与后果 |
| `$impeccable harden 场景编辑页` | 检查长文本、输入法、错误和边界状态 |
| `$impeccable audit app/src/main/java/com/speakpen/app/ui` | 使用 Android 原生审计路线 |
| `$impeccable polish 已修改的目标选择组件` | 收尾本轮变化 |

`live`、`generate` 和 `detect` 当前是 Web 能力。可用于能正常运行的网页原型；不能拿浏览器截图或 DOM 扫描替代 APK 验证。Compose 多方案使用独立 Preview、调试展示页或不同候选构建进行比较。

### 2.2 UI UX Pro Max：安装后做定向检索

官方 CLI 为 `ui-ux-pro-max-cli`，命令名为 `uipro`。无需先做全局安装的调用方式：

```powershell
npx --package=ui-ux-pro-max-cli uipro init --ai codex
```

当前 Codex 模板目标路径为 `.agents/skills/ui-ux-pro-max/`。安装后检查 `SKILL.md`、`scripts/search.py` 和数据目录是否齐全。

本项目已有视觉方向，日常修改优先定向检索，不从行业配色重新生成设计系统：

```powershell
python ".agents/skills/ui-ux-pro-max/scripts/search.py" "error summary validation" --domain ux
python ".agents/skills/ui-ux-pro-max/scripts/search.py" "dialog sheet keyboard" --stack jetpack-compose
python ".agents/skills/ui-ux-pro-max/scripts/search.py" "state restoration" --stack jetpack-compose
```

命令是查询示例；全局安装已用 `remember state --stack jetpack-compose` 验证检索，不能保证每个词组都有精确匹配。结果需检查适用平台、组件和版本；无匹配就明确报告，不用无关搜索结果凑答案。安装版本的 `--help` 为最终参数依据。

给 Codex 的日常提示：

```text
$ui-ux-pro-max
本轮只查询“目标多选的反馈”和 Jetpack Compose 对话框／底部面板的相关指南。
沿用 DESIGN.md 的现有配色和字体。
列出实际命中的规则、来源和适用边界，再应用到本轮组件。
```

上游最新 Compose 指南可能针对比当前项目更新的版本。先读 `app/build.gradle.kts`，使用当前依赖支持的 API；必要升级单独说明。

### 2.3 UX/UI Agent Skills：采用官方 Codex 路线，选择性合并

这个项目也有 Skill，但其官方 Codex 安装路线以 `AGENTS.md` 和参考文件接入，不能假设安装后就能执行 Claude Code 的 `/design-component` 或 `/critique`。

官方命令：

```powershell
npx ux-ui-agent-skills init --agent codex
```

它会写项目指引、组件规格、Tokens 和检查脚本。对于已有项目，建议先在单独临时目录生成并查看，再把选定内容接入 SpeakPen。不要把生成的整份 `AGENTS.md` 直接替代本项目指引。

本项目优先保留以下参考目录及其相对链接依赖，可放在 `design/references/ux-ui-agent-skills/`，记录来源 commit：

```text
components/
accessibility/
frameworks/adapter-protocol.md
frameworks/adapters/jetpack-compose.md
tokens/                  # 被所选规格引用时一并保留
SOURCE.md                # 仓库、commit、日期、许可证、选用范围
```

这些是可选项目内参考位置；本机相关参考已放入全局 `ux-ui-agent-skills` 和 `ui-layouts` 目录，无需重复下载。

日常调用方式用自然语言：

```text
使用 UX/UI Agent Skills 的 organisms、overlays、feedback 与
Jetpack Compose adapter 作为参考，为目标选择器写组件规格：
结构、内容变种、适用状态、取消／提交、焦点、输入法、TalkBack。
Tokens 映射到现有 Theme.kt，保留 DESIGN.md 风格。
只采用本任务适用的规则，说明哪些规则不适用。
```

它的 Playwright、DOM／ARIA、CSS 对比度等浏览器检查不能直接检测 Compose。原生部分转为 Compose semantics、设备布局、系统返回及相应 Android 测试。Token 格式检查与 APK 行为验证分别报告；没有运行或不适用的检查，不记为通过。

### 2.4 shadcn/ui：原生端读取参考，Web 实验才接入运行时

当前官方文档的 Skill 安装命令是：

```powershell
npx skills add shadcn/ui
```

安装时选择 Codex 和项目范围，核对实际 `.agents/skills/` 位置。此 Skill 的完整实现工作流围绕 React 项目和 `components.json`，会读取框架、别名、组件与基础库等信息。

**本项目的原生 App 当前没有 React／components.json，因此根目录不必执行 `shadcn init`。** 安装 Skill 也不会把 Kotlin App 变成可运行 shadcn 组件的项目。

原生端这样用：

```text
参考 shadcn/ui 的 Dialog、Drawer、AlertDialog 与反馈组件文档，
比较“临时选择”“需要明确确认”“持续错误提示”的语义区别。
目标是 Android Compose，只输出选型与 Compose 映射。
保留当前主题，示例代码使用项目现有 Kotlin／Material 依赖。
```

若确有必要做交互原型，可在 `design/lab/` 创建独立 React 实验项目，再在该子目录安装 shadcn Skill 和运行时。这个目录是备选方案，本次没有创建。实际项目仍保留 Compose；实验结果需要转译并重新验收。

shadcn MCP 是可选的检索／工具通道，Skill 是工作方法；二者不是彼此的安装前提。本轮无需为阅读参考先配置 MCP。

### 2.5 UI Layouts：直接读取具体组件

推荐从三个例子开始：

- Responsive Modal：移动端 Drawer、桌面 Dialog／面板的适配。
- Directional Drawer：不同方向与使用方式。
- Linear Modal：同一组件不同内容结构。

```text
读取 UI Layouts 的 Responsive Modal 与 Directional Drawer 的文档和源码。
提取展开层级、关闭方式、滚动区域和动作区域等交互思路。
说明哪些适合目标选择任务，并转成 Compose Material 3 可实现的方案。
外观映射到 DESIGN.md；网页专属效果不作为原生实现要求。
```

在实际 React 实验项目中，文档给出的入口例如：

```powershell
npx shadcn@latest add 'https://www.ui-layouts.com/r/responsive-modal.json'
```

此命令仅用于已配置的 React／shadcn 项目，添加前检查组件源码和依赖。本项目 Kotlin 根目录不运行它；现有静态 HTML 也不能直接运行 TSX 组件。

### 2.6 确认 Codex 已发现 Skill

Codex 官方支持项目内 `.agents/skills/`、个人目录 `~/.agents/skills/`。CLI／IDE 可使用 `/skills` 或 `$` 选择，桌面界面以实际 Skill 入口为准。自动发现依赖 description，关键任务宜明确点名。

```text
列出这次能发现的 impeccable、ui-ux-pro-max、shadcn Skill 及其绝对路径。
对找不到的项标记“未发现”，不要假设已安装。
只读取匹配本轮任务的参考；本轮暂不执行设计工具或修改界面。
```

首次使用时确认变化已被发现；若列表未刷新，重开会话／应用再检查。项目级和个人级同名 Skill 可能同时出现，避免安装重复版本后混用。

## 3. 全项目入口与文档分工

入口已建立：全局 `C:/Users/Realsansjfly/.codex/AGENTS.md` 保存跨项目文案偏好；项目根目录 `AGENTS.md` 指定本项目 UI 任务需要读取的文件和修改前后检查。后续新会话可由这些入口发现约定，具体效果仍需结合任务检查。

| 文件 | 回答的问题 | 什么时候读／什么时候改 |
|---|---|---|
| 全局 AGENTS.md | 所有项目做界面时，默认遵循什么偏好？ | 保存“减少不必要的次要标签和描述，让布局表达信息”；只在跨项目偏好变化时改。 |
| 项目 AGENTS.md | 这个项目的 AI 从哪里开始、哪些步骤必须做？ | UI／可见文案任务入口；读取 DESIGN 和清单的受影响模块，执行修改前后检查；不塞入完整组件目录。 |
| [DESIGN.md](../DESIGN.md) | 界面应保持什么视觉与信息表达原则？ | UI 工作前读；只有字体、颜色角色、信息密度等长期规则改变时更新，不记每次修复。 |
| [UI-COMPONENT-INVENTORY.md](UI-COMPONENT-INVENTORY.md) | 当前模块实际上用了什么组件与状态？ | 分析前查相关模块并对照源码，变更组件种类／入口／变体／状态后同步；它是现状快照。 |
| [UI-COMPONENT-DECISIONS.md](UI-COMPONENT-DECISIONS.md) | 这个任务为什么选择此组件，怎样判断合理？ | 选型时读检查表和相关候选；完成有取舍的变更后记理由与验证。候选与已实施分开。 |
| 本工作流 | 一轮任务先后怎么做、需要哪种 Skill 和证据？ | 不熟悉流程或任务跨步骤时查；流程变化时改，无需每次修 UI 都重写教程。 |

日常顺序：**入口规则 → DESIGN → 清单中的目标模块与源码 → 决策检查 → 实现 → 验证 → 同步受影响记录。**

文案偏好应对外部模板和 Skill 的一般建议有约束力；用户本轮明确要求及项目具体语义仍决定取舍。清晰的字段标签、读屏名称、错误恢复和操作后果不能因为“简洁”而消失。具体例子见 DESIGN 的“界面文案与信息密度”。

这些 Markdown 约定要求 AI 在 UI 任务中核对，并不等同于文件写入 Hook 或保证每次落盘都会自动运行的检查器。代码现状和运行证据始终需要复核。

## 4. 每轮实际工作流

### 第一步：确定一个任务与范围

读取 DESIGN，并在组件清单中定位本轮目标 ID 与对应源码；先校准过期条目。清单中的检查点只是评估线索，不自动扩大为本轮修复范围。

用户可以直接描述体验，不必先知道术语。Codex 负责把体验翻译为组件／状态／布局问题。

```text
保留现有风格。这轮只改“聊天消息长按后的操作区域”。
现在像一个通用弹窗，我希望它更像针对这条消息的快捷操作，
并且能看出当前操作对象。复制、识别好表达、优化／重新生成的业务行为沿用现状。
请读取设计基准和当前代码，比较锚定菜单与底部动作面板。
先给选择依据和局部方案；没有必要时不要增加第三种方案。
```

指定“先给方案”时，方案阶段不等同于实现授权；如果直接要求“修好并验证”，Codex 应在约定范围内完成分析、实现和验证。

### 第二步：把问题写成可验证条目

每条报告包括：页面／入口、代码位置、现象、触发条件、用户影响、推荐变化、依据和验收方法。

```text
UI-001
页面／入口：聊天页 → 长按一条助手回复。
现象：多个独立操作出现在通用确认窗口中，操作对象不突出。
影响假设：切换消息时用户可能不确定正在操作哪条内容；需设备验证。
改进目标：保留消息上下文、动作清楚、退出后回到原位置。
范围：消息操作呈现与相关局部组件；原有回调语义保持。
验收：用户／助手／提示三类消息；无转写；动作不可用；返回关闭；长文本。
```

代码能证明使用了哪种组件，不能单凭源码证明“看起来拥挤”“用户迷惑”或“更流畅”。这类判断要有画面或操作证据。

### 第三步：按需使用五个资源

| 本轮问题 | 主要使用 | 补充使用 |
|---|---|---|
| 间距、层级、文字不清楚 | Impeccable layout／typeset／clarify | UX Pro Max 对应规则 |
| 弹窗种类单一、场景不匹配 | UX/UI Agent Skills 规格＋Impeccable shape | shadcn 语义、UI Layouts 变体 |
| 缺少加载／错误／空状态 | UX/UI feedback＋UX Pro Max | Impeccable harden |
| 想看同任务的多种交互 | 组件规格＋两个局部方案 | Compose Preview；必要时 Web 实验 |
| 原生控件和返回／输入法问题 | Android／Compose 指南＋当前依赖 | Impeccable 原生 audit／adapt |
| 同类组件多处不一致 | 对照 DESIGN 和实际消费者 | Impeccable extract；共享组件规格 |

对于组件结构变化，先写一份规格：目的、内容、容器、模态性、变种、状态、取消／确认、焦点、返回、输入法和 Token 映射。 规格关联清单 ID；按组件决定文档的“修改前后检查”判断选型与文案是否必要。原型已足够清楚的关系优先由布局表达；保留必要的标签、状态和操作后果。

### 第四步：在现有设计里扩展变体

允许根据场景增加紧凑／展开、短／长内容、可撤销／不可撤销、只读／编辑、局部加载／整页加载等变体。变体的理由要对应真实任务。

局部方案比较时固定同一份内容、同一设备和同一状态。先比交互结构，再比细节，避免用内容不同掩盖布局差异。

```text
请给目标选择器做两个 Compose 局部方案：
A 保留简单选择 Dialog；B 使用 Bottom sheet＋固定底部动作区。
二者使用相同数据、主题与字号；比较 0 项、3 项、50 项列表，选满 5 项与大字体。
说明推荐方案和代价，不改全局导航。
```

### 第五步：完成最小实现

选定方案后，Codex 应先读实际调用链和本地变更，保留正在进行的功能工作。优先复用 Theme 和已存在的 UI 组件，避免顺手重写网络／语音代码。 写入前核对受影响组件的任务、状态、模态与退出、文案、平台和复用范围；小改动简短核对即可，无需为此单独停下来申请批准。

如果 UI 修复确需状态保存或回调调整，要说明必要关系，补恰当的状态测试。纯颜色或间距修正不需要编写与实现逐字对应的测试。

### 第六步：分层验收

| 层次 | 核对什么 | 证据 |
|---|---|---|
| 源码／编译 | 当前 API、类型、引用、业务回调 | 构建和相关测试结果 |
| Compose 布局 | 小屏、大字体、长文本、输入法与 Insets | 模拟器／真机对应截图 |
| 操作流程 | 返回、关闭、取消、保存、复选、重复点击 | 操作记录／设备测试 |
| 语音相关 | 录音取消、播放、转写与 AI 等待的反馈 | 适用时真机验证；真实服务测试单独标明 |
| 可访问性 | 触摸目标、TalkBack 标签、选中／忙碌态、焦点 | 原生语义和实际读屏检查 |
| 风格保持 | 配色、字体、气泡、信息层级与已选基准 | 相同场景改前／改后 |

本项目 `scripts/build.ps1` 已执行 assembleDebug、单元测试和 lint，并处理 Windows 中文路径。为 UI 验收输出独立、未配对的包可参考：

```powershell
.\scripts\build.ps1 -Unpaired -OutputDirectory '.\deliverables\ui-review' -ApkName 'SpeakPen-ui-review-debug.apk'
```

脚本要求有效 JDK／SDK，以上本次未执行。输出到独立目录避免覆盖已有交付。未配对包只能证明相应离线／示例场景；联网、录音权限和真机反馈按实际证据补验。

Android 截图使用 Android Studio 的设备截图功能最直接。命令行也可在设备有写入权限时先保存到设备再 pull：

```powershell
adb -s <设备序列号> shell screencap -p /sdcard/speakpen-ui-review.png
adb -s <设备序列号> pull /sdcard/speakpen-ui-review.png .\design\speakpen-ui-review.png
```

`<设备序列号>` 需替换成 `adb devices` 的实际值。保存文件时记录 App 版本、设备、尺寸／密度、字体缩放和复现步骤。浏览器手机视口只证明网页适配，不证明 Android 输入法或返回行为。

未运行、无法连接设备、只检查了代码、只验证了网页，都分别说明；不能统一写成“验证通过”。

### 第七步：保留决定而非重复提示

写完后复查受影响组件，按以下分工收尾：

- 组件种类、入口、变体或状态改变：更新清单的对应 ID，只写最终实现。
- 发生有意义的选型取舍：在决定记录中写问题编号、清单 ID、采用原因与验证状态；纯间距／错字修改不新增决定。
- 形成可复用的视觉或文案规则：更新 DESIGN；个别页面的临时方案留在决定记录。
- 运行流程本身改变：更新本教程；普通 UI 任务不改工作流。

例如，把 CHAT-12 从 AlertDialog 动作列表改成底部动作面板，应更新清单 CHAT-12 的实际 API／状态，在决定中解释为何保留消息上下文、关闭后回哪里及验证情况。仅调整已有绿白风格内的间距，不需要重写 DESIGN。这个例子是维护方法，不代表已实施该替换。

## 5. 可复制的日常提示词

### 5.1 整体检查，但保留原型风格

```text
按 docs/UI-DESIGN-WORKFLOW.md 工作，先读取 DESIGN.md。
保留现有绿白、Nunito、语音气泡和蓝色学习提示风格。
用 Impeccable 的 Android critique／audit 路线检查 App 的聊天、复习和设置任务流。
重点查组件选型、状态反馈、长内容、输入法和系统返回。
按问题→证据→影响→建议→优先级输出，源码推断与设备已验证分开。
本轮先形成问题清单，不修改界面；第三方风格建议不作为重新设计依据。
```

### 5.2 不会术语时也能准确描述

```text
位置：设置 → 表达难度。
现在：点开后弹一层窗口，选一次还要退出。
期望：一眼能知道当前档位，切换更直接，同时保留每档解释。
限制：沿用颜色和字体，保留三档及其数据语义。
请把我的描述转成 UI 问题，再比较行内单选、分段选择和底部面板。
按解释长度与使用频率推荐一个，不为增加种类而增加组件。
```

### 5.3 直接要求实现并验证

```text
按设计基准修复 UI-001，采用已选定的底部动作面板方案。
复用现有主题与业务回调，只修改必要的 UI／状态衔接。
检查用户消息、助手消息、蓝色提示、无转写、动作不可用和系统返回。
完成构建与适用验证，交付改前／改后证据及未验证项。
```

### 5.4 只微调布局

```text
$impeccable layout
只调整聊天页录音按钮与上方辅助入口的层级和间距。
保持现有字体、颜色、文案与录音手势契约。
让主操作更容易识别；在小屏、键盘显示和大字体条件下验证。
```

### 5.5 建立自己的组件变体词表

```text
围绕本项目已出现的任务，更新 docs/UI-COMPONENT-DECISIONS.md。
每类列出：语义、别名、适用／不适用、可替代模式、变种、适用状态、
Compose 映射、实际消费者与设备验收条件。
参考 UX/UI Agent Skills、shadcn/ui、UI Layouts；新增规则说明来源与采用原因。
只记录真实需求和最终决定，候选方案明确标注待验证。
```

## 6. 推荐第一轮从哪里开始

从“消息长按动作”或“目标选择器”选一个，比较范围较小。之后处理 AI 候选编辑的浮层关系，再评估场景长表单和练习记录。

可以直接在下次对话输入：

```text
按 docs/UI-DESIGN-WORKFLOW.md 开始第一轮。
先读取 DESIGN.md 和 UI-COMPONENT-DECISIONS.md，确认可用 Skill 与参考。
只分析聊天消息长按操作：检查当前实现，比较锚定动作菜单和底部动作面板，
保留现有风格及所有业务动作。给出推荐方案、状态表与需改文件。
本轮到方案为止；尚未安装的工具用文档参考完成分析，并明确接入状态。
```

## 7. 来源与适用边界

以下为本次读取过的官方／上游来源。安装器、Skill 和组件 API 可能更新，实际接入以固定版本和文件差异为准。

- [Codex Skills：调用、发现路径与按需加载](https://developers.openai.com/codex/skills)
- [Codex AGENTS.md：项目指引与加载规则](https://developers.openai.com/codex/guides/agents-md)
- [Impeccable README：安装与命令](https://github.com/pbakaus/impeccable)
- [Impeccable Codex Skill](https://github.com/pbakaus/impeccable/blob/main/.agents/skills/impeccable/SKILL.md)
- [Impeccable Android 指导](https://github.com/pbakaus/impeccable/blob/main/.agents/skills/impeccable/reference/android.md)
- [Impeccable 命令边界：live／generate／detect 为 Web](https://github.com/pbakaus/impeccable/blob/main/.agents/skills/impeccable/reference/routing.md)
- [UI UX Pro Max：安装入口](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill)
- [UI UX Pro Max：Codex 模板路径](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill/blob/main/src/ui-ux-pro-max/templates/platforms/codex.json)
- [UI UX Pro Max：实际查询流程](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill/blob/main/.claude/skills/ui-ux-pro-max/SKILL.md)
- [UX/UI Agent Skills：非 Claude 环境接入](https://github.com/plugin87/ux-ui-agent-skills/blob/main/docs/AGENTS-SETUP.md)
- [UX/UI Agent Skills：Compose adapter](https://github.com/plugin87/ux-ui-agent-skills/blob/main/frameworks/adapters/jetpack-compose.md)
- [shadcn/ui 官方 Skill 安装文档](https://github.com/shadcn-ui/ui/blob/main/apps/v4/content/docs/%28root%29/skills.mdx)
- [shadcn/ui 官方 Skill 内容](https://github.com/shadcn-ui/ui/blob/main/skills/shadcn/SKILL.md)
- [UI Layouts Responsive Modal](https://github.com/ui-layouts/uilayouts/blob/main/apps/ui-layout/content/components/responsive-modal.mdx)
- [UI Layouts Directional Drawer](https://github.com/ui-layouts/uilayouts/blob/main/apps/ui-layout/content/components/directional-drawer.mdx)
