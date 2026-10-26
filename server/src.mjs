import http from 'node:http';
import { fileURLToPath } from 'node:url';
import { timingSafeEqual, createHash, randomUUID } from 'node:crypto';
import { once } from 'node:events';
import { readFileSync, writeFileSync, renameSync, existsSync, createReadStream, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

const root = dirname(fileURLToPath(import.meta.url));
export function providers(env = process.env) {
  const choices = {
    dashscope: env.DASHSCOPE_API_KEY ? {
      name: 'dashscope', base: env.DASHSCOPE_BASE_URL || 'https://dashscope.aliyuncs.com/compatible-mode/v1', key: env.DASHSCOPE_API_KEY,
      chat: env.DASHSCOPE_CHAT_MODEL || 'qwen3.7-flash-2026-07-15', asr: env.DASHSCOPE_ASR_MODEL || 'qwen3-asr-flash',
    } : null,
    groq: env.GROQ_API_KEY ? {
      name: 'groq', base: 'https://api.groq.com/openai/v1', key: env.GROQ_API_KEY,
      chat: env.GROQ_CHAT_MODEL || 'openai/gpt-oss-20b', asr: env.GROQ_ASR_MODEL || 'whisper-large-v3-turbo',
    } : null,
    siliconflow: env.SILICONFLOW_API_KEY ? {
      name: 'siliconflow', base: 'https://api.siliconflow.cn/v1', key: env.SILICONFLOW_API_KEY,
      chat: env.SILICONFLOW_CHAT_MODEL || 'Qwen/Qwen3-8B', asr: env.SILICONFLOW_ASR_MODEL || 'FunAudioLLM/SenseVoiceSmall',
    } : null,
  };
  return (env.PROVIDER_PRIORITY || 'dashscope,groq,siliconflow').split(',').map(x => choices[x.trim()]).filter(Boolean);
}
const json = (res, code, value) => {
  if (res.headersSent || res.destroyed) return;
  res.writeHead(code, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
  res.end(JSON.stringify(value));
};
const problem = (res, status, code, message) => json(res, status, { error: { code, message } });
export const authorized = (request, token) => {
  if (!token) return true;
  const expected = Buffer.from(`Bearer ${token}`), provided = Buffer.from(request.headers.authorization || '');
  return expected.length === provided.length && timingSafeEqual(expected, provided);
};
async function body(request, limit) {
  const parts = []; let length = 0;
  for await (const part of request) {
    length += part.length;
    if (length > limit) throw Object.assign(new Error('Payload too large'), { status: 413 });
    parts.push(part);
  }
  return Buffer.concat(parts);
}
export function chatPayload(input, provider, stream = input.stream === true) {
  if (!Array.isArray(input.messages) || !input.messages.length || input.messages.length > 48) throw new Error('Invalid messages');
  let total = 0;
  const messages = input.messages.map(message => {
    if (!['system', 'user', 'assistant'].includes(message.role) || typeof message.content !== 'string' || message.content.length > 20000) throw new Error('Invalid message');
    total += message.content.length;
    return { role: message.role, content: message.content };
  });
  if (total > 70000) throw new Error('Context too large');
  const auxiliary = ['summary', 'learning'].includes(input.purpose);
  let chatLimit = 240;
  if (!auxiliary && input.reply_length !== undefined) {
    const limits = { short: 120, standard: 240, extended: 400 };
    if (typeof input.reply_length !== 'string' || !Object.hasOwn(limits, input.reply_length)) throw new Error('Invalid reply length');
    chatLimit = limits[input.reply_length];
  }
  if (!auxiliary && input.temperature !== undefined &&
      (typeof input.temperature !== 'number' || !Number.isFinite(input.temperature) || input.temperature < 0 || input.temperature > 1)) {
    throw new Error('Invalid temperature');
  }
  const payload = { model: provider.chat, messages, stream, max_tokens: input.purpose === 'summary' ? 1600 : input.purpose === 'learning' ? 700 : chatLimit };
  if (!auxiliary && input.temperature !== undefined) payload.temperature = input.temperature;
  if (['siliconflow', 'dashscope'].includes(provider.name)) payload.enable_thinking = false;
  if (input.purpose === 'summary' && provider.name === 'dashscope') payload.response_format = { type: 'json_object' };
  if (provider.name === 'groq' && provider.chat.startsWith('openai/gpt-oss')) {
    payload.reasoning_effort = 'low'; payload.include_reasoning = false;
    delete payload.max_tokens; payload.max_completion_tokens = input.purpose === 'summary' ? 2200 : input.purpose === 'learning' ? 1200 : input.reply_length === undefined ? 700 : chatLimit;
  }
  return payload;
}
export async function audioRequest(input, provider) {
  if (provider.name === 'dashscope') {
    const type = ['audio/wav', 'audio/mp4', 'audio/mpeg', 'audio/webm', 'audio/ogg', 'audio/flac'].includes(input.file.type) ? input.file.type : 'audio/mp4';
    const data = Buffer.from(await input.file.arrayBuffer()).toString('base64');
    const glossary = (input.prompt || '').split('Relevant vocabulary: ')[1]?.trim();
    const messages = [];
    if (glossary) messages.push({ role: 'system', content: glossary.slice(0, 500) });
    messages.push({ role: 'user', content: [{ type: 'input_audio', input_audio: { data: `data:${type};base64,${data}` } }] });
    return { path: '/chat/completions', type: 'application/json', payload: JSON.stringify({
      model: provider.asr, messages, stream: false,
      // Mixed Chinese/English help uses automatic language detection.
      asr_options: { ...(input.language === 'en' ? { language: 'en' } : {}), enable_itn: false },
    }) };
  }
  const payload = new FormData(); payload.set('file', input.file, 'practice.m4a'); payload.set('model', provider.asr);
  if (provider.name === 'groq') { payload.set('language', input.language); payload.set('prompt', input.prompt); payload.set('response_format', 'json'); }
  return { path: '/audio/transcriptions', payload };
}

export function createService({ env = process.env, fetchImpl = fetch, now = Date.now } = {}) {
  const upstreams = providers(env);
  const instanceId = randomUUID();
  const buckets = new Map(); let daily = { day: '', count: 0 };
  if (env.USAGE_FILE && existsSync(env.USAGE_FILE)) {
    try { const saved = JSON.parse(readFileSync(env.USAGE_FILE, 'utf8')); if (typeof saved.day === 'string' && Number.isSafeInteger(saved.count) && saved.count >= 0) daily = saved; }
    catch { throw new Error('Usage file is unreadable; recover it before starting paid inference.'); }
  }
  const speechCache = new Map();
  const metrics = { requests: 0, lastSuccessAt: null, chatMs: null, transcriptionMs: null };
  const server = http.createServer(async (request, res) => {
    res.setHeader('X-Content-Type-Options', 'nosniff'); res.setHeader('Referrer-Policy', 'no-referrer');
    const path = new URL(request.url, 'http://localhost').pathname;
    if (request.method === 'GET' && path === '/health') return json(res, 200, {
      ready: upstreams.length > 0, version: '0.4.0', service: 'SpeakPen', speech: upstreams.length > 0,
    });
    const assets = { '/': ['index.html', 'text/html; charset=utf-8'], '/app.js': ['app.js', 'text/javascript; charset=utf-8'], '/app.css': ['app.css', 'text/css; charset=utf-8'], '/learning.js': ['learning.js', 'text/javascript; charset=utf-8'] };
    if (request.method === 'GET' && assets[path]) {
      const [filename, mime] = assets[path];
      const file = join(root, 'public', filename);
      if (!existsSync(file)) return problem(res, 404, 'not_found', 'Web interface unavailable.');
      res.writeHead(200, { 'content-type': mime, 'cache-control': 'no-store',
        'content-security-policy': "default-src 'self'; script-src 'self'; style-src 'self'; media-src 'self' blob:; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'" });
      createReadStream(file).pipe(res); return;
    }
    if (!authorized(request, env.SERVICE_ACCESS_TOKEN)) return problem(res, 401, 'unauthorized', 'Service authorization required.');
    if (request.method === 'GET' && path === '/v1/status') return json(res, 200, {
      service: 'SpeakPen', instanceId, ready: upstreams.length > 0, chatModel: upstreams[0]?.chat, asrModel: upstreams[0]?.asr,
      streaming: env.DISABLE_STREAMING !== 'true', version: '0.4.0', conversationControlsVersion: 1, ...metrics,
    });
    if (request.method === 'GET' && path === '/app.apk') {
      const file = resolve(env.APK_PATH || join(root, '../deliverables/SpeakPen-0.4.0-debug.apk'));
      if (!existsSync(file)) return problem(res, 404, 'not_found', 'APK build is pending.');
      res.writeHead(200, { 'content-type': 'application/vnd.android.package-archive', 'content-length': statSync(file).size,
        'content-disposition': 'attachment; filename="SpeakPen-0.4.0.apk"', 'cache-control': 'no-store' });
      createReadStream(file).pipe(res); return;
    }
    if (request.method !== 'POST' || !['/v1/chat/completions', '/v1/audio/transcriptions', '/v1/audio/speech'].includes(path)) return problem(res, 404, 'not_found', 'Unknown endpoint.');
    if (!upstreams.length) return problem(res, 503, 'not_configured', 'AI service is not activated. Operator credentials are required.');
    const stamp = now(), day = new Date(stamp).toISOString().slice(0, 10), address = request.socket.remoteAddress;
    if (daily.day !== day) daily = { day, count: 0 };
    if (buckets.size > 1000) for (const [key, value] of buckets) if (stamp - value.start >= 60000) buckets.delete(key);
    let bucket = buckets.get(address);
    if (!bucket || stamp - bucket.start >= 60000) { bucket = { start: stamp, count: 0 }; buckets.set(address, bucket); }
    if (bucket.count >= Number(env.MAX_REQUESTS_PER_MINUTE || 30) || daily.count >= Number(env.MAX_DAILY_REQUESTS || 500)) return problem(res, 429, 'rate_limit', 'Service quota reached. Try again later.');
    bucket.count++; daily.count++; metrics.requests++;
    if (env.USAGE_FILE) {
      try { const temp = `${env.USAGE_FILE}.tmp`; writeFileSync(temp, JSON.stringify(daily), { mode: 0o600 }); renameSync(temp, env.USAGE_FILE); }
      catch { return problem(res, 503, 'quota_storage_unavailable', 'Unable to save service quota.'); }
    }
    const abort = new AbortController();
    const deadline = setTimeout(() => abort.abort(), path.includes('audio') ? 30000 : 45000);
    res.on('close', () => { if (!res.writableEnded) abort.abort(); });
    request.on('aborted', () => abort.abort());
    try {
      if (path === '/v1/audio/speech') {
        const input = JSON.parse((await body(request, 12 * 1024)).toString('utf8'));
        if (typeof input.input !== 'string' || !input.input.trim() || input.input.length > 2000) throw new Error('Invalid speech input');
        const provider = upstreams.find(item => item.name === 'dashscope');
        if (!provider) return problem(res, 503, 'tts_unavailable', 'Cloud speech is unavailable.');
        const hash = createHash('sha256').update(input.input).digest('hex');
        let bytes = speechCache.get(hash);
        if (!bytes) {
          const ttsResponse = await fetchImpl('https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation', {
            method: 'POST', headers: { authorization: `Bearer ${provider.key}`, 'content-type': 'application/json' },
            body: JSON.stringify({ model: env.DASHSCOPE_TTS_MODEL || 'qwen3-tts-flash', input: {
              text: input.input, voice: 'Cherry', language_type: /[\u3400-\u9fff]/.test(input.input) ? 'Chinese' : 'English',
            } }), signal: abort.signal, redirect: 'error',
          });
          if (!ttsResponse.ok) return problem(res, 502, 'tts_unavailable', 'Unable to generate speech.');
          const result = await ttsResponse.json();
          const url = new URL(result.output?.audio?.url);
          if (!['http:', 'https:'].includes(url.protocol) || !url.hostname.endsWith('.aliyuncs.com') || url.username || url.password || url.port) throw Object.assign(new Error('Invalid audio host'), { status: 502 });
          url.protocol = 'https:';
          // Never forward the provider authorization header to a signed download URL.
          const download = await fetchImpl(url.toString(), { signal: abort.signal, redirect: 'error' });
          if (!download.ok) throw Object.assign(new Error('Audio unavailable'), { status: 502 });
          const chunks = []; let length = 0;
          for await (const chunk of download.body) { length += chunk.length; if (length > 6 * 1024 * 1024) throw Object.assign(new Error('Audio too large'), { status: 502 }); chunks.push(chunk); }
          bytes = Buffer.concat(chunks);
          if (!bytes.length) throw Object.assign(new Error('Empty audio'), { status: 502 });
          if (speechCache.size >= 24) speechCache.delete(speechCache.keys().next().value);
          speechCache.set(hash, bytes);
        }
        res.writeHead(200, { 'content-type': 'audio/wav', 'content-length': bytes.length, 'cache-control': 'private, max-age=300' });
        res.end(bytes); metrics.lastSuccessAt = new Date(now()).toISOString(); return;
      }
      const audio = path.includes('audio');
      const raw = await body(request, audio ? 6 * 1024 * 1024 : 128 * 1024);
      let input;
      if (audio) {
        const type = request.headers['content-type'] || '';
        if (!type.startsWith('multipart/form-data')) throw new Error('Multipart required');
        const form = await new Response(raw, { headers: { 'content-type': type } }).formData();
        const file = form.get('file'), language = form.get('language');
        if (!(file instanceof Blob) || !file.size || !['en', 'zh'].includes(language)) throw new Error('Invalid audio input');
        input = { file, language, prompt: String(form.get('prompt') || '').slice(0, 600) };
      } else { input = JSON.parse(raw.toString('utf8')); chatPayload(input, upstreams[0]); }
      const useStream = !audio && input.stream === true && env.DISABLE_STREAMING !== 'true';
      let response, chosen;
      for (const provider of upstreams) {
        try {
          const requestData = audio ? await audioRequest(input, provider)
            : { path: '/chat/completions', type: 'application/json', payload: JSON.stringify(chatPayload(input, provider, useStream)) };
          const headers = { authorization: `Bearer ${provider.key}`, ...(requestData.type ? { 'content-type': requestData.type } : {}) };
          response = await fetchImpl(`${provider.base}${requestData.path}`, {
            method: 'POST', headers, body: requestData.payload,
            signal: AbortSignal.any([abort.signal, AbortSignal.timeout(audio ? 20000 : 12000)]), redirect: 'error',
          });
          if (response.ok) { chosen = provider; break; }
          await response.body?.cancel(); response = null;
        } catch (error) { if (abort.signal.aborted) throw error; response = null; }
      }
      if (!response?.ok) return problem(res, 502, 'upstream_unavailable', 'AI service temporarily unavailable. Your message is retained.');
      if (useStream) {
        if (!response.headers.get('content-type')?.includes('text/event-stream')) throw Object.assign(new Error('Invalid stream'), { status: 502 });
        res.writeHead(200, { 'content-type': 'text/event-stream; charset=utf-8', 'cache-control': 'no-cache', 'x-accel-buffering': 'no' });
        res.flushHeaders();
        for await (const chunk of response.body) { if (!res.write(chunk)) await once(res, 'drain', { signal: abort.signal }); }
        res.end();
      } else {
        let result = await response.json();
        if (audio && chosen.name === 'dashscope') result = { text: result.choices?.[0]?.message?.content };
        if (audio && (typeof result.text !== 'string' || !result.text.trim())) throw Object.assign(new Error('Invalid transcription'), { status: 502 });
        if (!audio && !Array.isArray(result.choices)) throw Object.assign(new Error('Invalid completion'), { status: 502 });
        json(res, 200, result);
      }
      metrics.lastSuccessAt = new Date(now()).toISOString();
      metrics[audio ? 'transcriptionMs' : 'chatMs'] = now() - stamp;
    } catch (error) {
      if (res.headersSent) res.destroy();
      else problem(res, error.status || (abort.signal.aborted ? 504 : 400), abort.signal.aborted ? 'timeout' : 'invalid_request',
        abort.signal.aborted ? 'AI service timed out. Try again.' : 'Unable to process request.');
    } finally { clearTimeout(deadline); }
  });
  server.requestTimeout = 30000;
  return server;
}
if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const host = process.env.HOST || '127.0.0.1', port = Number(process.env.PORT || 8787);
  createService().listen(port, host, () => console.log(`SpeakPen service listening on ${host}:${port} — ${providers().length ? 'AI configured' : 'operator credentials missing'}`));
}


