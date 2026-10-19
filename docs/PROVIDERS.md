# 服务选型记录 · 2026-10-13

目的：英语口语对话，小量上下文、短回答、中文求助、英文录音转写。网络速度、首字等待和英文口音效果必须在部署地区实测；吞吐不是端到端延迟。

## 本版接入

| 路径 | 聊天 | 转写 | 决策依据 |
|---|---|---|---|
| Groq（默认优先） | openai/gpt-oss-20b | whisper-large-v3-turbo | 官方模型列表列出 1000 tokens/s、每百万输入 $0.075 / 输出 $0.30；Turbo 转写每小时 $0.04。存在有额度限制的免费层；没有匿名无限免费承诺 |
| SiliconFlow（可作为国内主路） | Qwen/Qwen3-8B | FunAudioLLM/SenseVoiceSmall | 官方应用案例列出免费小模型和 ASR；API 手册仍要求 Bearer API Key。使用 enable_thinking=false，转写只发服务商支持的 file/model |

Groq GPT-OSS 使用 reasoning_effort=low、include_reasoning=false。该模型 **不支持 reasoning_format**，不能把其他推理模型的 hidden 参数直接照搬。服务端固定模型和输出上限，用户不配置模型。

免费渠道不等于未经授权借用他人凭据。官方合作 App 的免登录能力不代表此项目可以使用它们的私有 Key。此次只实现官方 API；没有从第三方 App 提取或复用密钥。

## 已核查、暂不作为默认

- OpenRouter：免费模型适合体验，但官方 FAQ 的免费额度/速率限制较低，且需要自己的账户凭据。增加一层路由不一定缩短延迟。
- Cerebras：官方定价对开发/促销额度有条件说明；没有把短期额度当作长期无限免费承诺。
- 云端 TTS：会增加成本、首音等待和第三条服务依赖。本版优先设备英文 TTS，AI 语音块是朗读文本；用户语音块则重放原录音。

## 运行状态

本机 GROQ_API_KEY、SILICONFLOW_API_KEY、OPENROUTER_API_KEY、CEREBRAS_API_KEY、GEMINI_API_KEY、OPENAI_API_KEY 均未配置；未提供后端域名。

因此：代码接入与模拟协议测试已实现，真实服务授权、地区可达性、首字/首音耗时和口音准确率尚未测试。APK 会明确显示“AI 服务准备中”；示例体验必须由用户主动开启，不会自动冒充 AI。

## 官方一手来源

1. Groq 模型与价格：https://console.groq.com/docs/models
2. Groq 转写协议/语言/格式：https://console.groq.com/docs/speech-to-text
3. Groq 推理参数：https://console.groq.com/docs/reasoning
4. Groq 免费层与限流：https://console.groq.com/docs/rate-limits
5. SiliconFlow 免费模型案例：https://docs.siliconflow.cn/docs/usercases/use-siliconcloud-in-BiBiKeyboard
6. SiliconFlow 聊天协议：https://docs.siliconflow.cn/docs/api/chat-completions-post
7. SiliconFlow 转写协议：https://docs.siliconflow.cn/docs/api/audio-transcriptions-post
8. OpenRouter FAQ：https://openrouter.ai/docs/faq
9. Cerebras 定价：https://www.cerebras.ai/pricing
10. 多邻国设计文章：https://blog.duolingo.com/hub/design/

按 2026-10-13 抓取的官方网页记录，不声称覆盖互联网全部服务商。Agent Reach Exa 搜索用于发现来源，价格与接口以官方原文为依据。
