import { readFileSync, writeFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import { extractionPrompts,optimizationPrompts,topicPrompts,conversationMessages } from './public/learning.js';
import { serviceRequest } from '../scripts/tunnel-network.mjs';
const connection=JSON.parse(readFileSync(new URL('../.local/connection.json',import.meta.url),'utf8'));
const messages=[{id:'u1',role:'user',text:'We are at a coffee shop.'},{id:'a1',role:'assistant',text:'What would you like to order?'},{id:'u2',role:'user',text:'我想说：一杯拿铁，少糖，带走。'},{id:'hidden',role:'hint',text:'PRIVATE_HINT_SHOULD_NOT_APPEAR'}];
const times=[];
async function generate(prompts,purpose){const start=performance.now();const response=await serviceRequest('http://127.0.0.1:8787/v1/chat/completions',{allowLocal:true,token:connection.accessToken,method:'POST',body:{purpose,messages:prompts},timeoutMs:35000});assert.equal(response.status,200);const text=JSON.parse(response.body).choices[0].message.content;assert.ok(text.trim());times.push({purpose,ms:Math.round(performance.now()-start)});return text;}
const extraction=JSON.parse(await generate(extractionPrompts(messages[1]),'summary'));
assert.ok(Array.isArray(extraction.items));assert.ok(extraction.items.length>0);assert.ok(extraction.items.every(x=>x.sourceMessageId==='a1'));
const optimized=await generate(optimizationPrompts(messages,messages[2]),'learning');assert.match(optimized.toLowerCase(),/latte/);assert.ok(optimized.length>20);
const topic=await generate(topicPrompts(messages),'learning');assert.ok(topic.length>60);assert.ok(!topic.includes('PRIVATE_HINT'));
const regenerated=await generate(optimizationPrompts(messages,messages[2]),'learning');assert.ok(regenerated.trim());
assert.ok(!JSON.stringify(conversationMessages(messages)).includes('PRIVATE_HINT'));
writeFileSync(new URL('../.local/learning-live-results.json',import.meta.url),JSON.stringify({passed:true,at:new Date().toISOString(),times,extracted:extraction.items,optimized,topic},null,2));
console.log(JSON.stringify({passed:true,selectedOnlyItems:extraction.items.length,times,optimizePreservesLatte:true,topicCharacters:topic.length,privateHintsExcluded:true}));
