# 本地话题与噪声素材核查

核查日期：2026-10-31。只读取公开材料及保存源码，没有安装或执行第三方仓库。

| 资源 | 来源、版本与许可 | 决定 |
|---|---|---|
| 英文破冰问题 | [eecastro/Icebreakers](https://github.com/eecastro/Icebreakers/tree/e9bbcaa0e89fd7f44f2abd3fdead2e54c3cdb67c)，固定 commit `e9bbcaa0e89fd7f44f2abd3fdead2e54c3cdb67c`；仓库 CC0-1.0 | 原库面向高年级学生，不能直接当作全年龄口语教材。筛选 240 条日常、想象、偏好话题，排除不合适的私密、群体课堂专用或无意义片段；保留英文原文与行号。 |
| 中文话题 | SpeakPen 本次原创，200 条，版本 1 | 补足中文可读入口，覆盖日常、旅行、爱好、美食、城市等；静态编入，不运行时生成。 |
| 另一份 MIT 问题集合 | 本目录 `mit-questions.md`／`mit-LICENSE.txt`，版权署名 Rendall，2021；正文多处标注其他网站出处 | 仅调研保留，没有打包。仓库标 MIT 不足以澄清每个被汇编第三方问题的权利链，不为数量照搬。 |
| Simplex 噪声 | [stegu/webgl-noise 的 noise2D.glsl](https://github.com/stegu/webgl-noise/blob/master/src/noise2D.glsl)，源头 [Ashima Arts](https://github.com/ashima/webgl-noise)；MIT，Copyright 2011 Ashima Arts。固定本地原始文件及 SHA-256 见 `resource-hashes.json` | 将算法适配为标量 Kotlin，使用少量样本驱动四片低对比色场，不引入 WebGL 运行时。许可证完整打包。 |

`cc0-questions.txt`、`cc0-README.md` 和 `cc0-LICENSE.txt` 为英文来源快照；`noise2D.glsl` 和 `noise-LICENSE.txt` 为噪声来源快照。噪声的 GitHub commit API 追加查询未成功，因此用留存原始文件的内容哈希固定版本，不编造 commit 或声称核对了上游最新版本。

实际话题目录版本、总数和内容 SHA-256 在 `app/src/main/assets/topics/manifest.json`。每条英文目录项含 `source=eecastro-cc0` 与 `line`，原创中文含 `source=speakpen-original`；生成脚本内的行号白名单可复核选入范围。共 440 条且 ID／文字唯一，文本长度 2～130 字符。筛选由本次开发完成，尚未经过用户逐条审阅或教育内容专家评级。

随包保留 `topics/NOTICE.txt`、`topics/CC0-LICENSE.txt` 及 `licenses/webgl-noise-MIT.txt`。英文来源使用公共领域贡献的表达；不宣称“官方题库”或“完整课程”。无网络抓取或收费生成发生在 App 的话题展示／换批环节。
