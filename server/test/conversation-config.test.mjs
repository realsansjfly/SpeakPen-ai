import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { chatPayload, createService, providers } from '../src.mjs';

const messages = [{ role: 'user', content: 'I go there yesterday.' }];
const provider = providers({ DASHSCOPE_API_KEY: 'fixture-key' })[0];

test('old clients retain their chat budget and no forced temperature', () => {
  const payload = chatPayload({ messages }, provider);
  assert.equal(payload.max_tokens, 240);
  assert.equal(payload.temperature, undefined);
});

test('chat length and temperature are explicitly mapped to operator budgets', () => {
  for (const [reply_length, tokens] of [['short', 120], ['standard', 240], ['extended', 400]]) {
    for (const temperature of [0, 0.7, 1]) {
      const payload = chatPayload({ messages, reply_length, temperature, model: 'expensive', max_tokens: 99999 }, provider);
      assert.equal(payload.max_tokens, tokens);
      assert.equal(payload.temperature, temperature);
      assert.equal(payload.model, provider.chat);
      assert.equal(payload.reply_length, undefined);
    }
  }
});

test('invalid chat parameters cannot be coerced or escape the whitelist', () => {
  for (const temperature of [-1, 1.1, NaN, Infinity, '0.7', null, true]) {
    assert.throws(() => chatPayload({ messages, temperature }, provider));
  }
  for (const reply_length of ['unlimited', '__proto__', 'constructor', null, {}, ['short'], 400]) {
    assert.throws(() => chatPayload({ messages, reply_length }, provider));
  }
});

test('summary and learning ignore conversation generation settings', () => {
  for (const [purpose, tokens] of [['summary', 1600], ['learning', 700]]) {
    const payload = chatPayload({ messages, purpose, temperature: 1, reply_length: 'extended', max_tokens: 99999 }, provider);
    assert.equal(payload.max_tokens, tokens);
    assert.equal(payload.temperature, undefined);
  }
});

test('fallback providers enforce the same length whitelist', () => {
  for (const entry of providers({ GROQ_API_KEY: 'fixture', SILICONFLOW_API_KEY: 'fixture' })) {
    const payload = chatPayload({ messages, reply_length: 'short', temperature: 0.3 }, entry);
    assert.equal(payload.max_tokens ?? payload.max_completion_tokens, 120);
    assert.equal(payload.temperature, 0.3);
  }
});

test('HTTP JSON fallback forwards the allowed parameters and rejects invalid ones before inference', async t => {
  const captured = [];
  const server = createService({
    env: { DASHSCOPE_API_KEY: 'fixture', DISABLE_STREAMING: 'true' },
    fetchImpl: async (_url, options) => {
      captured.push(JSON.parse(options.body));
      return Response.json({ choices: [{ message: { content: 'How was it?' } }] });
    },
  });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  t.after(() => { server.closeAllConnections(); server.close(); });
  const request = input => fetch(`http://127.0.0.1:${server.address().port}/v1/chat/completions`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(input),
  });
  const status = await fetch(`http://127.0.0.1:${server.address().port}/v1/status`).then(r => r.json());
  assert.equal(status.conversationControlsVersion, 1);
  const valid = await request({ messages, stream: true, temperature: 0.2, reply_length: 'short' });
  assert.equal(valid.status, 200);
  await valid.json();
  assert.equal(captured[0].temperature, 0.2);
  assert.equal(captured[0].max_tokens, 120);
  assert.equal(captured[0].stream, false);
  const invalid = await request({ messages, temperature: '1' });
  assert.equal(invalid.status, 400);
  await invalid.json();
  assert.equal(captured.length, 1);
});
