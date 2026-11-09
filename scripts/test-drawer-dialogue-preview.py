"""Standalone layout/interaction checks; browser speech is stubbed, no paid requests."""
import json
from pathlib import Path
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[1]
out = root / 'deliverables/drawer-dialogue-2026-11-09/preview-check'
out.mkdir(parents=True, exist_ok=True)
results = []
with sync_playwright() as p:
    browser = p.chromium.launch(channel='msedge', headless=True)
    for width in (360, 390, 430):
        page = browser.new_page(viewport={'width': width, 'height': 844}, is_mobile=True, has_touch=True)
        errors = []
        page.on('pageerror', lambda e: errors.append(str(e)))
        page.on('console', lambda m: errors.append(m.text) if m.type == 'error' else None)
        page.goto((root / 'design/drawer-dialogue-2026-11-09/index.html').as_uri())
        page.evaluate('''() => { window.spoken=[]; speechSynthesis.speak=u=>{spoken.push(u.text)};
            speechSynthesis.cancel=()=>{}; }''')
        page.get_by_role('button', name='打开菜单').click()
        page.locator('#scrim').click(position={'x': width - 10, 'y': 400})
        assert page.locator('#drawer').is_hidden()
        page.get_by_role('button', name='打开菜单').click()
        page.locator('#drawer').evaluate('''el=>{
            el.dispatchEvent(new TouchEvent('touchstart',{touches:[new Touch({identifier:1,target:el,clientX:200,clientY:400})]}));
            el.dispatchEvent(new TouchEvent('touchend',{changedTouches:[new Touch({identifier:1,target:el,clientX:50,clientY:400})]}));
        }''')
        assert page.locator('#drawer').is_hidden()
        page.get_by_role('button', name='场景练习', exact=True).click()
        page.locator('.scene').nth(3).click()
        page.screenshot(path=str(out / f'{width}-brief.png'))
        page.get_by_role('button', name='查看完整示范').click()
        expected = json.loads((root / 'app/src/main/assets/scenarios/material.json').read_text(encoding='utf-8'))['scenes']['hotel']['dialogue']
        assert page.locator('.line').count() == len(expected)
        assert expected[-1].split(': ', 1)[-1] in page.locator('#content').inner_text()
        assert page.get_by_text('跟读', exact=True).count() == 0
        page.get_by_role('button', name='播放完整对话').click()
        assert page.evaluate('spoken.length') == 1
        page.get_by_role('button', name='停止播放').click()
        page.get_by_role('button', name='播放第 2 句', exact=True).click()
        assert page.evaluate('spoken[1]') == expected[1].split(': ', 1)[-1]
        for scale in (1, 1.5):
            page.evaluate('(scale)=>document.documentElement.style.fontSize=(16*scale)+"px"', scale)
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), (width, scale)
            assert page.locator('#content').evaluate('''el=>[...el.querySelectorAll('*')].every(n=>{
                const r=n.getBoundingClientRect();return !r.width || (r.left>=-1 && r.right<=innerWidth+1)})''')
            page.locator('#content').evaluate('el=>el.scrollTop=el.scrollHeight')
            bounds = page.get_by_role('button', name='开始对话', exact=True).bounding_box()
            assert bounds and bounds['y'] >= 0 and bounds['y'] + bounds['height'] <= 844
            page.screenshot(path=str(out / f'{width}-dialogue-{scale}.png'))
        page.get_by_role('button', name='返回', exact=True).click()
        assert page.locator('#title').inner_text() == '任务简报'
        assert not errors, errors
        results.append({'width': width, 'dialogueLines': len(expected), 'fontScales': [1, 1.5], 'errors': errors})
        page.close()
    browser.close()
(out / 'results.json').write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(results, ensure_ascii=False))
