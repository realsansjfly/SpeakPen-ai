"""Verify the actual browser implementation with mocked AI; never uses cloud credentials."""
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import threading
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[1]
output = root / 'deliverables/learning-upgrade-2026-11-09/browser-check'
output.mkdir(parents=True, exist_ok=True)
class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *_):
        pass
server = ThreadingHTTPServer(('127.0.0.1', 0), partial(QuietHandler, directory=str(root / 'server/public')))
thread = threading.Thread(target=server.serve_forever, daemon=True)
thread.start()
history = [{'id':'old-user','role':'user','text':'SECRET_OLD_HISTORY'}, {'id':'old-ai','role':'assistant','text':'old'},
    {'id':'user','role':'user','text':'Orange juice, please.'}, {'id':'ai','role':'assistant','text':'Regular or large?'}]
results = []
try:
    with sync_playwright() as p:
        browser = p.chromium.launch(channel='msedge', headless=True)
        for width in [360, 390, 430]:
            context = browser.new_context(viewport={'width':width,'height':844}, is_mobile=True, has_touch=True)
            context.add_init_script('sessionStorage.setItem("speakpen-token","fixture");if(!localStorage.getItem("speakpen-web-v1"))localStorage.setItem("speakpen-web-v1",'+json.dumps(json.dumps({'messages':history,'library':[]}))+');')
            page = context.new_page()
            errors, calls = [], []
            page.on('pageerror', lambda e: errors.append(str(e)))
            page.on('console', lambda m: errors.append(m.text) if m.type == 'error' else None)
            page.route('**/v1/status', lambda route: route.fulfill(json={'ready':True}))
            def complete(route):
                body = route.request.post_data_json
                assert body['purpose'] == 'topic_bank'
                options = json.loads(body['messages'][-1]['content'])
                assert [m['id'] for m in options['round']] == ['user','ai']
                assert 'SECRET_OLD_HISTORY' not in json.dumps(body)
                calls.append(options)
                item = {'expression':'A large, please.','meaning':'礼貌选择大杯。','example':'A large orange juice, please.','translation':'请给我大杯橙汁。'}
                route.fulfill(json={'choices':[{'finish_reason':'stop','message':{'content':json.dumps({'items':[item]*options['count']})}}]})
            page.route('**/v1/chat/completions', complete)
            page.goto(f'http://127.0.0.1:{server.server_port}/')
            page.wait_for_load_state('networkidle')
            for purpose in ['ANSWER','FOLLOW_UP','RELATED']:
                page.locator('#topic-expressions').click()
                page.locator('#topic-purpose').select_option(purpose)
                page.locator('#topic-difficulty').select_option('IDIOMATIC')
                page.locator('#topic-count').fill('10')
                assert page.locator('#topic-count-label').inner_text() == '10'
                bounds = page.locator('#topic-options').bounding_box()
                assert bounds['x'] >= 0 and bounds['x'] + bounds['width'] <= width
                page.screenshot(path=str(output / f'{width}-{purpose}.png'))
                page.locator('#topic-generate').click()
                page.wait_for_function('document.querySelectorAll(".message.hint").length === '+str(len(calls)))
                assert calls[-1]['purpose'] == purpose
            assert page.locator('.message.user').count() == 2
            assert not errors, errors
            page.evaluate('localStorage.setItem("speakpen-web-v1",JSON.stringify({messages:[{role:"user",text:"pending"}],library:[]}))')
            page.reload()
            page.wait_for_load_state('networkidle')
            page.locator('#topic-expressions').click()
            assert page.locator('#topic-generate').is_disabled()
            assert '最新一轮尚未完成' in page.locator('#topic-boundary').inner_text()
            page.locator('#topic-cancel').click()
            assert len(calls) == 3
            results.append({'width':width,'purposes':3,'noOldContext':True,'noExtraUserMessage':True,'pendingRoundBlocked':True,'consoleErrors':errors})
            context.close()
        browser.close()
finally:
    server.shutdown(); server.server_close(); thread.join()
(output / 'results.json').write_text(json.dumps(results,indent=2), encoding='utf8')
print(json.dumps(results))
