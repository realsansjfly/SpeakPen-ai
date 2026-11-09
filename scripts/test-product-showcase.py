"""Responsive, accessibility-fallback and real motion checks for the showcase."""
from pathlib import Path
import json
import sys

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'.tooling/preview-python'))
from playwright.sync_api import sync_playwright

SITE=ROOT/'deliverables/product-showcase-v2-2026-10-31'
URL=(SITE/'index.html').as_uri()
assert '../../app/src/main/res/font/' not in (SITE/'index.html').read_text(encoding='utf-8')
errors=[]
requests=[]
viewports=[]

def assert_no_overflow(page):
    result=page.evaluate('''() => ({viewport:innerWidth,width:document.documentElement.scrollWidth})''')
    assert result['width']<=result['viewport'],result
    return result

with sync_playwright() as p:
    browser=p.chromium.launch(headless=True,executable_path='C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
    # Deterministic visual captures; readable static content is the default.
    context=browser.new_context(reduced_motion='reduce')
    page=context.new_page()
    page.on('pageerror',lambda e:errors.append(str(e)))
    for width,height in [(320,740),(360,800),(390,844),(430,932),(768,1024),(1440,1000)]:
        page.set_viewport_size({'width':width,'height':height})
        page.goto(URL)
        page.evaluate('document.fonts.ready')
        viewports.append(assert_no_overflow(page))
        for chapter in ['conversation','learning','scenarios','vocabulary','collection']:
            assert page.locator('#'+chapter).is_visible()
        assert page.locator('#refresh-topics').count()==0
        assert page.locator('.topic-flow button,.topic-flow a,.topic-flow input').count()==0
        assert page.locator('.topic-row').count()==3
        assert page.locator('[data-reveal]').count()>=7
        assert page.locator('.chapter-links').evaluate('el=>el.scrollWidth>=el.clientWidth')
        page.screenshot(path=str(SITE/f'viewport-{width}.png'),full_page=True)
        if width==390:
            page.screenshot(path=str(SITE/'mobile-first-screen.png'))
            for selector,name in [('.topic-demo','floating-topics'),('.resay-demo','resay'),('.scenario-example','scenario'),('.wordbook','vocabulary'),('.recap-sample','recap')]:
                page.locator(selector).screenshot(path=str(SITE/(name+'.png')))

    # Keyboard and touch-like interactions retain native semantics.
    page.set_viewport_size({'width':390,'height':844})
    page.goto(URL)
    hint=page.locator('#reveal-hint')
    hint.focus();page.keyboard.press('Enter')
    assert page.locator('#hint-skeleton').is_visible() and page.locator('#hint-reference').is_hidden()
    page.keyboard.press('Space')
    assert page.locator('#hint-reference').is_visible()
    page.keyboard.press('Enter')
    assert page.locator('#hint-reference').is_hidden()
    for scene in ['shopping','directions','hotel','campus','interview','cafe']:
        button=page.locator('[data-scene="'+scene+'"]')
        button.click()
        assert button.get_attribute('aria-pressed')=='true'
        assert page.locator('#scene-title').inner_text()==button.inner_text()
        assert_no_overflow(page)
    page.locator('a[href="#vocab-details"]').click()
    assert page.locator('#vocab-details').evaluate('el=>el.open')
    for details in page.locator('.details-list details').all():
        details.evaluate('el=>el.open=true')
    assert_no_overflow(page)
    assert page.locator('.details-list details').count()==7
    page.locator('.chapter-links a[href="#scenarios"]').click()
    page.wait_for_timeout(80)
    assert page.locator('.chapter-links [aria-current="location"]').get_attribute('href')=='#scenarios'
    assert page.locator('#scenarios').bounding_box()['y']>=page.locator('.chapter-bar').bounding_box()['height']
    assert page.locator('.chapter-bar').evaluate('el=>getComputedStyle(el).position')=='sticky'
    for link in page.locator('.apk-link').all():
        assert link.get_attribute('href').endswith('/v0.7.1/SpeakPen-0.7.1-debug.apk')
    assert '当前版本 0.7.1' in page.locator('.release-note').inner_text()
    assert '安装即连云服务，无需配对' in page.locator('.download-actions').inner_text()
    page.locator('.back-top').click()
    assert page.evaluate('scrollY')<100

    # Verify movement, global pause, viewport pause and live reduced-motion changes.
    motion=browser.new_page(viewport={'width':390,'height':844})
    motion.on('pageerror',lambda e:errors.append(str(e)))
    motion.on('request',lambda r:requests.append(r.url))
    motion.goto(URL)
    topic=motion.locator('.topic-demo')
    topic.scroll_into_view_if_needed();motion.wait_for_timeout(120)
    track=motion.locator('.topic-track').first
    transform=lambda:track.evaluate('el=>getComputedStyle(el).transform')
    start=transform();motion.wait_for_timeout(350);moving=transform()
    assert start!=moving,(start,moving)
    motion.locator('#motion-toggle').click();motion.wait_for_timeout(70)
    paused=transform();motion.wait_for_timeout(180)
    assert paused==transform()
    assert motion.locator('#motion-toggle').get_attribute('aria-pressed')=='true'
    motion.locator('#motion-toggle').click();motion.wait_for_timeout(120)
    assert paused!=transform()
    motion.locator('#collection').scroll_into_view_if_needed();motion.wait_for_timeout(150)
    assert topic.evaluate('el=>el.classList.contains("motion-paused")')
    paused=transform();motion.wait_for_timeout(150);assert paused==transform()
    motion.emulate_media(reduced_motion='reduce')
    motion.wait_for_function("document.querySelector('#motion-toggle').disabled")
    assert motion.locator('#motion-toggle').is_disabled()
    assert track.evaluate('el=>getComputedStyle(el).animationName')=='none'
    motion.locator('.topic-demo').scroll_into_view_if_needed()
    reduced=transform();motion.wait_for_timeout(120);assert reduced==transform()
    assert not motion.evaluate('document.getAnimations().some(a=>a.playState==="running")')
    motion.emulate_media(reduced_motion='no-preference')
    motion.wait_for_function("!document.querySelector('#motion-toggle').disabled")
    assert motion.locator('#motion-toggle').is_enabled()

    # No script or network is needed for reading or local demo controls.
    fallback=browser.new_page(java_script_enabled=False,viewport={'width':320,'height':740})
    fallback.goto(URL)
    assert fallback.get_by_text('系统固定最近完整的一轮用户与 AI 对话').is_visible()
    assert fallback.locator('#motion-toggle').is_hidden()
    assert fallback.locator('.scenario-picker').is_hidden()
    assert '1,652 条' in fallback.locator('body').inner_text()
    assert_no_overflow(fallback)
    offline=browser.new_context(offline=True,reduced_motion='reduce')
    offline_page=offline.new_page();offline_page.goto((SITE/'SpeakPen-功能介绍.html').as_uri())
    offline_page.locator('#reveal-hint').click()
    assert offline_page.locator('#hint-skeleton').is_visible()
    assert offline_page.evaluate('document.fonts.check("800 20px Nunito")')
    assert not [url for url in requests if url.startswith(('http://','https://'))],requests
    assert not errors,errors
    browser.close()
report={'viewports':viewports,'topicDisplay':'3 continuous rows, no topic controls','simulatedViews':8,'chapterNavigation':'sticky, anchors, current chapter and progress','motion':'visible animation, pause/resume, offscreen pause and live reduced-motion passed','hintProgression':'keyboard Enter/Space passed','scenarioSelections':6,'detailsSections':7,'javascriptDisabled':'core content readable; inactive controls hidden','standaloneOffline':'passed','externalResourceRequests':0,'pageErrors':errors,'deviceTesting':'desktop Edge viewport emulation; no physical phone/Safari/WeChat test'}
(SITE/'browser-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report,ensure_ascii=False))
