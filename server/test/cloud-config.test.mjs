import test from 'node:test';
import assert from 'node:assert/strict';
import { createService } from '../src.mjs';

test('public cloud refuses to start without its own strong access token', () => {
  for (const token of [undefined, '', 'sk-provider-key', 'a'.repeat(63)]) {
    assert.throws(() => createService({ env: { REQUIRE_AUTH: 'true', SERVICE_ACCESS_TOKEN: token } }), /SERVICE_ACCESS_TOKEN/);
  }
  const service = createService({ env: { REQUIRE_AUTH: 'true', SERVICE_ACCESS_TOKEN: 'a'.repeat(64) } });
  service.close();
});
