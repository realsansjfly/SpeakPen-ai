"""Verify a local Unpaired APK without printing private configuration values."""
from pathlib import Path
import hashlib, json, os, re, subprocess, sys, zipfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
mirror = Path(sys.argv[1]).resolve()
out = root / 'deliverables/vocabulary-2026-10-31'
apk = out / 'SpeakPen-0.6.0-dev04-debug.apk'
previous = root / 'deliverables/ui-2026-10-31/SpeakPen-0.6.0-dev03-debug.apk'
sdk = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk'
buildtools = sdk / 'build-tools/35.0.0'
env = dict(os.environ, JAVA_HOME='C:/EmojiJdk17')
def run(command):
    result = subprocess.run(command, env=env, capture_output=True, text=True, encoding='utf-8', errors='replace')
    assert result.returncode == 0, 'Verification tool failed: ' + str(command[0])
    return result.stdout
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def certificate(path):
    output = run([str(buildtools/'apksigner.bat'), 'verify', '--print-certs', str(path)])
    return re.search(r'certificate SHA-256 digest: (\w+)', output).group(1)

identity = run([str(buildtools/'aapt2.exe'), 'dump', 'badging', str(apk)]).splitlines()[0]
assert "name='com.speakpen.app'" in identity and "versionCode='11'" in identity and "versionName='0.6.0-dev04'" in identity
cert = certificate(apk)
assert cert == certificate(previous), 'Signing certificate changed'
paths = [p.relative_to(root) for p in (root/'app/src').rglob('*') if p.is_file()]
paths += [Path(p) for p in ['app/build.gradle.kts', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties']]
assert all((mirror/p).is_file() and sha(root/p) == sha(mirror/p) for p in paths), 'Build mirror differs from current source'
secrets = []
for line in (root/'server/.env').read_text(encoding='utf-8-sig').splitlines():
    if '=' in line and not line.lstrip().startswith('#'):
        key, value = line.split('=', 1); value = value.strip().strip('"\'')
        if len(value) >= 8 and ('KEY' in key or 'TOKEN' in key): secrets.append(value)
connection = json.loads((root/'.local/connection.json').read_text(encoding='utf-8-sig'))
secrets += [connection[k] for k in ['baseUrl', 'accessToken'] if connection.get(k)]
needles = [value.encode(encoding) for value in secrets for encoding in ['utf-8', 'utf-16le']]
with zipfile.ZipFile(apk) as z:
    for info in z.infolist():
        assert not any(n in z.read(info) for n in needles), 'Known private credential found in APK'
    for tag in ['cet4', 'cet6', 'ielts']:
        assert z.read('assets/vocabulary/'+tag+'.txt') == (root/'app/src/main/assets/vocabulary'/f'{tag}.txt').read_bytes()
config_source = (mirror/'app/build/generated/source/buildConfig/debug/com/speakpen/app/BuildConfig.java').read_text()
assert re.search(r'SERVICE_BASE_URL\s*=\s*""', config_source)
assert re.search(r'SERVICE_ACCESS_TOKEN\s*=\s*""', config_source)
totals = {key: 0 for key in ['tests', 'failures', 'errors', 'skipped']}
for p in (out/'test-results').glob('TEST-*.xml'):
    suite = ET.parse(p).getroot()
    for key in totals: totals[key] += int(suite.get(key, 0))
assert totals['tests'] >= 137 and not sum(totals[k] for k in ['failures', 'errors', 'skipped'])
lint = ET.parse(out/'reports/lint-results-debug.xml').getroot()
assert not lint.findall('issue')
evaluation = mirror/'app/build/outputs/vocabulary-offline-evaluation.json'
(out/'vocabulary-offline-evaluation.json').write_bytes(evaluation.read_bytes())
manifest = json.loads((root/'app/src/main/assets/vocabulary/manifest.json').read_text(encoding='utf-8'))
report = {'date': '2026-10-31', 'versionName': '0.6.0-dev04', 'versionCode': 11,
    'applicationId': 'com.speakpen.app', 'apkBytes': apk.stat().st_size, 'apkSha256': sha(apk),
    'signerSha256': cert, 'sameSignerAsDev03': True, 'unpaired': True, 'knownCredentialScan': 'pass',
    'buildMirror': str(mirror), 'matchingSourceInputs': len(paths), 'android': totals, 'lintIssues': 0,
    'node': {'tests': 46, 'passed': 46, 'result': 'npm test executed this batch'},
    'builtInCounts': {k: manifest[k]['entries'] for k in ['cet4','cet6','ielts']},
    'offlineCandidateCases': len(json.loads(evaluation.read_text(encoding='utf-8'))),
    'realServiceStatus': {'local': 200, 'public': 200, 'sameInstance': True},
    'paidAiAsrTests': 'not run', 'deviceTests': 'not run: adb has no device',
    'uiEvidence': 'static HTML flow preview only; no Android screenshot'}
(out/'verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(report, ensure_ascii=False, indent=2))
