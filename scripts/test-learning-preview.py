"""Exercise the standalone preview without making model or microphone requests."""
import json
from pathlib import Path
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[1]
output = root / 'deliverables/learning-upgrade-2026-11-09/preview-check'
output.mkdir(parents=True, exist_ok=True)
url = (root / 'design/learning-upgrade-2026-11-09/index.html').as_uri()
results = []


def layout(page, name):
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), name + ': document overflow'
    bad = page.locator('main').evaluate("""main => [...main.querySelectorAll('*')].filter(e => {
      const r=e.getBoundingClientRect(); return r.width>0 && (r.right>innerWidth+1 || r.left < -1);
    }).map(e=>e.tagName+':'+e.textContent.slice(0,50))""")
    assert not bad, (name, bad)
    page.screenshot(path=str(output / (name + '.png')), full_page=True)


with sync_playwright() as p:
    browser = p.chromium.launch(channel='msedge', headless=True)
    for width in [360, 390, 430]:
        context = browser.new_context(viewport={'width': width, 'height': 844}, is_mobile=True, has_touch=True)
        page = context.new_page()
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.on('console', lambda message: errors.append(message.text) if message.type == 'error' else None)
        page.goto(url)
        page.wait_for_load_state('networkidle')
        page.evaluate('localStorage.clear()')
        page.reload()
        layout(page, f'{width}-brief')
        page.locator('#scene').select_option('3')
        page.get_by_role('button', name='先学习', exact=True).click()
        layout(page, f'{width}-listen')
        page.locator('#listen').click()
        assert '已触发' in page.locator('#listen-state').inner_text()
        page.locator('#next').click()
        assert page.locator('#next').is_disabled()
        page.locator('#lesson-record').click()
        page.locator('#feedback').click()
        layout(page, f'{width}-repeat')
        page.locator('#next').click()
        assert page.get_by_role('heading', name='关键词替换', exact=True).count() == 1
        page.locator('#lesson-record').click()
        page.locator('#transcript').fill('I have a reservation for tomorrow.')
        page.locator('#feedback').click()
        page.locator('#next').click()
        assert page.get_by_role('heading', name='角色热身', exact=True).count() == 1
        page.locator('#lesson-record').click()
        page.locator('#feedback').click()
        page.locator('#next').click()
        assert '片段 2 /' in page.locator('main').inner_text()
        page.locator('#exit-lesson').click()
        page.get_by_role('button', name='先学习', exact=True).click()
        assert '片段 2 /' in page.locator('main').inner_text()
        page.locator('#exit-lesson').click()
        page.locator('[data-page=editor]').click()
        layout(page, f'{width}-editor')
        page.locator('[name=name]').fill('图书馆借书')
        page.locator('[data-page=words]').click()
        assert page.locator('#leave').is_visible()
        page.locator('#keep').click()
        page.locator('[name=user]').fill('读者')
        page.locator('[name=ai]').fill('图书管理员')
        page.locator('[name=goals]').fill('询问书的位置\n确认借阅期限')
        page.locator('[name=end]').fill('完成借书并道谢')
        page.get_by_role('button', name='保存场景').click()
        assert page.get_by_role('heading', name='图书馆借书', exact=True).count() == 1
        page.locator('[data-page=words]').click()
        layout(page, f'{width}-words')
        page.locator('#word-search').fill('interdepartmental')
        page.locator('summary').click()
        page.evaluate("document.documentElement.style.fontSize='24px'")
        layout(page, f'{width}-words-large-font')
        page.evaluate("document.documentElement.style.fontSize='16px'")
        page.locator('[data-page=topic]').click()
        page.locator('#incomplete').check()
        assert page.locator('#generate').is_disabled()
        assert page.locator('#round-error').is_visible()
        page.locator('#incomplete').uncheck()
        for purpose in ['回答', '追问', '泛话题相关']:
            page.locator('#purposes').get_by_role('button', name=purpose, exact=True).click()
            for difficulty in ['默认', '高级表达', '地道表达']:
                page.locator('#difficulties').get_by_role('button', name=difficulty, exact=True).click()
                page.locator('#count').fill('10')
                page.locator('#generate').click()
                assert page.locator('#bank article').count() == 10
        layout(page, f'{width}-topic')
        page.locator('[data-page=voice]').click()
        page.locator('#multi').check()
        for _ in range(6):
            page.locator('#record').click()
        assert page.locator('#record').is_disabled()
        page.locator('[data-play="0"]').click()
        page.locator('[data-redo="1"]').click()
        page.locator('[data-delete="5"]').click()
        page.locator('#fail').check()
        page.locator('#submit').click()
        assert page.locator('.segment').count() == 5
        layout(page, f'{width}-queue-failed')
        page.locator('#multi').uncheck()
        page.locator('[data-page=brief]').click()
        page.locator('[data-page=voice]').click()
        assert page.locator('.segment').count() == 5
        page.reload()
        page.locator('[data-page=voice]').click()
        assert page.locator('.segment').count() == 5
        page.locator('#submit').click()
        assert page.locator('.segment').count() == 0
        assert 'AI 回复 1 次' in page.locator('#voice-reply').inner_text()
        layout(page, f'{width}-queue-submitted')
        page.locator('[data-page=settings]').click()
        page.locator('#fallback').click()
        assert '暂用本地筛选' in page.locator('#retrieval-status').inner_text()
        layout(page, f'{width}-settings')
        # Also exercise the main tasks at 150% text size, including editable and error states.
        page.evaluate("document.documentElement.style.fontSize='24px'")
        for module in ['brief', 'editor', 'lesson', 'topic', 'voice', 'settings']:
            page.locator(f'[data-page={module}]').click()
            layout(page, f'{width}-{module}-large-font')
        assert not errors, errors
        results.append({'width': width, 'height': 844, 'consoleErrors': errors, 'passed': True})
        context.close()
    browser.close()
(output / 'results.json').write_text(json.dumps(results, indent=2), encoding='utf8')
print(json.dumps(results))
