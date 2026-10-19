import test from 'node:test';
import assert from 'node:assert/strict';
import { createService, providers, chatPayload } from '../src.mjs';

const config = { GROQ_API_KEY: 'fixture-secret', SILICONFLOW_API_KEY: 'second-secret' };
async function service(options, run) {
  const server = createService(options);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const root = `http://127.0.0.1:${server.address().port}`;
  try { await run(root); } finally { await new Promise(resolve => server.close(resolve)); }
}
const completion = { choices: [{ message: { content: 'How was your day?' } }] };
const jsonRequest = (messages = [{ role: 'user', content: 'Hello' }], extra = {}) => ({
  method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ messages, ...extra }),
});

test('health reports inactive honestly and never leaks credentials', async () => {
  await service({ env: {} }, async root => {
    const status = await (await fetch(`${root}/health`)).json();
    assert.equal(status.ready, false);
    assert.equal((await fetch(`${root}/v1/chat/completions`, jsonRequest())).status, 503);
  });
});
test('operator selects model; client cannot request arbitrary expensive models', () => {
  const provider = providers(config)[0];
  const payload = chatPayload({ messages: [{ role: 'user', content: 'Hi' }], model: 'expensive-model', max_tokens: 999999, stream: true }, provider);
  assert.equal(payload.model, 'openai/gpt-oss-20b');
  assert.equal(payload.reasoning_effort, 'low');
  assert.equal(payload.include_reasoning, false);
  assert.equal(payload.reasoning_format, undefined);
  assert.equal(payload.max_completion_tokens, 700);
  assert.equal(payload.stream, true);
});
test('SiliconFlow disables thinking for conversational latency', () => {
  const provider = providers({ ...config, PROVIDER_PRIORITY: 'siliconflow' })[0];
  assert.equal(chatPayload({ messages: [{ role: 'user', content: 'Hi' }] }, provider).enable_thinking, false);
});
test('forwards only operator credentials and fixed model to provider', async () => {
  await service({ env: config, fetchImpl: async (url, options) => {
    assert.equal(url, 'https://api.groq.com/openai/v1/chat/completions');
    assert.equal(options.headers.authorization, 'Bearer fixture-secret');
    assert.equal(JSON.parse(options.body).model, 'openai/gpt-oss-20b');
    return Response.json(completion);
  } }, async root => {
    const response = await fetch(`${root}/v1/chat/completions`, jsonRequest());
    assert.equal(response.status, 200); assert.deepEqual(await response.json(), completion);
  });
});
test('falls back before any streamed output on rate-limit', async () => {
  const calls = [];
  await service({ env: config, fetchImpl: async url => {
    calls.push(url);
    return calls.length === 1 ? new Response('secret upstream error', { status: 429 }) : Response.json(completion);
  } }, async root => {
    assert.equal((await fetch(`${root}/v1/chat/completions`, jsonRequest())).status, 200);
    assert.equal(calls.length, 2); assert.ok(calls[1].startsWith('https://api.siliconflow.cn'));
  });
});
test('proxies SSE immediately rather than buffering a complete reply', async () => {
  const encoder = new TextEncoder(); let push;
  const stream = new ReadableStream({ start(controller) { push = controller; controller.enqueue(encoder.encode('data: {"choices":[{"delta":{"content":"Hello"}}]}\n\n')); } });
  await service({ env: config, fetchImpl: async () => new Response(stream, { headers: { 'content-type': 'text/event-stream' } }) }, async root => {
    const response = await fetch(`${root}/v1/chat/completions`, jsonRequest(undefined, { stream: true }));
    assert.equal(response.headers.get('content-type'), 'text/event-stream; charset=utf-8');
    const reader = response.body.getReader();
    const first = await reader.read(); assert.ok(new TextDecoder().decode(first.value).includes('Hello'));
    push.enqueue(encoder.encode('data: [DONE]\n\n')); push.close();
    assert.equal((await reader.read()).done, false);
  });
});
test('rejects malformed history without invoking providers', async () => {
  await service({ env: config, fetchImpl: async () => { throw new Error('should never call'); } }, async root => {
    assert.equal((await fetch(`${root}/v1/chat/completions`, jsonRequest([{ role: 'tool', content: 'bad' }]))).status, 400);
  });
});
test('keeps provider failures out of client response', async () => {
  await service({ env: config, fetchImpl: async () => new Response('fixture-secret provider-error', { status: 401 }) }, async root => {
    const response = await fetch(`${root}/v1/chat/completions`, jsonRequest());
    assert.equal(response.status, 502); assert.ok(!(await response.text()).includes('fixture-secret'));
  });
});
test('rate limit prevents repeated inference charges', async () => {
  await service({ env: { ...config, MAX_REQUESTS_PER_MINUTE: '1' }, fetchImpl: async () => Response.json(completion) }, async root => {
    assert.equal((await fetch(`${root}/v1/chat/completions`, jsonRequest())).status, 200);
    assert.equal((await fetch(`${root}/v1/chat/completions`, jsonRequest())).status, 429);
  });
});
test('optional service authorization is enforced', async () => {
  await service({ env: { ...config, SERVICE_ACCESS_TOKEN: 'operator-token' } }, async root => {
    assert.equal((await fetch(`${root}/v1/chat/completions`, jsonRequest())).status, 401);
  });
});
test('uploads voice using operator ASR model and preserves original language', async () => {
  await service({ env: config, fetchImpl: async (url, options) => {
    assert.equal(url, 'https://api.groq.com/openai/v1/audio/transcriptions');
    assert.equal(options.body.get('model'), 'whisper-large-v3-turbo');
    assert.equal(options.body.get('language'), 'en');
    assert.equal(await options.body.get('file').text(), 'actual recorded audio');
    return Response.json({ text: 'I go there yesterday.' });
  } }, async root => {
    const form = new FormData(); form.set('file', new Blob(['actual recorded audio']), 'voice.m4a'); form.set('language', 'en'); form.set('model', 'ignored');
    const response = await fetch(`${root}/v1/audio/transcriptions`, { method: 'POST', body: form });
    assert.equal(response.status, 200); assert.equal((await response.json()).text, 'I go there yesterday.');
  });
});
test('rejects unsupported voice language', async () => {
  await service({ env: config }, async root => {
    const form = new FormData(); form.set('file', new Blob(['audio']), 'voice.m4a'); form.set('language', 'xx');
    assert.equal((await fetch(`${root}/v1/audio/transcriptions`, { method: 'POST', body: form })).status, 400);
  });
});

