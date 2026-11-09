"""Run on the cloud host. Never print credentials, signed audio URLs or raw provider errors."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time
import urllib.request
import urllib.error

parser = argparse.ArgumentParser()
parser.add_argument('--baseline', action='store_true')
parser.add_argument('--paid', action='store_true', help='One chat, one short TTS, one ASR using the synthesized sample')
parser.add_argument('--worker-only', action='store_true')
parser.add_argument('--fallback-probe', action='store_true', help='Briefly stop retrieval and verify safe public fallback, then restart in finally')
args = parser.parse_args()
env_path = Path('/etc/speakpen/speakpen.env')
baseline_path = Path('/root/speakpen-learning-baseline.json')
def fingerprint():
    return {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in [env_path, Path('/etc/nginx/sites-available/speakpen')] if path.exists()}
if args.baseline:
    baseline_path.write_text(json.dumps(fingerprint()))
    baseline_path.chmod(0o600)
    print(json.dumps({'baselineSaved': True}))
    raise SystemExit()
env = {}
for line in env_path.read_text().splitlines():
    if '=' in line and not line.startswith('#'):
        key, value = line.split('=', 1)
        env[key] = value.strip().strip('"').strip("'")
headers = {'Authorization': 'Bearer ' + env['SERVICE_ACCESS_TOKEN']}
def request(url, value=None, mime='application/json'):
    data = json.dumps(value).encode() if isinstance(value, dict) else value
    req = urllib.request.Request(url, data=data, headers={**headers, 'Content-Type': mime})
    with urllib.request.urlopen(req, timeout=50) as response:
        body = response.read()
        return json.loads(body) if 'json' in response.headers.get('Content-Type', '') else body
output = {'configurationPreserved': json.loads(baseline_path.read_text()) == fingerprint() if baseline_path.exists() else None}
for unit in ['speakpen', 'speakpen-semantic', 'nginx']:
    output[unit] = subprocess.check_output(['systemctl', 'is-active', unit], text=True).strip()
output['worker'] = request('http://127.0.0.1:8791/health')
endpoint = 'http://127.0.0.1:8791/search' if args.worker_only else 'https://47.118.51.5/v1/vocabulary/search'
output['recall'] = []
for text in ['我预订了酒店但前台找不到我的订单，需要确认姓名和日期。', 'I would like a large orange juice without ice, please.']:
    start = time.monotonic()
    result = request(endpoint, {'query': text, 'count': 4, 'books': ['anki-cet4'], 'level': 'DAILY'})
    assert not result.get('fallback') and result['candidates']
    output['recall'].append({'query': text, 'words': [c['expression'] for c in result['candidates']], 'ms': round((time.monotonic() - start) * 1000), 'version': result['version']})
if not args.worker_only:
    output['health'] = request('https://47.118.51.5/health')
    output['statusReady'] = request('https://47.118.51.5/v1/status')['ready']
    try:
        urllib.request.urlopen(urllib.request.Request('https://47.118.51.5/v1/vocabulary/search', data=b'{}', headers={'Content-Type':'application/json'}))
    except urllib.error.HTTPError as error:
        output['unauthorizedStatus'] = error.code
    assert output['unauthorizedStatus'] == 401
if args.paid:
    base = 'https://47.118.51.5/v1'
    chat = request(base + '/chat/completions', {'messages': [{'role': 'user', 'content': 'Reply with one short English greeting.'}], 'reply_length': 'short'})
    output['chat'] = {'ok': bool(chat['choices'][0]['message']['content'].strip())}
    audio = request(base + '/audio/speech', {'input': 'Hello. I would like orange juice.'})
    assert isinstance(audio, bytes) and audio.startswith(b'RIFF')
    assert int.from_bytes(audio[4:8], 'little') == len(audio) - 8
    output['tts'] = {'ok': True, 'bytes': len(audio), 'normalizedWav': True}
    boundary = 'SpeakPenVerifyLearningBoundary'
    multipart = (f'--{boundary}\r\nContent-Disposition: form-data; name="language"\r\n\r\nen\r\n'
        f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="sample.wav"\r\nContent-Type: audio/wav\r\n\r\n').encode() + audio + f'\r\n--{boundary}--\r\n'.encode()
    asr = request(base + '/audio/transcriptions', multipart, 'multipart/form-data; boundary=' + boundary)
    output['asr'] = {'ok': bool(asr.get('text', '').strip()), 'recognizedJuice': 'juice' in asr.get('text', '').lower()}
    assert output['asr']['ok']
if args.fallback_probe:
    subprocess.check_call(['systemctl', 'stop', 'speakpen-semantic'])
    try:
        result = request('https://47.118.51.5/v1/vocabulary/search', {'query': '酒店预订', 'count': 4, 'books': ['anki-cet4'], 'level': 'DAILY'})
        assert result.get('fallback') is True and result['candidates'] == []
        assert request('https://47.118.51.5/health')['ready']
        output['fallbackProbe'] = {'safeFallback': True, 'chatServiceReady': True}
    finally:
        subprocess.check_call(['systemctl', 'start', 'speakpen-semantic'])
    for attempt in range(20):
        try:
            output['workerAfterFallback'] = request('http://127.0.0.1:8791/health')
            break
        except (OSError, urllib.error.URLError):
            if attempt == 19:
                raise RuntimeError('Retrieval did not recover after fallback probe') from None
            time.sleep(.25)
output['quotaFilePresent'] = Path(env.get('USAGE_FILE', '/var/lib/speakpen/usage.json')).is_file()
output['workerMemoryBytes'] = int(subprocess.check_output(['systemctl', 'show', 'speakpen-semantic', '-p', 'MemoryCurrent', '--value'], text=True).strip())
print(json.dumps(output, ensure_ascii=False, indent=2))
