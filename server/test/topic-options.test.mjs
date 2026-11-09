import test from 'node:test';
import assert from 'node:assert/strict';
import { latestTopicRound, topicOptions, topicPrompts, topicBankText } from '../public/learning.js';
const round = [{ id: 'u', role: 'user', text: 'Orange juice, please.' }, { id: 'a', role: 'assistant', text: 'Regular or large?' }];
test('latest boundary rejects unanswered, empty and hint-only rounds without falling back', () => {
  for (const messages of [[], [round[1]], [...round, {role:'user',text:''}], [...round, {role:'user',text:'unanswered'}]]) assert.throws(() => latestTopicRound(messages));
  assert.deepEqual(latestTopicRound([...round, {role:'hint',text:'private'}]), round);
  for (const options of [{count:3},{count:11},{count:4.5},{purpose:'system'},{difficulty:'unbounded'}]) assert.throws(() => topicOptions(options));
});
test('all purposes and difficulties support 4-10 complete bilingual items', () => {
  for (const purpose of ['ANSWER','FOLLOW_UP','RELATED']) for (const difficulty of ['DEFAULT','ADVANCED','IDIOMATIC']) for (const count of [4,5,10]) {
    const options = { purpose, difficulty, count };
    assert.deepEqual(JSON.parse(topicPrompts(round, options)[1].content), {...options, round});
    const items = Array.from({length:count}, () => ({expression:'A large, please.',meaning:'礼貌选择大杯。',example:'A large orange juice, please.',translation:'请给我大杯橙汁。'}));
    assert.ok(topicBankText(JSON.stringify({items}), options).includes('大杯'));
    assert.throws(() => topicBankText(JSON.stringify({items:items.slice(1)}), options));
    items[0].meaning = 'No Chinese';
    assert.throws(() => topicBankText(JSON.stringify({items}), options));
  }
});
