# SpeakPen 项目指引

## UI 与界面文案

- 本项目的视觉与文案约定见 `DESIGN.md`；修改 App、原型或浏览器端 UI／可见文案前，读取该文件及 `docs/UI-COMPONENT-INVENTORY.md` 中受影响模块。
- 保留用户选定的原型风格，按本轮任务范围调整。Android 使用 Kotlin／Jetpack Compose／Material 3；`server/public/` 是独立浏览器实现。
- 按 `docs/UI-DESIGN-WORKFLOW.md` 第 4 节执行。写入 UI 前，按 `docs/UI-COMPONENT-DECISIONS.md` 的“修改前后检查”核对组件语义、适用状态、文案必要性及平台行为；小改动简短核对即可。
- 涉及组件种类、变体、状态或入口变化时，同步更新组件清单的受影响条目。涉及选型取舍时，记录决定及原因；只有形成可复用设计规则时才更新 `DESIGN.md`。单纯改间距不新增决策记录。
- 现状以当前源码为准。清单失效时先校准受影响条目；候选方案与已实施事实分开记录。未运行的设备／浏览器验收如实注明。
- 外部 Skill 提供方法与参考，项目设计约定和本轮用户明确要求决定结果。按问题选择 Skill，无需每轮加载全部五个。
