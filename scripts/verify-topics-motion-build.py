"""Verify the dev06 local Unpaired delivery without printing private configuration."""
from pathlib import Path
import hashlib, json, os, re, subprocess, sys, zipfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
mirror = Path(sys.argv[1]).resolve()
out = root / 'deliverables/topics-motion-2026-10-31'
apk = out / 'SpeakPen-0.6.0-dev06-debug.apk'
previous = root / 'deliverables/learning-recap-2026-10-31/SpeakPen-0.6.0-dev05-debug.apk'
buildtools = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/build-tools/35.0.0'
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
assert "name='com.speakpen.app'" in identity and "versionCode='13'" in identity and "versionName='0.6.0-dev06'" in identity
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
assets = list((root/'app/src/main/assets').rglob('*'))
with zipfile.ZipFile(apk) as z:
    for info in z.infolist():
        assert not any(n in z.read(info) for n in needles), 'Known private credential found in APK'
    for p in assets:
        if p.is_file():
            entry = 'assets/' + p.relative_to(root/'app/src/main/assets').as_posix()
            assert z.read(entry) == p.read_bytes(), 'Packaged resource mismatch: ' + entry
config = (mirror/'app/build/generated/source/buildConfig/debug/com/speakpen/app/BuildConfig.java').read_text()
assert re.search(r'SERVICE_BASE_URL\s*=\s*""', config)
assert re.search(r'SERVICE_ACCESS_TOKEN\s*=\s*""', config)
totals = {key: 0 for key in ['tests', 'failures', 'errors', 'skipped']}
for p in (out/'test-results').glob('TEST-*.xml'):
    suite = ET.parse(p).getroot()
    for key in totals: totals[key] += int(suite.get(key, 0))
assert totals['tests'] == 169 and not sum(totals[k] for k in ['failures', 'errors', 'skipped'])
assert not ET.parse(out/'reports/lint-results-debug.xml').getroot().findall('issue')
node = (out/'node-tests.txt').read_text(encoding='utf-8-sig')
assert re.search(r'tests\s+46\b', node) and re.search(r'pass\s+46\b', node) and re.search(r'fail\s+0\b', node)
status = [json.loads(line) for line in (out/'service-status.jsonl').read_text(encoding='utf-8-sig').splitlines() if line.strip()]
assert len(status) == 2 and all(s['status'] == 200 and s['pass'] and s['sameLocalInstance'] for s in status)
manifest = json.loads((root/'app/src/main/assets/topics/manifest.json').read_text(encoding='utf-8'))
assert sha(root/'app/src/main/assets/topics/catalog.json') == manifest['sha256']
preview = json.loads((out/'preview-verification.json').read_text(encoding='utf-8'))
assert preview['preview'] == 'pass'
report = {'date':'2026-10-31', 'versionName':'0.6.0-dev06', 'versionCode':13,
    'applicationId':'com.speakpen.app', 'apkBytes':apk.stat().st_size, 'apkSha256':sha(apk),
    'signerSha256':cert, 'sameSignerAsDev05':True, 'unpaired':True, 'knownCredentialScan':'pass',
    'buildMirror':str(mirror), 'matchingSourceInputs':len(paths), 'matchingPackagedAssets':sum(p.is_file() for p in assets),
    'android':totals, 'lintIssues':0, 'node':{'tests':46,'passed':46,'result':'npm test executed this batch'},
    'topics':manifest, 'topicsAndMotionTests':7, 'preview':preview, 'realServiceStatus':status,
    'paidAiAsrTests':'not run; no topic or motion request uses the model',
    'deviceTests':'not run: adb has no device',
    'limits':['No Android rendering or frame-rate evidence','No device upgrade or accessibility acceptance']}
(out/'SHA256SUMS.txt').write_text(sha(apk)+'  '+apk.name+'\n',encoding='utf-8')
(out/'verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))
