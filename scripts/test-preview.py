from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / ".tooling/preview-python"))
from playwright.sync_api import sync_playwright
import os

workspace = Path(__file__).resolve().parents[1]
errors = []
with sync_playwright() as p:
    candidates = [Path(os.environ.get('PROGRAMFILES', 'C:/Program Files')) / 'Google/Chrome/Application/chrome.exe', Path('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')]
    binary = next((str(x) for x in candidates if x.exists()), None)
    browser = p.chromium.launch(headless=True, executable_path=binary)
    page = browser.new_page(viewport={'width': 1300, 'height': 890})
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.goto((workspace / 'design/preview.html').as_uri())
    page.wait_for_load_state('networkidle')
    assert page.locator('.phone').count() == 3
    page.locator('.message.user .expand').click()
    assert page.locator('.message.user .transcript').is_visible()
    page.locator('.message.user .expand').click()
    assert not page.locator('.message.user .transcript').is_visible()
    page.locator('.message.user .mark').click()
    assert '已标记' in page.locator('.message.user .mark').inner_text()
    page.locator('.message.user .voice').click()
    assert 'playing' in page.locator('.message.user .voice').get_attribute('class')
    page.locator('#text-pref').click()
    assert page.locator('.message:not(.user) .transcript').is_visible()
    page.locator('#voice-pref').click()
    assert not page.locator('.message:not(.user) .transcript').is_visible()
    rect = page.locator('#hold').bounding_box()
    x, y = rect['x'] + rect['width'] / 2, rect['y'] + rect['height'] / 2
    page.mouse.move(x, y); page.mouse.down()
    assert '松开发送' in page.locator('#hold').inner_text()
    page.mouse.up()
    assert '手势完成' in page.locator('#hint').inner_text()
    page.mouse.move(x, y); page.mouse.down(); page.mouse.move(x, y - 100)
    assert '松开取消' in page.locator('#hold').inner_text()
    page.mouse.up()
    assert '已取消' in page.locator('#hint').inner_text()
    assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
    page.set_viewport_size({'width': 390, 'height': 844})
    assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
    assert not errors, errors
    print('UI_PREVIEW_PASS: expand/collapse, mark, playback indicator, display preference, hold/release, slide/cancel, desktop/mobile overflow, no JS errors')
    browser.close()

