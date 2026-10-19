from pathlib import Path
import sys, json, os
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'.tooling/preview-python'))
from playwright.sync_api import sync_playwright
workspace=Path(__file__).resolve().parents[1]
connection=json.loads((workspace/'.local/connection.json').read_text(encoding='utf-8-sig'))
with sync_playwright() as p:
    candidates=[Path('C:/Program Files/Google/Chrome/Application/chrome.exe'),Path('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')]
    binary=next(str(x) for x in candidates if x.exists())
    browser=p.chromium.launch(headless=True,executable_path=binary,args=['--use-fake-device-for-media-stream','--use-fake-ui-for-media-stream','--use-file-for-fake-audio-capture='+str(workspace/'.local/english-test.wav')])
    context=browser.new_context(permissions=['microphone'])
    page=context.new_page();page.goto('http://localhost:8787/')
    result=page.evaluate('''async token => {
        const mime='audio/mp4;codecs=mp4a.40.2';
        if(!MediaRecorder.isTypeSupported(mime))return {supported:false};
        const stream=await navigator.mediaDevices.getUserMedia({audio:{channelCount:1,sampleRate:16000}});
        const recorder=new MediaRecorder(stream,{mimeType:mime,audioBitsPerSecond:64000});
        const chunks=[];recorder.ondataavailable=event=>{if(event.data.size)chunks.push(event.data)};
        const done=new Promise(resolve=>recorder.onstop=resolve);
        recorder.start();await new Promise(resolve=>setTimeout(resolve,5200));recorder.stop();await done;stream.getTracks().forEach(track=>track.stop());
        const blob=new Blob(chunks,{type:'audio/mp4'});const body=new FormData();body.set('file',blob,'practice.m4a');body.set('language','en');
        const start=performance.now();const response=await fetch('/v1/audio/transcriptions',{method:'POST',headers:{authorization:'Bearer '+token},body});
        return {supported:true,status:response.status,bytes:blob.size,elapsedMs:Math.round(performance.now()-start),text:(await response.json()).text};
    }''',connection['accessToken'])
    assert result.get('supported') and result.get('status')==200 and len(result.get('text',''))>10,result
    print('REAL_M4A_ASR_PASS '+json.dumps(result,ensure_ascii=False))
    (workspace/'.local/m4a-test-results.json').write_text(json.dumps(result,ensure_ascii=False),encoding='utf-8')
    browser.close()
