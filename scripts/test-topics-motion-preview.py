"""Preview checks only: no claim of Android rendering or frame timing."""
from pathlib import Path
import sys, json
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'.tooling/preview-python'))
from playwright.sync_api import sync_playwright
from PIL import Image, ImageChops, ImageStat

root=Path(__file__).resolve().parents[1]
out=root/'deliverables/topics-motion-2026-10-31';out.mkdir(parents=True,exist_ok=True)
errors=[]
with sync_playwright() as p:
    browser=p.chromium.launch(headless=True,executable_path='C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
    page=browser.new_page(viewport={'width':1000,'height':960})
    page.on('pageerror',lambda e:errors.append(str(e)))
    page.goto((root/'design/topics-motion-2026-10-31/index.html').as_uri())
    page.evaluate('setPlaying(false);drawAt(0)')
    assert page.locator('.chip').count()==36
    seen=set();previous=set(page.evaluate('batchIds'))
    for i in range(50):
        page.locator('#refresh').click()
        current=set(page.evaluate('batchIds'));assert len(current)==18 and not current.intersection(previous)
        seen.update(current);previous=current
    assert len(seen)>400
    draft=page.locator('#draft').inner_text()
    page.locator('#reenter').click()
    assert not previous.intersection(set(page.evaluate('batchIds')))
    assert page.locator('#draft').inner_text()==draft
    assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
    page.screenshot(path=str(out/'preview-desktop.png'),full_page=True)
    canvas=page.locator('canvas').first
    canvas.screenshot(path=str(out/'background-0s.png'))
    page.evaluate('drawAt(4)');canvas.screenshot(path=str(out/'background-4s.png'))
    page.evaluate('setPlaying(true)');start=page.evaluate('seconds');page.wait_for_timeout(350)
    assert page.evaluate('seconds')>start
    page.evaluate('setPlaying(false)');start=page.evaluate('seconds');page.wait_for_timeout(150)
    assert page.evaluate('seconds')==start
    for width in [320,390]:
        page.set_viewport_size({'width':width,'height':844})
        assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
        page.screenshot(path=str(out/f'preview-{width}.png'),full_page=True)
    reduced=browser.new_page(viewport={'width':390,'height':844},reduced_motion='reduce')
    reduced.goto((root/'design/topics-motion-2026-10-31/index.html').as_uri())
    assert not reduced.locator('#motion').is_checked()
    assert not errors,errors
    browser.close()
diff=ImageChops.difference(Image.open(out/'background-0s.png').convert('RGB'),Image.open(out/'background-4s.png').convert('RGB'))
mean=sum(ImageStat.Stat(diff).mean)/3
assert mean>.3
report={'preview':'pass','distinctTopicsAcross50Batches':len(seen),'adjacentBatchOverlap':0,
        'viewports':[320,390,1000],'javascriptErrors':errors,'background4sMeanChannelChange':round(mean,3),
        'reducedMotion':'pass','androidRendering':'not tested'}
(out/'preview-verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report))
