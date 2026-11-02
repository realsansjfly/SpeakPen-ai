# 单词本与内置中文释义

2026-11-02。本地增量基于 `main / cd2675f`（0.6.0-dev07／versionCode 14）。**版本号未改，未打标签、未发 Release**；源码已提交并推送到 `main`（提交 `1b51da9`）。

## 需求

原积累库只有手填释义一条路：遇到不认识的词，要先在别处查好中文，才能录进来。这次补的是查词环节，同时给内置词表加上中文释义：

- 抽屉顶部直接查词，命中给词条详情，未命中给近似词与 AI 兜底。
- 新增「单词本」页，列出内置四级／六级／雅思词表，可按字母浏览、按级别筛选、用英文或中文释义搜索。
- 词条详情显示音标、词性、级别、柯林斯星级、全部义项与词形变化，可一键加入积累库。
- 复用既有 `VocabularyBook`／`VocabularyEntry` 模型，不新增第四套学习资产。

## 资源与依赖

内置 `app/src/main/assets/vocabulary/dictionary.tsv`：表头 + 7,638 行，7 列 `word / phonetic / pos / collins / translation / exchange / tags`，924,844 字节，sha256 `b61102dd8bb1176e58dc72ad02dabafef01783b7b38edaa859b77bd255552054`，按 `word` 升序。

来源仍是 ECDICT 固定提交 `ab22ad36302cafc6349a050e2b60894855208055` 的 `ecdict.csv`（66,009,548 字节，sha256 `d1ecbdf9ee2b920afb6f430ca6b6bb9f559588cf0dff86e04bd255f9390843cc`，770,611 行），按 `manifest.json` 钉住的三个考试标签过滤后小写去重：四级 3,849、六级 5,407、雅思 5,040，并集 7,638 词（重叠词只留一行，`tags` 列记多个级别）。

- 生成脚本 `scripts/build-dictionary.py`，`--source` 指向本地 `ecdict.csv`，`--root` 指向仓库；输出写回 `dictionary.tsv` 与 `manifest.json` 的 `dictionary` 段（行数、sha256、级别、生成脚本），并打印 JSON 报告。数据源不在仓库内，重跑需自备同一份 `ecdict.csv` 并核对上面的 sha256。
- 词性 `pos` 由脚本从义项前缀推导（`n.`／`vt.`／`adj.` 等），义项分隔符 `\n` 换成 `、`；不是官方标注。
- 音标、柯林斯星级、词形变化（过去式／过去分词／现在分词／三单／复数／比较级／最高级／原形）同样来自该 CSV，缺失则留空。
- 许可 MIT，原文随包在 `app/src/main/assets/vocabulary/ECDICT-LICENSE.txt`；词库页与词条弹窗都注明来源，并写明「非官方大纲」。

**与既有约定的偏离**：`docs/VOCABULARY-BATCH-2026-10-31.md` 与 `docs/HANDOFF-2026-10-31.md` 曾写明「仅分发词形与标签，不分发混合来源的词典释义、例句、音标、词频」。本次按需求附加了中文释义、音标与柯林斯星级，属于对该约定的主动变更，理由是查词功能没有释义就不成立；ECDICT 为 MIT，允许再分发，保留声明即可。`translation` 字段来自社区整理，质量参差，不承诺覆盖考纲或释义准确。例句（`detail`）在该提交里为空，因此**本地不含例句**，需要时按次调用 AI 生成，不写回词库。

## 已实现

数据层：

- `data/Dictionary.kt`：`Dictionary.query`（空白、含中文、超 120 字符直接拒绝）、`parse`（强制表头与 7 列）、`shortGloss`（保留词性，取前 2～3 个义项，超过 34 字符截断加省略号）、`fullGloss`、`levelsLabel`、`collinsStars`、`exchangeLines`（按固定顺序翻译词形变化的键名）；`DictionaryIndex` 提供精确查、词形回退（`-s`／`-es`／`-ed`／`-ing`／`-ies` 等）与编辑距离 ≤2 的近似词。
- `VocabularyEntry` 增加 `phonetic / pos / levels / collins / exchange`，`VocabularyStore.dictionary` 读取时校验行数与 sha256，校验失败回退到旧的三个纯词形 `txt`（`legacyEntries`），不影响已有导入词库。
- `VocabularyJson` 未改动，避免既有词库文件的 digest 校验失效。

界面：

- 抽屉顶部查词框（回车或「查」触发），结果进居中词条弹窗；`page = 3` 为单词本页，页头由 `ui/Navigation.kt` 提供，`BackHandler` 覆盖 `page == 1 || page == 3`。
- 单词本页：搜索框（英文或中文释义）、全部／四级／六级／雅思筛选、按首字母分组的字母头、行内显示词形 + 音标 + 简短释义 + 柯林斯星级；底部注明来源与「释义存在本机，不联网」，并提供「导入词库」入口复用 `ui/VocabularyScreen.kt`。
- 词条弹窗：本地命中显示完整信息与「加入积累库」（已存在时改显示「已在积累库 · 去复习」）；未命中给近似词建议与「让 AI 查意思」兜底，AI 释义沿用积累库既有的「建议 → 确认」流程，不会自动覆盖手填释义；例句按需生成，不缓存。
- 积累库：空结果态可直接用搜索词查词；对没有释义的条目提供查词入口；AI 补的释义以「AI 补充」标注，与手填释义区分。

## 修复

- `ui/WordDetailDialog.kt` 原先在组合内直接构造临时 `ReviewItem`，而 `ReviewItem.id` 默认 `newId()`，每次重组都换 id，`state.meaningSuggestion?.takeIf { it.itemId == fallback.id }` 永不成立，导致点「让 AI 查意思」看起来没有反应（请求其实已发出）。改为 `remember(query)` 固定 id，并用 `MeaningSuggestion.expression` 兜底匹配；请求在途时按钮显示「正在查…」并禁用。
- 单字母与冠词（`a`／`an`／`the`／`I`）不在三份考试词表内，原提示「没有收录」容易读成词库缺词，改为说明原因。

## 派生影响

`network/VocabularyPrompts.kt` 用 `effectiveMeaning` 组装候选行，内置词本有释义后，词库增强的候选会带中文进 prompt（此前只有词形）。提示词预算测试仍通过，但这一变化未做真实模型对比。

## 验证

- `testDebugUnitTest`：18 个测试类 187 项全部通过（基线 170，新增 `MeaningLookupTest` 8 项、`DictionaryTest` 9 项）；`testReleaseUnitTest` 同样通过；`assembleDebug` 通过。
- 真机（华为机，1080 宽）只验证了抽屉查词、未命中兜底、词条弹窗与「加入积累库」主路径。
- 未验收：7,638 行列表的滑动与字母头吸顶、键盘遮挡、TalkBack 朗读、深色模式（项目本无深色）、例句生成在真实服务下的成功率与耗时、长列表内存占用。

## 回滚

删除 `data/Dictionary.kt`、`ui/DictionaryScreen.kt`、`ui/WordDetailDialog.kt`、`assets/vocabulary/dictionary.tsv`、`scripts/build-dictionary.py`，还原 `manifest.json` 的 `dictionary` 段与 `ui/Navigation.kt` 的 `page = 3` 分支即可回到纯词形词库；`VocabularyStore` 会自动走 `legacyEntries`，积累库数据不受影响。
