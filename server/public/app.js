import {isConversation,conversationMessages,extractionPrompts,optimizationPrompts,topicPrompts,replaceMessage} from '/learning.js';
'use strict';
const $ = id => document.getElementById(id);
let token = sessionStorage.getItem('speakpen-token') || '';
const fragment = new URLSearchParams(location.hash.slice(1));
if (fragment.get('token')) { token = fragment.get('token'); sessionStorage.setItem('speakpen-token', token); history.replaceState(null, '', location.pathname); }
const saved = (() => { try { return JSON.parse(localStorage.getItem('speakpen-web-v1') || '{}'); } catch { return {}; } })();
let messages = saved.messages || [], library = saved.library || [], mode = 'free', targets = [], help = false, busy = false, voiceBlocks = true, candidates = [], audioPlayer = null, recording = null, starting = false, releaseDuringStart = false, cancelled = false;
const playbackUrls = new Map();
const db = new Promise((resolve, reject) => {
 const request = indexedDB.open('speakpen-audio', 1);
 request.onupgradeneeded = () => request.result.createObjectStore('recordings');
 request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error);
});
async function audioStore(id, blob) { const store = (await db).transaction('recordings', blob ? 'readwrite' : 'readonly').objectStore('recordings'); return new Promise((resolve, reject) => { const request = blob ? store.put(blob, id) : store.get(id); request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error); }); }
function save() { localStorage.setItem('speakpen-web-v1', JSON.stringify({ messages, library })); }
function feedback(text = '') { $('feedback').textContent = text; $('feedback').hidden = !text; }
async function request(path, data, multipart = false) {
 const response = await fetch(`/v1/${path}`, { method: 'POST', headers: { authorization: `Bearer ${token}`, ...(multipart ? {} : { 'content-type': 'application/json' }) }, body: multipart ? data : JSON.stringify(data), signal: AbortSignal.timeout(45000) });
 if (!response.ok) throw new Error(response.status === 401 ? '服务连接已失效，请重新打开电脑生成的连接入口。' : response.status === 429 ? '请求较多，请稍后再试。' : '服务暂时没有响应，消息已保留，可以重试。');
 return response;
}
async function checkStatus() {
 $('connect').hidden = !!token;
 if (!token) { $('status').textContent = '请先连接这台电脑'; return; }
 try { const response = await fetch('/v1/status', { headers: { authorization: `Bearer ${token}` } }); if (!response.ok) throw Error(); const result = await response.json(); $('status').textContent = result.ready ? '已连接 · 千问对话与语音识别' : '服务尚未就绪'; }
 catch { $('status').textContent = '连接不可用'; $('connect').hidden = false; }
}
function setBusy(value) { busy = value; ['send', 'begin', 'new', 'undo', 'summarize', 'free', 'review','topic-expressions'].forEach(id => $(id).disabled = value); $('hold').disabled = value; }
function lastConversation() { return messages.findLast(isConversation); }
function latestUserPending() { return lastConversation()?.role === 'user'; }
function render() {
 $('empty').hidden = messages.length > 0; $('messages').hidden = messages.length === 0;
 $('messages').replaceChildren();
 for (const message of messages) {
  const article = document.createElement('article'); article.className = `message ${message.role}`;
  article.dataset.id=message.id;
  const bubble = document.createElement('div'); bubble.className = 'bubble';
  bubble.setAttribute('tabindex','0');bubble.setAttribute('aria-label',message.role==='hint'?'提示，长按打开操作':'消息，长按打开操作');
  attachLongPress(bubble,message);
  if (message.role==='hint') {
   const label=document.createElement('div');label.className='hint-label';label.textContent='提示 · '+(message.hintKind==='OPTIMIZE'?'优化表达':'话题语料');
   const text=document.createElement('div');text.textContent=message.text;
   const note=document.createElement('small');note.textContent='仅供参考，不进入对话上下文 · 长按可操作';bubble.append(label,text,note);
  } else if (message.audio) {
   const audio = document.createElement('audio'); audio.controls = true; audio.preload = 'none'; bubble.append(audio);
   audioStore(message.id).then(blob => { if (blob) { let url = playbackUrls.get(message.id); if (!url) { url = URL.createObjectURL(blob); playbackUrls.set(message.id, url); } audio.src = url; } });
   const transcript = document.createElement('details'); const summary = document.createElement('summary'); summary.textContent = message.text ? '查看转写' : '转写未完成'; const text = document.createElement('div'); text.className = 'transcript'; text.textContent = message.text; transcript.append(summary, text); bubble.append(transcript);
  } else if (message.role === 'assistant' && voiceBlocks) {
   const play = document.createElement('button'); play.className = 'voice'; play.textContent = '▷　▂ ▅ ▃ ▆ ▂ ▄　听回复'; play.onclick = () => playReply(message, play); bubble.append(play);
   const details = document.createElement('details'); const summary = document.createElement('summary'); summary.textContent = '英文原文'; const text = document.createElement('div'); text.className = 'transcript'; text.textContent = message.text; details.append(summary, text); bubble.append(details);
  } else bubble.textContent = message.text;
  article.append(bubble);
  if (message.role === 'user') { const mark = document.createElement('button'); mark.className = 'quiet'; mark.textContent = message.markedStuck ? '✓ 已标记卡壳' : '这里卡了'; mark.onclick = () => { message.markedStuck = !message.markedStuck; save(); render(); }; article.append(mark); }
  $('messages').append(article);
 }
 $('retry').hidden = !latestUserPending();
 $('messages').scrollTop = $('messages').scrollHeight;
}
function coach() { return `You are a friendly English conversation partner for a Chinese learner. Reply in 1-3 brief natural English sentences and ask just one question. Adapt to their level. Do not claim to score pronunciation or detect pauses from text. If the user starts [CHINESE HELP], give a short Chinese explanation and one useful English expression, then invite them back into the same conversation. ${mode === 'review' ? 'Elicit these learning targets naturally: ' + targets.map(x => x.content).join('; ') : ''}`; }
async function generateReply() {
 const history = conversationMessages(messages).slice(-30).map(x => ({ role: x.role, content: x.help ? '[CHINESE HELP] ' + x.text : x.text }));
 const response = await request('chat/completions', { model: 'managed', stream: false, messages: [{ role: 'system', content: coach() }, ...history] });
 const result = await response.json(), text = result.choices?.[0]?.message?.content;
 if (!text) throw Error('AI 没有返回文字，请重试。');
 const message = { id: crypto.randomUUID(), role: 'assistant', text }; messages.push(message); save(); render();
 if ($('autoplay').checked) playReply(message);
}
async function sendText(text) {
 if (!token) { feedback('请先连接服务。'); return; }
 if (busy || !text.trim()) return;
 if (latestUserPending()) { feedback('先重试上一条消息，或点击重说。'); return; }
 messages.push({ id: crypto.randomUUID(), role: 'user', text: text.trim(), help }); save(); render(); $('draft').value = ''; feedback();
 setBusy(true); $('status').textContent = 'AI 正在回复…';
 try { await generateReply(); } catch (error) { feedback(error.message); } finally { setBusy(false); checkStatus(); }
}
async function transcribeAndReply(message) {
 setBusy(true); $('status').textContent = '正在听你的语音…';
 try {
  if (!message.text) {
   const blob = await audioStore(message.id); if (!blob) throw Error('录音不可用，请重说。');
   const form = new FormData(); form.set('file', blob, 'voice.wav'); form.set('language', message.help ? 'zh' : 'en');
   const result = await (await request('audio/transcriptions', form, true)).json(); message.text = result.text; save(); render();
  }
  await generateReply();
 } catch (error) { feedback(error.message); } finally { setBusy(false); checkStatus(); }
}
const ttsCache = new Map(); let playbackGeneration = 0;
function stopPlayback() { playbackGeneration++; if (audioPlayer) { audioPlayer.pause(); audioPlayer = null; } }
async function playReply(message, button) {
 stopPlayback(); const generation = playbackGeneration;
 if (button) button.textContent = '正在准备语音…';
 try {
  const cacheKey=message.id+':'+(message.revision||0);
  let url = ttsCache.get(cacheKey);
  if (!url) { const response = await request('audio/speech', { input: message.text }); url = URL.createObjectURL(await response.blob()); ttsCache.set(cacheKey, url); }
  if (generation !== playbackGeneration) return;
  audioPlayer = new Audio(url); await audioPlayer.play();
 } catch { feedback('语音未能播放，可以查看英文原文。手机浏览器首次可能需要主动点一下语音块。'); }
 finally { if (button) button.textContent = '▷　▂ ▅ ▃ ▆ ▂ ▄　听回复'; }
}
async function beginRecording() {
 if (busy || starting || recording || !token || latestUserPending()) return;
 starting = true; releaseDuringStart = false; cancelled = false; stopPlayback();
 try {
  const stream = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, echoCancellation: true } });
  if (releaseDuringStart) { stream.getTracks().forEach(track => track.stop()); return; }
  const context = new AudioContext(), source = context.createMediaStreamSource(stream), processor = context.createScriptProcessor(4096, 1, 1), silent = context.createGain();
  const chunks = []; silent.gain.value = 0; processor.onaudioprocess = event => chunks.push(new Float32Array(event.inputBuffer.getChannelData(0)));
  source.connect(processor); processor.connect(silent); silent.connect(context.destination);
  recording = { context, stream, processor, source, chunks, start: performance.now(), timer: setTimeout(() => finishRecording(false), 60000) };
  $('hold').classList.add('recording'); $('hold').textContent = '松开发送 · 上滑取消';
 } catch { feedback('请允许麦克风权限，并使用 HTTPS 或本机 localhost 打开。'); }
 finally { starting = false; }
}
function wav(chunks, sampleRate) {
 const length = chunks.reduce((n,x) => n+x.length,0), pcm = new Float32Array(length); let offset=0; for (const chunk of chunks) { pcm.set(chunk,offset); offset+=chunk.length; }
 const count = Math.floor(length * 16000 / sampleRate), buffer = new ArrayBuffer(44 + count * 2), view = new DataView(buffer);
 const text = (position,value) => { for(let i=0;i<value.length;i++) view.setUint8(position+i,value.charCodeAt(i)); };
 text(0,'RIFF'); view.setUint32(4,36+count*2,true); text(8,'WAVE'); text(12,'fmt '); view.setUint32(16,16,true); view.setUint16(20,1,true); view.setUint16(22,1,true); view.setUint32(24,16000,true); view.setUint32(28,32000,true); view.setUint16(32,2,true); view.setUint16(34,16,true); text(36,'data'); view.setUint32(40,count*2,true);
 for(let i=0;i<count;i++){ const sample=Math.max(-1,Math.min(1,pcm[Math.floor(i*sampleRate/16000)]||0)); view.setInt16(44+i*2,sample<0?sample*32768:sample*32767,true); }
 return new Blob([buffer],{type:'audio/wav'});
}
async function finishRecording(cancel) {
 if (starting) { releaseDuringStart = true; return; }
 if (!recording) return;
 const capture=recording; recording=null; clearTimeout(capture.timer); capture.processor.disconnect(); capture.source.disconnect(); capture.stream.getTracks().forEach(x=>x.stop());
 const blob=wav(capture.chunks,capture.context.sampleRate), duration=performance.now()-capture.start; await capture.context.close();
 $('hold').classList.remove('recording','cancel'); $('hold').textContent='按住说话';
 if(cancel){ feedback('已取消，这段录音没有发送。'); return; }
 if(duration<400){ feedback('录音太短，请按住半秒后再松开。'); return; }
 const message={id:crypto.randomUUID(),role:'user',text:'',audio:true,help,duration};
 await audioStore(message.id,blob); messages.push(message); save(); render(); await transcribeAndReply(message);
}
$('send-form').onsubmit = event => { event.preventDefault(); sendText($('draft').value); };
$('begin').onclick = () => sendText("Hi! Let's have a conversation.");
$('help').onclick=()=>{if(busy||recording)return;help=!help;$('help').textContent=help?'中文求助中 · 切回英文':'卡住了？用中文问';};
$('new').onclick=()=>{if(busy||recording)return;stopPlayback();messages=[];save();render();feedback();};
$('undo').onclick=()=>{if(busy||recording)return;stopPlayback();let i=messages.findLastIndex(x=>x.role==='user');if(i>=0){messages=messages.slice(0,i);save();render();feedback('上一轮已回退，可以重新说。');}};
$('retry').onclick=()=>{if(busy)return;const message=lastConversation();if(!message||message.role!=='user')return;if(message.audio)transcribeAndReply(message);else{setBusy(true);generateReply().catch(error=>feedback(error.message)).finally(()=>setBusy(false));}};
$('display').onclick=()=>{voiceBlocks=!voiceBlocks;$('display').textContent=voiceBlocks?'显示英文原文':'显示语音块';render();};
$('stop-speech').onclick=stopPlayback;
let startY=0;
$('hold').onpointerdown=event=>{if(busy)return;startY=event.clientY;cancelled=false;event.currentTarget.setPointerCapture(event.pointerId);beginRecording();};
$('hold').onpointermove=event=>{if(!recording)return;cancelled=event.clientY-startY<-72;$('hold').classList.toggle('cancel',cancelled);$('hold').textContent=cancelled?'松开取消':'松开发送 · 上滑取消';};
$('hold').onpointerup=()=>finishRecording(cancelled);$('hold').onpointercancel=()=>finishRecording(true);
window.addEventListener('pagehide',()=>{finishRecording(true);stopPlayback();});
function renderLibrary(){ $('items').replaceChildren(); if(!library.length){$('items').textContent='聊完收藏，或手动添加一个想练的表达。';return;} library.forEach(item=>{const article=document.createElement('article');article.className='item';const title=document.createElement('h3');title.textContent=item.content;const meaning=document.createElement('p');meaning.textContent=item.meaning;const example=document.createElement('p');example.textContent=item.example;const actions=document.createElement('div');actions.className='row';const practice=document.createElement('button');practice.textContent='练这一项';practice.onclick=()=>{targets=[item];startReview();};const remove=document.createElement('button');remove.className='quiet';remove.textContent='删除';remove.onclick=()=>{library=library.filter(x=>x.id!==item.id);save();renderLibrary();};actions.append(practice,remove);article.append(title,meaning,example,actions);$('items').append(article);}); }
$('library').onclick=()=>{if(recording)return;$('library-panel').hidden=!$('library-panel').hidden;$('conversation').hidden=!$('library-panel').hidden;renderLibrary();};
$('add').onclick=()=>openManualEntry();
function startReview(){if(busy)return;mode='review';messages=[];save();render();$('review').classList.add('selected');$('free').classList.remove('selected');$('library-panel').hidden=true;$('conversation').hidden=false;sendText('Let us practice my learning targets.');}
$('review').onclick=()=>{if(!library.length){feedback('先收藏或添加一项表达。');return;}targets=library.slice(0,5);startReview();};
$('free').onclick=()=>{if(busy)return;mode='free';targets=[];$('free').classList.add('selected');$('review').classList.remove('selected');};
$('summarize').onclick=()=>openManualEntry();
let selectedBlock=null;
function openManualEntry(){if(busy||recording)return;$('entry-form').reset();$('manual-entry').showModal();$('entry-content').focus();}
$('cancel-entry').onclick=()=>$('manual-entry').close();
$('entry-form').onsubmit=event=>{event.preventDefault();const content=$('entry-content').value.trim();if(!content)return;library.push({id:crypto.randomUUID(),kind:$('entry-kind').value,content,meaning:$('entry-meaning').value.trim(),example:$('entry-example').value.trim(),sourceNote:'手动积累'});save();$('manual-entry').close();feedback('已保存到复习库。');renderLibrary();};
function showActions(message){selectedBlock=message;$('action-description').textContent=message.role==='hint'?'提示不进入对话上下文':'仅针对选中的这一块';$('rewrite-message').textContent=message.role==='user'?'优化表达':'重新生成';$('rewrite-message').disabled=busy||!message.text;$('identify-expression').disabled=busy||!message.text;$('copy-message').disabled=!message.text;$('message-actions').showModal();}
function attachLongPress(bubble,message){
 let timer=null,x=0,y=0,triggered=false;const clear=()=>{clearTimeout(timer);timer=null;};
 bubble.onpointerdown=event=>{if(event.button!==0)return;x=event.clientX;y=event.clientY;triggered=false;timer=setTimeout(()=>{triggered=true;showActions(message);},500);};
 bubble.onpointermove=event=>{if(Math.abs(event.clientX-x)>12||Math.abs(event.clientY-y)>12)clear();};
 bubble.onpointerup=clear;bubble.onpointercancel=clear;bubble.onpointerleave=clear;
 bubble.oncontextmenu=event=>{event.preventDefault();clear();if(!triggered)showActions(message);};
 bubble.addEventListener('click',event=>{if(triggered){event.stopImmediatePropagation();event.preventDefault();triggered=false;}},true);
 bubble.onkeydown=event=>{if(event.key==='ContextMenu'||event.shiftKey&&event.key==='F10'){event.preventDefault();showActions(message);}};
}
$('close-actions').onclick=()=>$('message-actions').close();
$('copy-message').onclick=async()=>{const text=selectedBlock?.text||'';$('message-actions').close();try{await navigator.clipboard.writeText(text);feedback('已复制这条消息。');}catch{feedback('浏览器不允许复制。');}};
async function completion(prompts,purpose='learning'){
 const response=await request('chat/completions',{purpose,messages:prompts});const result=await response.json();const text=result.choices?.[0]?.message?.content;if(!text?.trim())throw Error('生成结果为空，原消息保留。');return text;
}
function renderCandidates(){
 $('candidate-list').replaceChildren();
 if(!candidates.length){$('candidate-list').textContent='这条消息暂无候选，可以手动积累。';}
 candidates.forEach((item,index)=>{const box=document.createElement('div');box.className='candidate';const label=document.createElement('label');const check=document.createElement('input');check.type='checkbox';check.checked=true;check.dataset.index=index;label.append(check,document.createTextNode(' 保存此项'));box.append(label);
 for(const [key,title] of [['content','英文内容'],['meaning','中文释义'],['example','例句']]){const field=document.createElement('input');field.value=item[key]||'';field.setAttribute('aria-label',title);field.placeholder=title;field.oninput=()=>{item[key]=field.value};box.append(field);}
 const source=document.createElement('small');source.textContent='单条消息识别 · 请确认';box.append(source);$('candidate-list').append(box);});$('candidates').showModal();
}
$('identify-expression').onclick=async()=>{const selected=selectedBlock;$('message-actions').close();if(busy||!selected?.text)return;setBusy(true);try{const text=await completion(extractionPrompts(selected),'summary');const result=JSON.parse(text.trim().replace(/^```json\s*/, '').replace(/```$/, ''));candidates=(result.items||[]).filter(x=>x.content?.trim()).slice(0,8);renderCandidates();}catch(error){feedback(error.message||'识别失败，请重试。');}finally{setBusy(false);}};
$('rewrite-message').onclick=async()=>{const selected=selectedBlock;$('message-actions').close();if(busy||!selected?.text)return;setBusy(true);stopPlayback();try{
 if(selected.role==='user'){
  const context=conversationMessages(messages.slice(0,messages.findIndex(x=>x.id===selected.id))).slice(-8);
  const text=await completion(optimizationPrompts(messages,selected));messages.push({id:crypto.randomUUID(),role:'hint',hintKind:'OPTIMIZE',text,sourceMessageId:selected.id,hintContextIds:context.map(x=>x.id)});
 }else if(selected.role==='hint'){
  const context=messages.filter(x=>selected.hintContextIds?.includes(x.id)&&isConversation(x));
  const original=messages.find(x=>x.id===selected.sourceMessageId);
  if(selected.hintKind==='OPTIMIZE'&&!original)throw Error('原话已不存在。');
  const prompts=selected.hintKind==='OPTIMIZE'?optimizationPrompts([...context,original],original):topicPrompts(context);
  messages=replaceMessage(messages,selected.id,await completion(prompts));
 }else{
  const before=conversationMessages(messages.slice(0,messages.findIndex(x=>x.id===selected.id)));
  const prompts=[{role:'system',content:coach()},...before.map(x=>({role:x.role,content:x.help?'[CHINESE HELP] '+x.text:x.text}))];
  messages=replaceMessage(messages,selected.id,await completion(prompts,'chat'));
 }
 save();render();
 }catch(error){feedback(error.message);}finally{setBusy(false);}};
$('topic-expressions').onclick=async()=>{if(busy||recording)return;const context=conversationMessages(messages).slice(-12);if(!context.length)return feedback('先开启一个话题。');setBusy(true);try{const text=await completion(topicPrompts(context));messages.push({id:crypto.randomUUID(),role:'hint',hintKind:'TOPIC',text,hintContextIds:context.map(x=>x.id)});save();render();}catch(error){feedback(error.message);}finally{setBusy(false);}};
$('save-candidates').onclick=()=>{let count=0;$('candidate-list').querySelectorAll('input:checked').forEach(check=>{const item=candidates[Number(check.dataset.index)];if(!item.content?.trim())return;if(!library.some(x=>x.content.toLowerCase()===item.content.toLowerCase())){library.push({...item,id:crypto.randomUUID()});count++;}});save();$('candidates').close();feedback(`已收藏 ${count} 项表达。`);};
$('close-candidates').onclick=()=>$('candidates').close();
$('connect-button').onclick=()=>{try{const value=new URL($('connection').value);const candidate=new URLSearchParams(value.hash.slice(1)).get('token');if(!candidate?.match(/^[a-f0-9]{64}$/))throw Error();token=candidate;sessionStorage.setItem('speakpen-token',token);$('connection').value='';checkStatus();}catch{feedback('请粘贴电脑生成的完整连接入口。');}};
$('pair').onclick=()=>{if(!token)return feedback('先连接服务。');location.href=`speakpen://connect?url=${encodeURIComponent(location.origin+'/v1')}&token=${encodeURIComponent(token)}`;};
$('install').onclick=async()=>{if(!token)return feedback('先连接服务。');try{const response=await fetch('/app.apk',{headers:{authorization:`Bearer ${token}`}});if(!response.ok)throw Error();const url=URL.createObjectURL(await response.blob());const link=document.createElement('a');link.href=url;link.download='SpeakPen-0.4.0.apk';link.click();setTimeout(()=>URL.revokeObjectURL(url),30000);}catch{feedback('安装包尚未就绪，请稍后重试。');}};
$('copy-link').onclick=async()=>{
 if(!token)return feedback('先连接服务。');
 const value=location.origin+'/#token='+token;
 try{await navigator.clipboard.writeText(value);feedback('已复制完整入口。到 App → 偏好 → 服务连接 → 重新连接中粘贴。');}
 catch{const input=document.createElement('textarea');input.value=value;document.body.append(input);input.select();const copied=document.execCommand('copy');input.remove();feedback(copied?'已复制完整入口，可到 App 中粘贴。':'浏览器不允许复制，请从电脑的连接入口页面复制完整链接。');}
};
render();checkStatus();


