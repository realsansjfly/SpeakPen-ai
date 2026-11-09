import test from 'node:test';
import assert from 'node:assert/strict';
import { createService, providers, speechProvider, normalizeWav, chatPayload, audioRequest } from '../src.mjs';

const config = { DASHSCOPE_API_KEY: 'alibaba-fixture-secret' };
const completion = { choices: [{ message: { content: 'What made you smile?' } }] };
async function service(options, action) {
 const server=createService(options);await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 try{await action(`http://127.0.0.1:${server.address().port}`);}finally{await new Promise(resolve=>server.close(resolve));}
}
const post = input => ({method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(input)});

test('custom compatible gateway receives its own key for chat and Qwen ASR', async () => {
 const env = { DASHSCOPE_API_KEY: 'gateway.test-key', DASHSCOPE_BASE_URL: 'https://maas.qianwenaiapi.com/compatible-mode/v1/', DASHSCOPE_CHAT_MODEL: 'gateway-chat' };
 let calls = 0;
 await service({ env, fetchImpl: async (url, options) => {
  calls++;
  assert.equal(url, 'https://maas.qianwenaiapi.com/compatible-mode/v1/chat/completions');
  assert.equal(options.headers.authorization, 'Bearer gateway.test-key');
  assert.equal(JSON.parse(options.body).model, calls === 1 ? 'gateway-chat' : 'qwen3-asr-flash');
  return Response.json(completion);
 } }, async root => {
  assert.equal((await fetch(root + '/v1/chat/completions', post({ messages: [{ role: 'user', content: 'Hi' }] }))).status, 200);
  const audio = new FormData(); audio.set('file', new Blob(['fixture']), 'test.m4a'); audio.set('language', 'en');
  assert.equal((await fetch(root + '/v1/audio/transcriptions', { method: 'POST', body: audio })).status, 200);
 });
 assert.equal(calls, 2);
});

test('custom gateway key is never sent to the official native TTS host', async () => {
 let calls = 0;
 await service({ env: { DASHSCOPE_API_KEY: 'gateway.test-key', DASHSCOPE_BASE_URL: 'https://maas.qianwenaiapi.com/compatible-mode/v1' },
  fetchImpl: async () => { calls++; throw Error('must not call another provider'); } }, async root => {
  assert.equal((await fetch(root + '/v1/audio/speech', post({ input: 'Hello' }))).status, 503);
 });
 assert.equal(calls, 0);
});
test('independent official TTS key works without changing the custom chat gateway', async () => {
 const env = { DASHSCOPE_API_KEY: 'gateway.test-key', DASHSCOPE_BASE_URL: 'https://maas.qianwenaiapi.com/compatible-mode/v1',
  DASHSCOPE_TTS_API_KEY: 'official.tts-secret', DASHSCOPE_TTS_VOICE: 'Cherry' };
 const selected = speechProvider(env, providers(env));
 assert.equal(selected.key, 'official.tts-secret');
 let calls = 0;
 await service({ env, fetchImpl: async (url, options) => {
  calls++;
  if (calls === 1) {
   assert.equal(url, 'https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation');
   assert.equal(options.headers.authorization, 'Bearer official.tts-secret');
   assert.ok(!options.headers.authorization.includes('gateway.test-key'));
   const input = JSON.parse(options.body);
   assert.equal(input.model, 'qwen3-tts-flash'); assert.equal(input.input.voice, 'Cherry');
   return Response.json({ output: { audio: { url: 'https://audio-test.oss-cn-beijing.aliyuncs.com/result.wav?signature=fixture' } } });
  }
  assert.equal(options.headers, undefined);
  return new Response('RIFF independent TTS', { headers: { 'content-type': 'audio/wav' } });
 } }, async root => {
  const response = await fetch(root + '/v1/audio/speech', post({ input: 'Please speak naturally.' }));
  assert.equal(response.status, 200); assert.equal(await response.text(), 'RIFF independent TTS');
  const status = await (await fetch(root + '/v1/status')).json();
  assert.equal(status.speech, true); assert.equal(status.speechModel, 'qwen3-tts-flash');
  assert.ok(!JSON.stringify(status).includes('official.tts-secret'));
 });
 assert.equal(calls, 2);
});
test('normalizes streaming WAV placeholder lengths for Android playback', () => {
 const wav = Buffer.alloc(48);
 wav.write('RIFF', 0); wav.writeUInt32LE(0x7fffffbf, 4); wav.write('WAVEfmt ', 8); wav.writeUInt32LE(16, 16);
 wav.writeUInt16LE(1, 20); wav.writeUInt16LE(1, 22); wav.writeUInt32LE(24000, 24); wav.writeUInt32LE(48000, 28);
 wav.writeUInt16LE(2, 32); wav.writeUInt16LE(16, 34); wav.write('data', 36); wav.writeUInt32LE(0x7fffff9b, 40);
 const normalized = normalizeWav(wav);
 assert.notEqual(normalized, wav); assert.equal(normalized.readUInt32LE(4), 40); assert.equal(normalized.readUInt32LE(40), 4);
 assert.equal(wav.readUInt32LE(4), 0x7fffffbf);
});
test('Alibaba is selected ahead of optional alternatives',()=>{const first=providers({...config,GROQ_API_KEY:'other'})[0];assert.equal(first.name,'dashscope');assert.equal(first.chat,'qwen3.7-flash-2026-07-15');assert.equal(first.asr,'qwen3-asr-flash');});
test('Alibaba chat disables reasoning and summary requests valid JSON',()=>{const payload=chatPayload({purpose:'summary',messages:[{role:'user',content:'Output JSON.'}]},providers(config)[0]);assert.equal(payload.enable_thinking,false);assert.deepEqual(payload.response_format,{type:'json_object'});assert.equal(payload.max_tokens,1600);});
test('Qwen ASR uses input_audio base64 instead of unsupported multipart endpoint',async()=>{const result=await audioRequest({file:new Blob(['original recording'],{type:'audio/mp4'}),language:'en',prompt:'English practice. Relevant vocabulary: hiking'},providers(config)[0]);assert.equal(result.path,'/chat/completions');const json=JSON.parse(result.payload);assert.equal(json.model,'qwen3-asr-flash');assert.equal(json.messages[0].content,'hiking');const data=json.messages[1].content[0].input_audio.data;assert.equal(Buffer.from(data.split(',')[1],'base64').toString(),'original recording');assert.equal(json.asr_options.language,'en');assert.equal(json.asr_options.enable_itn,false);});
test('Chinese help allows automatic recognition of mixed English speech',async()=>{const result=await audioRequest({file:new Blob(['audio']),language:'zh'},providers(config)[0]);assert.equal(JSON.parse(result.payload).asr_options.language,undefined);});
test('ASR completion is normalized to the Android text contract',async()=>{await service({env:config,fetchImpl:async()=>Response.json({choices:[{message:{content:'I go there yesterday.'}}]})},async root=>{const body=new FormData();body.set('file',new Blob(['audio']),'practice.m4a');body.set('language','en');const result=await fetch(root+'/v1/audio/transcriptions',{method:'POST',body});assert.equal(result.status,200);assert.deepEqual(await result.json(),{text:'I go there yesterday.'});});});
test('temporary tunnel mode returns JSON when app requests streaming',async()=>{await service({env:{...config,DISABLE_STREAMING:'true'},fetchImpl:async(url,options)=>{assert.equal(JSON.parse(options.body).stream,false);return Response.json(completion);}},async root=>{const response=await fetch(root+'/v1/chat/completions',post({stream:true,messages:[{role:'user',content:'Hi'}]}));assert.ok(response.headers.get('content-type').includes('application/json'));assert.deepEqual(await response.json(),completion);});});
test('service status is protected and never exposes provider or pairing secret',async()=>{await service({env:{...config,SERVICE_ACCESS_TOKEN:'client-secret'}},async root=>{assert.equal((await fetch(root+'/v1/status')).status,401);const response=await fetch(root+'/v1/status',{headers:{authorization:'Bearer client-secret'}});const text=await response.text();assert.ok(text.includes('qwen3.7-flash'));assert.ok(!text.includes('alibaba-fixture-secret'));assert.ok(!text.includes('client-secret'));});});
test('APK download requires local service authorization',async()=>{await service({env:{...config,SERVICE_ACCESS_TOKEN:'client-secret'}},async root=>{assert.equal((await fetch(root+'/app.apk')).status,401);});});
test('cloud speech is normalized to audio and signed download gets no provider key',async()=>{let calls=0;await service({env:config,fetchImpl:async(url,options)=>{calls++;if(calls===1){assert.ok(url.includes('multimodal-generation'));const request=JSON.parse(options.body);assert.equal(request.model,'qwen3-tts-flash');assert.equal(request.input.language_type,'English');return Response.json({output:{audio:{url:'http://audio-test.oss-cn-beijing.aliyuncs.com/result.wav?signature=fixture'}}});}assert.ok(url.startsWith('https:'));assert.equal(options.headers,undefined);return new Response('RIFF fixture audio',{headers:{'content-type':'audio/wav'}});}},async root=>{const response=await fetch(root+'/v1/audio/speech',post({input:'Hello there.'}));assert.equal(response.status,200);assert.equal(await response.text(),'RIFF fixture audio');const repeat=await fetch(root+'/v1/audio/speech',post({input:'Hello there.'}));assert.equal(repeat.status,200);assert.equal(calls,2);});});
test('rejects provider audio URLs pointing to private or unrelated hosts',async()=>{await service({env:config,fetchImpl:async()=>Response.json({output:{audio:{url:'https://127.0.0.1/private'}}})},async root=>{const response=await fetch(root+'/v1/audio/speech',post({input:'Hello'}));assert.equal(response.status,502);});});
test('rejects empty or excessively long speech input',async()=>{await service({env:config},async root=>{assert.equal((await fetch(root+'/v1/audio/speech',post({input:''}))).status,400);assert.equal((await fetch(root+'/v1/audio/speech',post({input:'a'.repeat(2001)}))).status,400);});});
