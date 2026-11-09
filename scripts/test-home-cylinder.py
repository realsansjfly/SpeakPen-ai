from pathlib import Path
import sys,json
sys.path.insert(0,str(Path.cwd()/'.tooling/preview-python'))
from playwright.sync_api import sync_playwright,expect
root=Path.cwd();out=root/'deliverables/home-webview-fix-2026-11-09';out.mkdir(parents=True,exist_ok=True)
origin='https://appassets.androidplatform.net'
corpus=json.loads((root/'app/src/main/assets/topics/catalog.json').read_text(encoding='utf-8'))['topics']
first=[{'id':t['id'],'text':t['text']} for t in corpus[240:249]+corpus[:9]]
second=[{'id':t['id'],'text':t['text']} for t in corpus[260:269]+corpus[20:29]]
initial={'active':True,'enabled':True,'motion':True,'fontScale':1,'demoAvailable':False,'batch':{'revision':1,'topics':first}}
checks=[]
def record(name):checks.append(name);print('PASS',name,flush=True)
with sync_playwright() as p:
    b=p.chromium.launch(headless=True,executable_path='C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
    errors=[]
    def setup(width=390,height=570,reduced=False,legacy=False):
        page=b.new_page(viewport={'width':width,'height':height},reduced_motion='reduce' if reduced else 'no-preference')
        page.on('pageerror',lambda e:errors.append(str(e)))
        def font(route):
            name='nunito_bold.ttf' if 'bold' in route.request.url else 'nunito_regular.ttf'
            route.fulfill(path=str(root/'app/src/main/res/font'/name),content_type='font/ttf',headers={'Access-Control-Allow-Origin':'*'})
        page.route(origin+'/fonts/*',font)
        page.route(origin+'/home/topic-cylinder.html',lambda route:route.fulfill(path=str(root/'app/src/main/assets/home/topic-cylinder.html'),content_type='text/html'))
        page.add_init_script('window.nativeCalls=[];window.SpeakPenNative={confirm:id=>window.nativeCalls.push({type:"confirm",id}),refresh:()=>window.nativeCalls.push({type:"refresh"}),selection:value=>window.nativeCalls.push({type:"selection",value}),failed:reason=>window.nativeCalls.push({type:"failed",reason})};')
        if legacy:
            page.add_init_script('window.ResizeObserver=undefined;Element.prototype.replaceChildren=undefined;const original=window.matchMedia;window.matchMedia=q=>{const m=original(q);m.addEventListener=undefined;return m;};')
        page.goto(origin+'/home/topic-cylinder.html');page.wait_for_load_state('networkidle')
        # Android supplies topics after page completion; do not pre-inject the batch.
        assert page.evaluate('SpeakPenHome.inspect().topicIds.length')==0
        page.evaluate('(o)=>SpeakPenHome.update(o)',dict(initial,active=False))
        assert page.evaluate('SpeakPenHome.inspect().topicIds.length')==18
        assert page.evaluate('document.documentElement.clientWidth>0&&document.documentElement.clientHeight>0')
        page.evaluate('(o)=>SpeakPenHome.update(o)',initial)
        return page
    page=setup();record('offline_https_document_then_native_payload_and_viewport_probe')
    st=lambda:page.evaluate('SpeakPenHome.inspect()')
    a=st()['angle'];page.wait_for_timeout(400);assert st()['angle']!=a;record('slow_autorotation')
    # Click a fully visible front button, using a point that is actually hit-testable.
    pos=page.evaluate('''() => {for(const e of document.querySelectorAll('.keyword.front')){const r=e.getBoundingClientRect(),x=r.x+r.width/2,y=r.y+r.height/2;if(x>30&&x<innerWidth-30&&y>30&&y<innerHeight-100&&e.contains(document.elementFromPoint(x,y)))return {x,y,id:e.dataset.id};}throw Error('No visible front topic');}''')
    page.mouse.click(pos['x'],pos['y']);expect(page.locator('#focus')).to_be_visible();assert st()['selected']==pos['id']
    a=st()['angle'];page.wait_for_timeout(400);assert st()['angle']==a;assert page.evaluate('window.nativeCalls.filter(c=>c.type==="confirm").length')==0;record('topic_click_freezes_and_needs_confirmation')
    assert page.locator('#selected-topic').inner_text()==next(t['text'] for t in first if t['id']==pos['id']);assert page.locator('#browse').evaluate('e=>e.inert');record('full_text_and_modal_background')
    page.locator('#cancel').click();expect(page.locator('#focus')).to_be_hidden();a=st()['angle'];page.wait_for_timeout(350);assert st()['angle']!=a;record('cancel_resumes_without_request')
    page.locator('#random').click();chosen=st()['selected'];assert chosen in [t['id'] for t in first];page.locator('#confirm').click()
    assert page.evaluate('window.nativeCalls.filter(c=>c.type==="confirm").length')==1;assert st()['confirming'];assert page.locator('#confirm').is_disabled();record('random_uses_same_confirmation_and_one_bridge_call')
    page.evaluate('SpeakPenHome.confirmRejected()');page.keyboard.press('Escape');expect(page.locator('#focus')).to_be_hidden();record('rejection_and_back_cancel')
    # A drag must never open a topic; release retains velocity then damps back to auto speed.
    page.mouse.move(120,280);page.mouse.down();page.mouse.move(290,280,steps=8);page.mouse.up();assert st()['selected'] is None;v=abs(st()['velocity']-6);assert v>10;page.wait_for_timeout(2200);assert abs(st()['velocity']-6)<v*.2;record('drag_inertia_and_no_accidental_selection')
    # Analytic integrator must be stable at different display refresh rates and reverse flicks.
    physics=page.evaluate('''() => {let r=[];for(const fps of [30,60,120]){let p={angle:20,velocity:-180};for(let i=0;i<fps*3;i++)p=SpeakPenHome.kinetic(p.angle,p.velocity,1/fps);r.push(p);}return r;}''')
    assert max(p['angle'] for p in physics)-min(p['angle'] for p in physics)<1e-6;record('frame_rate_independent_reverse_inertia')
    page.locator('#refresh').click();assert st()['refreshing'];page.evaluate('(batch)=>SpeakPenHome.update({active:true,enabled:true,motion:true,batch})',{'revision':2,'topics':second});page.wait_for_timeout(1200);assert not st()['refreshing'];assert set(st()['topicIds']).isdisjoint(t['id'] for t in first);assert st()['velocity']==6;record('refresh_fast_spin_swaps_batch_and_restores_speed')
    page.evaluate('SpeakPenHome.update({active:false,enabled:true,motion:true})');a=st()['angle'];page.wait_for_timeout(250);assert st()['angle']==a;record('background_stops_motion')
    page.evaluate('SpeakPenHome.update({active:true,enabled:true,motion:true})');page.locator('#refresh').click();page.evaluate('SpeakPenHome.refreshFailed()');assert not st()['refreshing'];expect(page.locator('#error')).to_contain_text('重试');record('refresh_failure_retains_retry')
    page.close()
    for w,h in [(320,440),(390,570),(430,650),(768,700),(390,260)]:
        pg=setup(w,h);assert pg.evaluate('document.documentElement.scrollWidth<=innerWidth');pg.screenshot(path=str(out/f'home-{w}x{h}.png'))
        pg.locator('#random').click();expect(pg.locator('#selected-topic')).to_be_visible();assert pg.locator('#cancel').bounding_box()['y']>=0;pg.screenshot(path=str(out/f'selection-{w}x{h}.png'));pg.close()
    record('responsive_320_390_430_768_and_short_window')
    pg=setup(reduced=True);assert pg.evaluate('SpeakPenHome.inspect().staticMode');expect(pg.locator('#list')).to_be_visible();pg.locator('#list-options button').first.click();expect(pg.locator('#focus')).to_be_visible();pg.keyboard.press('Escape');pg.close();record('reduced_motion_scrollable_list_and_selection')
    pg=setup();pg.evaluate('SpeakPenHome.update({active:true,enabled:true,motion:true,fontScale:2})');assert pg.evaluate('SpeakPenHome.inspect().staticMode');pg.locator('#list-options button').first.click();expect(pg.locator('#focus')).to_be_visible();pg.screenshot(path=str(out/'large-type-390.png'));pg.close();record('large_font_list_fallback')
    pg=setup(320,320)
    long_topic=('Tell me about a small place that surprised you, the people you met there, and what made you want to visit it again. ' + 'What stood out?')[:130]
    pg.evaluate('(topic)=>SpeakPenHome.update({active:true,enabled:true,motion:false,batch:{revision:3,topics:[{id:"long",text:topic}]}})',long_topic)
    pg.locator('#random').click();assert pg.locator('#selected-topic').inner_text()==long_topic
    pg.locator('#confirm').scroll_into_view_if_needed();expect(pg.locator('#confirm')).to_be_visible()
    pg.keyboard.press('Tab');assert pg.locator('#cancel').evaluate('e=>e===document.activeElement')
    pg.keyboard.press('Escape');expect(pg.locator('#focus')).to_be_hidden()
    assert pg.evaluate('document.documentElement.scrollWidth<=innerWidth');pg.close();record('130_character_topic_scrolls_fully_and_keyboard_focus_is_trapped')
    pg=setup(legacy=True);assert pg.evaluate('SpeakPenHome.inspect().topicIds.length')==18
    pg.locator('#random').click();expect(pg.locator('#focus')).to_be_visible();pg.locator('#cancel').click()
    pg.set_viewport_size({'width':430,'height':650});pg.wait_for_timeout(100)
    assert pg.locator('#stage').bounding_box()['width']==430
    pg.close();record('older_webview_without_resizeobserver_replacechildren_or_media_change_listener')
    pg=setup();pg.evaluate("window.dispatchEvent(new ErrorEvent('error',{message:'simulated render failure'}))")
    assert pg.evaluate('window.nativeCalls.some(c=>c.type==="failed")');pg.close();record('runtime_error_reports_to_native_recovery')
    assert not errors,errors
    b.close()
(out/'browser-verification.json').write_text(json.dumps({'checks':checks,'pageErrors':errors,'native_device_tested':False},ensure_ascii=False,indent=2),encoding='utf-8')
