// Explicit paid acceptance check. Never restarts or reconfigures the active service.
// Synthetic production requests are exported by ConversationConfigTest during the build.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { parseEnv } from 'node:util';
import { once } from 'node:events';
import assert from 'node:assert/strict';
import { createService, providers } from './src.mjs';

const fixturePath = process.argv[2];
const topicOnly = process.argv.includes('--topic-only');
assert.ok(fixturePath, 'Pass the built conversation-config-live-requests.json path.');
const fixtures = JSON.parse(readFileSync(fixturePath, 'utf8'));
assert.ok(Array.isArray(fixtures) && fixtures.length === (topicOnly ? 3 : 7), 'Unexpected synthetic acceptance request count.');
const configured = { ...parseEnv(readFileSync(new URL('.env', import.meta.url), 'utf8')), ...process.env };
assert.ok(providers(configured).length, 'Local operator credentials are unavailable.');
const forwarded = [];
const server = createService({
  env: { ...configured, DISABLE_STREAMING: 'true', USAGE_FILE: '', SERVICE_ACCESS_TOKEN: '', MAX_REQUESTS_PER_MINUTE: '7', MAX_DAILY_REQUESTS: '7' },
  fetchImpl: (url, options) => {
    const body = JSON.parse(options.body);
    forwarded.push({ model: body.model, temperature: body.temperature, maxTokens: body.max_tokens ?? body.max_completion_tokens, stream: body.stream });
    return fetch(url, options);
  },
});
const results = [];
try {
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  for (const fixture of fixtures) {
    const start = performance.now(), before = forwarded.length;
    try {
      const response = await fetch(`http://127.0.0.1:${server.address().port}/v1/chat/completions`, {
        method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(fixture.body), signal: AbortSignal.timeout(50000),
      });
      const body = await response.json();
      const text = body.choices?.[0]?.message?.content;
      const calls = forwarded.slice(before);
      const expectedTokens = fixture.body.purpose === 'learning' ? 700 : { short: 120, standard: 240, extended: 400 }[fixture.body.reply_length];
      const protocolPass = response.status === 200 && typeof text === 'string' && text.trim().length > 0 &&
        calls.every(call => call.maxTokens === expectedTokens && call.temperature === fixture.body.temperature && !call.stream);
      const checks = fixture.body.purpose === 'learning'
        ? { chineseExplanation: typeof text === 'string' && /\p{Script=Han}/u.test(text) }
        : { noUnsolicitedCorrection: typeof text === 'string' && !/you (?:meant to|should) say|correct sentence|grammar|past tense/i.test(text) };
      if (fixture.body.reply_length === 'short') checks.shortSentences = typeof text === 'string' && (text.match(/[.!?](?:\s|$)/g)?.length ?? 0) <= 2;
      if (topicOnly && typeof text === 'string') {
        const explanations = [...text.matchAll(/中文解释[：:]([^\n]+)/g)].map(match => match[1]);
        const examples = [...text.matchAll(/英文例句[：:]([^\n]+)/g)].map(match => match[1]);
        const translations = [...text.matchAll(/例句意思[：:]([^\n]+)/g)].map(match => match[1]);
        checks.eachItemHasChinese = explanations.length >= 4 && explanations.length <= 6 && explanations.every(line => /\p{Script=Han}/u.test(line));
        checks.eachItemHasEnglishExample = examples.length === explanations.length && examples.every(line => /[a-z]/i.test(line));
        checks.eachExampleHasChineseTranslation = translations.length === explanations.length && translations.every(line => /\p{Script=Han}/u.test(line));
        checks.completed = body.choices?.[0]?.finish_reason === 'stop';
      }
      const pass = protocolPass && Object.values(checks).every(Boolean);
      const result = { name: fixture.name, pass, protocolPass, checks, status: response.status, elapsedMs: Math.round(performance.now() - start),
        forwarded: calls, finishReason: body.choices?.[0]?.finish_reason, usage: body.usage, text: typeof text === 'string' ? text : '' };
      results.push(result);
      console.log(JSON.stringify({ name: result.name, pass, status: result.status, elapsedMs: result.elapsedMs, finishReason: result.finishReason }));
    } catch {
      results.push({ name: fixture.name, pass: false, elapsedMs: Math.round(performance.now() - start), error: 'Request failed; no provider response is logged.' });
      console.log(JSON.stringify({ name: fixture.name, pass: false }));
    }
  }
} finally {
  server.closeAllConnections();
  await new Promise(resolve => server.close(resolve));
}
const outputDirectory = new URL('../.local/', import.meta.url);
mkdirSync(outputDirectory, { recursive: true });
writeFileSync(new URL(topicOnly ? 'debug-topic-live-results.json' : 'conversation-config-live-results.json', outputDirectory), JSON.stringify({ at: new Date().toISOString(), passed: results.every(r => r.pass), results }, null, 2));
process.exitCode = results.every(r => r.pass) ? 0 : 1;
