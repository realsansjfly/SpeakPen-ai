from pathlib import Path
import sys,json
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'.tooling/preview-python'))
from playwright.sync_api import sync_playwright,expect
workspace=Path(__file__).resolve().parents[1]
requests=[];counts={'opt':0,'topic':0,'chat':0};errors=[]
with sync_playwright() as p:
 binary=next(str(x) for x in [Path('C:/Program Files/Google/Chrome/Application/chrome.exe'),Path('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')] if x.exists())
 browser=p.chromium.launch(headless=True,executable_path=binary)
 context=browser.new_context(viewport={'width':390,'height':844},permissions=['clipboard-read','clipboard-write'])
 page=context.new_page();page.on('pageerror',lambda e:errors.append(str(e)))
 page.add_init_script("sessionStorage.setItem('speakpen-token','fixture');")
 def reply(route):
  data=route.request.post_data_json;requests.append(data);system=data['messages'][0]['content']
  if 'ONLY from the single selected block' in system:
   selected=json.loads(data['messages'][1]['content']);text=json.dumps({'items':[{'kind':'PHRASE','content':'catch up over coffee','meaning':'喝咖啡叙旧','example':'Let us catch up over coffee.','sourceMessageId':selected['selectedId']}]})
  elif 'Rewrite ONLY' in system:
   counts['opt']+=1;text=f'I would love a coffee with less sugar. OPT_{counts["opt"]}'
  elif 'phrase bank' in system:
   counts['topic']+=1;text=f'Coffee shop expressions TOPIC_{counts["topic"]}\n\nless sugar — 少糖\nCould I have less sugar, please?'
  else:
   counts['chat']+=1;text=f'What would you like to order? CHAT_{counts["chat"]}'
  route.fulfill(status=200,content_type='application/json',body=json.dumps({'choices':[{'message':{'content':text}}]}))
 page.route('**/v1/status',lambda route:route.fulfill(status=200,content_type='application/json',body='{"ready":true}'))
 page.route('**/v1/chat/completions',reply)
 page.goto('http://localhost:8787/');page.wait_for_load_state('networkidle');page.locator('#autoplay').uncheck()
 page.locator('#summarize').click();expect(page.locator('#manual-entry')).to_be_visible();assert not requests
 page.locator('#entry-content').fill('Could I have less sugar?');page.locator('#entry-meaning').fill('少糖');page.locator('#entry-form button[type=submit]').click();assert not requests
 page.locator('#draft').fill('I want coffee little sugar.');page.locator('#send').click();expect(page.locator('.message.assistant')).to_have_count(1)
 user=page.locator('.message.user .bubble');box=user.bounding_box();page.mouse.move(box['x']+15,box['y']+15);page.mouse.down();page.wait_for_timeout(600);page.mouse.up()
 expect(page.locator('#message-actions')).to_be_visible();expect(page.locator('#rewrite-message')).to_have_text('优化表达')
 page.locator('#rewrite-message').click();expect(page.locator('.message.hint')).to_have_count(1)
 expect(page.locator('.message.hint')).to_contain_text('提示 · 优化表达');expect(page.locator('.message.hint')).to_contain_text('OPT_1')
 page.locator('.message.hint .bubble').click(button='right');expect(page.locator('#rewrite-message')).to_have_text('重新生成');page.locator('#rewrite-message').click();expect(page.locator('.message.hint')).to_have_count(1);expect(page.locator('.message.hint')).to_contain_text('OPT_2')
 page.locator('#topic-expressions').click();expect(page.locator('.message.hint')).to_have_count(2);expect(page.locator('.message.hint').last).to_contain_text('话题语料')
 page.locator('.message.user .bubble').click(button='right');page.locator('#identify-expression').click();expect(page.locator('#candidates')).to_be_visible();extract=requests[-1];assert len(extract['messages'])==2;assert 'What would you' not in extract['messages'][1]['content'];assert 'OPT_' not in extract['messages'][1]['content']
 page.locator('.candidate input[aria-label="英文内容"]').fill('catch up with a friend');page.locator('#save-candidates').click()
 page.locator('#draft').fill('I went to a cafe today.');page.locator('#send').click();expect(page.locator('.message.assistant')).to_have_count(2)
 latest=requests[-1];assert all(x['role'] in ['system','user','assistant'] for x in latest['messages']);assert not any('OPT_' in x['content'] or 'TOPIC_' in x['content'] for x in latest['messages'])
 page.locator('.message.assistant .bubble').first.click(button='right');page.locator('#rewrite-message').click();expect(page.locator('.message.assistant')).to_have_count(2)
 assert page.locator('.message.assistant').last.inner_text().strip()
 page.locator('.message.user .bubble').first.click(button='right');page.locator('#copy-message').click();assert page.evaluate('navigator.clipboard.readText()')=='I want coffee little sugar.'
 page.reload();page.wait_for_load_state('networkidle');page.locator('#autoplay').uncheck();expect(page.locator('.message.hint')).to_have_count(2)
 page.locator('#library').click();assert page.locator('.item').count()==2
 assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
 assert not errors,errors
 page.locator('#library').click();page.screenshot(path=str(workspace/'design/learning-actions-mobile.png'),full_page=True)
 browser.close()
print('LEARNING_UI_PASS: blank/manual no AI, long press menus, selected-only extraction/edit, optimize/topic blue hints, context isolation, copy, regeneration preserves later turns, reload')
