import test from 'node:test';
import assert from 'node:assert/strict';
import { conversationMessages, extractionPrompts, optimizationPrompts, topicPrompts, replaceMessage } from '../public/learning.js';
import { chatPayload, providers } from '../src.mjs';
const messages=[{id:'u1',role:'user',text:'I want talk about coffee.'},{id:'a1',role:'assistant',text:'What do you normally order?'},{id:'h1',role:'hint',text:'PRIVATE_HINT',hintKind:'TOPIC',hintContextIds:['u1','a1']},{id:'u2',role:'user',text:'少点糖'},{id:'a2',role:'assistant',text:'FUTURE_REPLY'}];
test('normal model history has no hint roles or private hint text',()=>{const real=conversationMessages(messages);assert.deepEqual(real.map(x=>x.id),['u1','a1','u2','a2']);assert.ok(!JSON.stringify(real).includes('PRIVATE_HINT'));});
test('recognizing a block sends only its own text and ID',()=>{const prompts=extractionPrompts(messages[0]);assert.ok(prompts[1].content.includes('I want talk'));assert.ok(!JSON.stringify(prompts).includes('What do you normally order'));assert.equal(JSON.parse(prompts[1].content).selectedId,'u1');});
test('optimizing uses original intent and only preceding actual turns',()=>{const prompts=optimizationPrompts(messages,messages[3]);const data=JSON.parse(prompts[1].content);assert.deepEqual(data.precedingContext.map(x=>x.role),['user','assistant']);assert.ok(!JSON.stringify(data).includes('PRIVATE_HINT'));assert.ok(!JSON.stringify(data).includes('FUTURE_REPLY'));assert.equal(data.selected.text,'少点糖');});
test('topic phrase bank filters previous learning hints',()=>{const prompts=topicPrompts(messages);assert.ok(!prompts[1].content.includes('PRIVATE_HINT'));assert.ok(prompts[0].content.includes('4-6'));});
test('regeneration preserves later dialogue and changes only selected revision',()=>{const updated=replaceMessage(messages,'a1','New reply');assert.equal(updated.length,messages.length);assert.equal(updated[1].revision,1);assert.equal(updated[1].text,'New reply');assert.deepEqual(updated.slice(2),messages.slice(2));});
test('hint regeneration retains hint identity and source metadata',()=>{const updated=replaceMessage(messages,'h1','New private hint');assert.equal(updated[2].role,'hint');assert.equal(updated[2].hintKind,'TOPIC');assert.deepEqual(updated[2].hintContextIds,['u1','a1']);assert.ok(!JSON.stringify(conversationMessages(updated)).includes('New private hint'));});
test('original user wording cannot be regenerated or blanked',()=>{assert.throws(()=>replaceMessage(messages,'u1','Changed'));assert.throws(()=>replaceMessage(messages,'a1',' '));});
test('learning output budget allows phrase banks without forcing JSON',()=>{const provider=providers({DASHSCOPE_API_KEY:'fixture'})[0];const payload=chatPayload({messages:topicPrompts(messages),purpose:'learning'},provider);assert.equal(payload.max_tokens,700);assert.equal(payload.response_format,undefined);assert.equal(payload.enable_thinking,false);});


