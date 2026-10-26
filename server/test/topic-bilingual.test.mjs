import test from 'node:test';
import assert from 'node:assert/strict';
import { replaceMessage } from '../public/learning.js';

test('an English-only topic regeneration is rejected and the previous bilingual hint survives', () => {
  const original = [{ id: 'topic', role: 'hint', hintKind: 'TOPIC', text: 'catch up — 叙旧\nLet us catch up.' }];
  assert.throws(() => replaceMessage(original, 'topic', 'Useful expressions\nCatch up: talk with someone you know.'), /中文/);
  assert.equal(original[0].text, 'catch up — 叙旧\nLet us catch up.');
});

test('a bilingual topic regeneration remains editable and private', () => {
  const original = [{ id: 'topic', role: 'hint', hintKind: 'TOPIC', text: 'old' }];
  const result = replaceMessage(original, 'topic', '聊聊近况\ncatch up — 叙旧\nLet us catch up.');
  assert.equal(result[0].role, 'hint');
  assert.equal(result[0].revision, 1);
});
