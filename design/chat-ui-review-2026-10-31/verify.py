"""Verify the isolated proposal only; never accesses the application or its service."""
from pathlib import Path
import json
import sys

folder = Path(__file__).resolve().parent
root = folder.parents[1]
sys.path.insert(0, str(root / '.tooling/preview-python'))
from playwright.sync_api import sync_playwright

errors = []
with sync_playwright() as p:
    binaries = [Path('C:/Program Files/Google/Chrome/Application/chrome.exe'), Path('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')]
    binary = next((str(x) for x in binaries if x.exists()), None)
    browser = p.chromium.launch(headless=True, executable_path=binary)
    page = browser.new_page(viewport={'width':1100,'height':930}, device_scale_factor=1)
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.goto((folder / 'index.html').as_uri())
    page.wait_for_load_state('networkidle')
    page.evaluate("paused=true;updateMotion()")
    page.locator('.device').screenshot(path=str(folder/'idle.png'))
    page.locator('[data-view=drawer]').click()
    page.wait_for_timeout(300)
    page.locator('.device').screenshot(path=str(folder/'drawer.png'))
    ratio = page.locator('#drawer').bounding_box()['width'] / page.locator('.device').bounding_box()['width']
    assert abs(ratio-.75)<.01, ratio
    page.locator('#settings-link').focus()
    page.keyboard.press('Tab')
    assert page.locator('#library-link').evaluate('(el)=>el===document.activeElement')
    page.keyboard.press('Escape')
    assert page.locator('#drawer').evaluate('(el)=>el.inert')
    page.locator('[data-view=settings]').click()
    page.locator('.device').screenshot(path=str(folder/'settings.png'))
    page.select_option('#difficulty','deep')
    page.locator('[data-scope=defaults]').click()
    assert page.locator('#difficulty').input_value()=='simple'
    page.select_option('#difficulty','daily')
    page.locator('[data-scope=current]').click()
    assert page.locator('#difficulty').input_value()=='deep'
    page.locator('#simulate-failure').check()
    page.locator('#settings-back').click()
    page.wait_for_timeout(600)
    assert page.locator('#save-error').is_visible()
    assert page.locator('#difficulty').input_value()=='deep'
    page.locator('.device').screenshot(path=str(folder/'save-error.png'))
    page.locator('#simulate-failure').uncheck()
    page.locator('#retry-save').click()
    page.wait_for_timeout(600)
    assert page.locator('#settings-page').is_hidden()
    page.locator('[data-view=settings]').click()
    assert page.locator('#difficulty').input_value()=='deep'
    page.locator('[data-scope=defaults]').click()
    assert page.locator('#difficulty').input_value()=='daily'
    page.locator('[data-view=chat]').click()
    page.locator('#toast').evaluate('(e)=>e.hidden=true')
    page.locator('.device').screenshot(path=str(folder/'chat.png'))
    page.locator('[data-view=scene]').click()
    page.locator('#scene-bar summary').click()
    assert page.locator('#show-evidence').is_visible()
    page.locator('#scene-bar summary').click()
    page.locator('.device').screenshot(path=str(folder/'scene.png'))
    page.locator('[data-view=idle]').click()
    page.locator('.topic').first.click()
    assert page.locator('#text-row').is_visible()
    assert len(page.locator('#draft').input_value())>0
    page.locator('button#send').click()
    assert page.locator('#messages .user').count()==1
    assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
    for width in [360,390]:
        page.set_viewport_size({'width':width,'height':844})
        page.locator('[data-view=idle]').click()
        assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
        page.locator('[data-view=settings]').click()
        assert page.locator('#difficulty').bounding_box()['x']>=0
        assert page.evaluate('document.querySelector(".settings-scroll").scrollWidth<=document.querySelector(".settings-scroll").clientWidth')
    page.emulate_media(reduced_motion='reduce')
    page.locator('[data-view=idle]').click()
    assert page.locator('.lane').first.evaluate('(e)=>getComputedStyle(e).animationName')=='none'
    page.locator('.device').screenshot(path=str(folder/'reduced-motion-390.png'))
    assert not errors, errors
    page.set_viewport_size({'width':1290,'height':955})
    image_files=['idle.png','drawer.png','settings.png']
    labels=['空白首页 · 单行顶栏','左侧抽屉 · 75% 屏宽','设置 · 返回时自动保存']
    board='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><style>
    @font-face{font-family:Nunito;src:url('nunito_regular.ttf')}
    *{box-sizing:border-box}body{margin:0;padding:24px 32px;background:#EDF2EE;color:#21382C;font-family:Nunito,"Microsoft YaHei",sans-serif}header{display:flex;align-items:baseline;justify-content:space-between;margin-bottom:18px}h1{font-size:24px;margin:0}header p{font-size:12px;color:#64756C;margin:0}.screens{display:flex;gap:28px;justify-content:space-between}figure{margin:0}figcaption{font-size:14px;margin-bottom:10px}img{width:390px;height:844px;display:block;border-radius:24px}
    </style><header><h1>SpeakPen · 聊聊界面方案</h1><p>设计预览 / 示例内容 / 原生 App 尚未修改</p></header><div class="screens">'''
    for name,label in zip(image_files,labels):
        board+=f'<figure><figcaption>{label}</figcaption><img src="{name}" alt="{label}"></figure>'
    board+='</div></html>'
    (folder/'overview.html').write_text(board,encoding='utf-8')
    page.goto((folder/'overview.html').as_uri())
    page.wait_for_load_state('networkidle')
    page.screenshot(path=str(folder/'overview.png'), full_page=True)
    browser.close()

report={'scope':'isolated HTML proposal, not Android validation','passed':['75% drawer width','drawer keyboard focus loop and Escape','independent current/default drafts','exit save and reopening','save failure retains draft and retry succeeds','scene goal disclosure','topic fills draft before explicit send','desktop/360/390 no horizontal overflow','reduced motion disables scrolling animation','no JavaScript errors'],'api_calls':0,'android_build':False,'device_validation':False}
(folder/'verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False))
