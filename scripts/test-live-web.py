from pathlib import Path
import sys, json, os
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / '.tooling/preview-python'))
from playwright.sync_api import sync_playwright, expect

workspace = Path(__file__).resolve().parents[1]
connection = json.loads((workspace / '.local/connection.json').read_text(encoding='utf-8-sig'))
errors = []
with sync_playwright() as p:
    paths = [Path(os.environ.get('PROGRAMFILES', 'C:/Program Files'))/'Google/Chrome/Application/chrome.exe', Path('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')]
    executable = next(str(path) for path in paths if path.exists())
    browser = p.chromium.launch(headless=True, executable_path=executable, args=[
        '--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream',
        '--use-file-for-fake-audio-capture=' + str(workspace/'.local/english-test.wav'), '--autoplay-policy=no-user-gesture-required',
    ])
    context = browser.new_context(viewport={'width': 390, 'height': 844}, permissions=['microphone'])
    page = context.new_page()
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.goto('http://localhost:8787/#token=' + connection['accessToken'])
    page.wait_for_load_state('networkidle')
    expect(page.locator('#status')).to_contain_text('已连接', timeout=15000)
    page.locator('#autoplay').uncheck()
    page.locator('#draft').fill('I had coffee with a friend today.')
    page.locator('#send').click()
    expect(page.locator('.message.assistant')).to_have_count(1, timeout=30000)
    page.locator('.message.user > .quiet').click()
    expect(page.locator('.message.user > .quiet')).to_contain_text('已标记')
    page.locator('.message.assistant summary').click()
    expect(page.locator('.message.assistant .transcript')).to_be_visible()
    page.locator('#display').click()
    expect(page.locator('.message.assistant .bubble')).to_contain_text('?')
    page.locator('#display').click()
    with page.expect_response(lambda response: response.url.endswith('/v1/audio/speech'), timeout=30000) as speech:
        page.locator('.message.assistant .voice').click()
    assert speech.value.status == 200
    page.locator('#stop-speech').click()
    hold = page.locator('#hold').bounding_box()
    x, y = hold['x']+hold['width']/2, hold['y']+hold['height']/2
    page.mouse.move(x,y); page.mouse.down()
    expect(page.locator('#hold')).to_have_class('hold recording', timeout=10000)
    page.wait_for_timeout(5200)
    page.mouse.up()
    expect(page.locator('.message.user audio')).to_have_count(1, timeout=10000)
    expect(page.locator('.message.assistant')).to_have_count(2, timeout=30000)
    page.locator('.message.user audio').click(position={'x': 25, 'y': 25})
    assert page.locator('.message.user audio').evaluate('(audio) => !!audio.src')
    page.reload(); page.wait_for_load_state('networkidle')
    expect(page.locator('.message.user audio')).to_have_count(1)
    expect(page.locator('.message.assistant')).to_have_count(2)
    page.locator('#summarize').click()
    expect(page.locator('#manual-entry')).to_be_visible()
    assert page.locator('#entry-content').input_value() == ''
    page.locator('#cancel-entry').click()
    page.locator('.message.assistant .bubble').first.click(button='right')
    page.locator('#identify-expression').click()
    expect(page.locator('#candidates')).to_be_visible(timeout=30000)
    assert page.locator('.candidate').count() >= 1
    page.locator('#save-candidates').click()
    page.locator('#library').click()
    assert page.locator('.item').count() >= 1
    page.locator('.item button').first.click()
    expect(page.locator('.message.assistant')).to_have_count(1, timeout=30000)
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
    page.screenshot(path=str(workspace/'design/live-service-mobile.png'), full_page=True)
    assert not errors, errors
    print('LIVE_BROWSER_PASS: real text/ASR/TTS/manual/selected-extraction/review, original audio preserved after reload, no page errors, mobile layout')
    browser.close()

