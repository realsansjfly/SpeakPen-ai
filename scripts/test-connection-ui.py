from pathlib import Path
import sys, json
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'.tooling/preview-python'))
from playwright.sync_api import sync_playwright, expect
workspace=Path(__file__).resolve().parents[1]
connection=json.loads((workspace/'.local/connection.json').read_text(encoding='utf-8-sig'))
errors=[]
with sync_playwright() as p:
    binaries=[Path('C:/Program Files/Google/Chrome/Application/chrome.exe'),Path('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')]
    browser=p.chromium.launch(headless=True,executable_path=next(str(x) for x in binaries if x.exists()))
    context=browser.new_context(viewport={'width':390,'height':844},permissions=['clipboard-read','clipboard-write'])
    page=context.new_page();page.on('pageerror',lambda error:errors.append(str(error)))
    page.goto('http://localhost:8787/#token='+connection['accessToken'])
    page.wait_for_load_state('networkidle')
    expect(page.locator('#status')).to_contain_text('已连接')
    page.locator('#copy-link').click()
    expect(page.locator('#feedback')).to_contain_text('已复制')
    copied=page.evaluate('navigator.clipboard.readText()')
    assert copied=='http://localhost:8787/#token='+connection['accessToken']
    assert page.evaluate('document.documentElement.scrollWidth<=innerWidth'), 'mobile horizontal overflow'
    assert not errors,errors
    browser.close()
print('CONNECTION_UI_PASS: authorized status, copy pairing entry, mobile layout, no JS errors')
