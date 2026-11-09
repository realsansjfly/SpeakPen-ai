"""Verify JVM-generated production opening prompts against the configured cloud service.
Uses the ignored cloud connection file, never prints its token. Makes two paid requests.
"""
from pathlib import Path
import argparse
import json
import requests

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('fixture', type=Path)
parser.add_argument('--output', type=Path, default=Path('deliverables/home-cylinder-2026-11-09/topic-opening-live-results.json'))
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
cfg = json.loads((root / '.local/cloud-connection.json').read_text(encoding='utf-8-sig'))
session = requests.Session()
session.trust_env = False
results = []
for case in json.loads(args.fixture.read_text(encoding='utf-8')):
    response = session.post(cfg['baseUrl'] + '/chat/completions',
        headers={'Authorization': 'Bearer ' + cfg['accessToken']},
        json={'messages': case['messages'], 'stream': True, 'temperature': .65, 'reply_length': 'short'}, timeout=55)
    response.raise_for_status()
    chunks = []
    for line in response.text.splitlines():
        if not line.startswith('data:'):
            continue
        data = line[5:].strip()
        if data == '[DONE]':
            continue
        for choice in json.loads(data).get('choices', []):
            chunks.append(choice.get('delta', {}).get('content', '') or '')
    text = ''.join(chunks).strip()
    checks = {
        'contains_question': '?' in text,
        'not_copied_verbatim': case['topic'].lower() not in text.lower(),
        'not_answering_as_self': not text.lower().startswith(('i ', "i'")),
        'stream_completed': '[DONE]' in response.text,
    }
    result = {'topic': case['topic'], 'reply': text, 'status': response.status_code,
        'production_prompt_used': True, 'checks': checks}
    results.append(result)
    print(json.dumps(result, ensure_ascii=True), flush=True)
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
args.output.with_name('topic-opening-live-requests.json').write_text(args.fixture.read_text(encoding='utf-8'), encoding='utf-8')
if not all(all(result['checks'].values()) for result in results):
    raise SystemExit('Opening behavior checks failed; inspect recorded responses.')
