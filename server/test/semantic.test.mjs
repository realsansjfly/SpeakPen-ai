import test from 'node:test';
import assert from 'node:assert/strict';
import { retrievalInput, retrieve } from '../semantic.mjs';
import { createService, chatPayload, providers } from '../src.mjs';
const input = { query: '想找住的地方', count: 4, books: ['anki-cet4'], level: 'DAILY' };
const token = 'a'.repeat(64);
async function service(semanticFetch, run) {
  const server = createService({ env: { SERVICE_ACCESS_TOKEN: token }, semanticFetch });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await run(`http://127.0.0.1:${server.address().port}`); } finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
}
const request = (value = input, auth = true) => ({ method: 'POST', headers: { 'content-type': 'application/json', ...(auth ? { authorization: `Bearer ${token}` } : {}) }, body: JSON.stringify(value) });
test('retrieval strict query, enums, counts and book limits', () => {
  assert.deepEqual(retrievalInput(input), input);
  for (const value of [{}, { ...input, query: 'x'.repeat(3001) }, { ...input, count: 6 }, { ...input, books: ['private-path'] }, { ...input, level: 'arbitrary' }]) assert.throws(() => retrievalInput(value));
});
test('retrieval uses only loopback and service auth; does not require paid provider', async () => {
  await service(async (url, options) => {
    assert.equal(url, 'http://127.0.0.1:8791/search'); assert.equal(options.headers.authorization, `Bearer ${token}`);
    assert.deepEqual(JSON.parse(options.body), input);
    return Response.json({ version: 'index-1', candidates: [{ id: 'a', expression: 'accommodation', meaning: '住处', book: 'anki-cet4' }] });
  }, async root => {
    assert.equal((await fetch(`${root}/v1/vocabulary/search`, request(input, false))).status, 401);
    const response = await fetch(`${root}/v1/vocabulary/search`, request());
    const result = await response.json(); assert.equal(result.version, 'index-1'); assert.equal(result.candidates.length, 1);
    assert.equal((await fetch(`${root}/v1/vocabulary/search`, request({ ...input, count: 999 }))).status, 400);
    assert.equal((await fetch(`${root}/v1/vocabulary/search`, request({ ...input, query: 'x'.repeat(20000) }))).status, 400);
  });
});
test('worker failure or malformed result safely falls back without credentials', async () => {
  for (const mock of [async () => { throw Error('secret'); }, async () => Response.json({ version: 'v', candidates: [{ book: 'other' }] }), async () => new Response('', { status: 503 })]) {
    await service(mock, async root => {
      const result = await (await fetch(`${root}/v1/vocabulary/search`, request())).json();
      assert.equal(result.fallback, true); assert.deepEqual(result.candidates, []); assert.ok(!JSON.stringify(result).includes('secret'));
    });
  }
});
test('deadline aborts a stalled retrieval', async () => {
  await assert.rejects(retrieve(input, token, async (_, options) => new Promise((resolve, reject) => {
    options.signal.addEventListener('abort', () => reject(options.signal.reason));
    setTimeout(resolve, 100);
  }), 10));
});
test('topic bank output budget and fixed provider remain bounded', () => {
  const payload = chatPayload({ purpose: 'topic_bank', messages: [{ role: 'user', content: 'hello' }], max_tokens: 90000 }, providers({ DASHSCOPE_API_KEY: 'fixture' })[0]);
  assert.equal(payload.max_tokens, 2600);
});
