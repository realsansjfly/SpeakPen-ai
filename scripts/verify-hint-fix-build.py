"""Verify dev07 identity, signature, source mirror, tests and private credential boundary."""
from pathlib import Path
import hashlib, json, os, re, subprocess, sys, zipfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
mirror = Path(sys.argv[1]).resolve()
out = root / 'deliverables/hint-fix-2026-10-31'
apk = out / 'SpeakPen-0.6.0-dev07-debug.apk'
previous = root / 'deliverables/topics-motion-2026-10-31/SpeakPen-0.6.0-dev06-debug.apk'
bt = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/build-tools/35.0.0'
env = dict(os.environ, JAVA_HOME='C:/EmojiJdk17')

def run(command):
    result = subprocess.run(command, env=env, capture_output=True, text=True, encoding='utf-8', errors='replace')
    assert result.returncode == 0, 'Verification tool failed: ' + str(command[0])
    return result.stdout

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def cert(path):
    return re.search(r'certificate SHA-256 digest: (\w+)',
        run([str(bt/'apksigner.bat'), 'verify', '--print-certs', str(path)])).group(1)

identity = run([str(bt/'aapt2.exe'), 'dump', 'badging', str(apk)]).splitlines()[0]
assert all(s in identity for s in ["name='com.speakpen.app'", "versionCode='14'", "versionName='0.6.0-dev07'"])
signer = cert(apk)
assert signer == cert(previous)
paths = [p.relative_to(root) for p in (root/'app/src').rglob('*') if p.is_file()]
paths += [Path(p) for p in ['app/build.gradle.kts','build.gradle.kts','settings.gradle.kts','gradle.properties']]
assert all((mirror/p).is_file() and sha(root/p) == sha(mirror/p) for p in paths), 'Build source mismatch'
config = (mirror/'app/build/generated/source/buildConfig/debug/com/speakpen/app/BuildConfig.java').read_text()
assert re.search(r'SERVICE_BASE_URL\s*=\s*""', config) and re.search(r'SERVICE_ACCESS_TOKEN\s*=\s*""', config)
secrets = []
for line in (root/'server/.env').read_text(encoding='utf-8-sig').splitlines():
    if '=' in line and not line.lstrip().startswith('#'):
        key, value = line.split('=',1); value = value.strip().strip('"\'')
        if len(value) >= 8 and ('KEY' in key or 'TOKEN' in key): secrets.append(value)
connection = json.loads((root/'.local/connection.json').read_text(encoding='utf-8-sig'))
secrets += [connection[k] for k in ['baseUrl','accessToken'] if connection.get(k)]
needles = [s.encode(enc) for s in secrets for enc in ['utf-8','utf-16le']]
with zipfile.ZipFile(apk) as z:
    for entry in z.infolist(): assert not any(n in z.read(entry) for n in needles), 'Private credential found'
    for asset in (root/'app/src/main/assets').rglob('*'):
        if asset.is_file(): assert z.read('assets/'+asset.relative_to(root/'app/src/main/assets').as_posix()) == asset.read_bytes()
totals = {k:0 for k in ['tests','failures','errors','skipped']}
for p in (out/'test-results').glob('TEST-*.xml'):
    suite = ET.parse(p).getroot()
    for k in totals: totals[k] += int(suite.get(k,0))
assert totals['tests'] == 170 and not sum(totals[k] for k in ['failures','errors','skipped'])
assert not ET.parse(out/'reports/lint-results-debug.xml').getroot().findall('issue')
status = [json.loads(s) for s in (out/'service-status.jsonl').read_text(encoding='utf-8-sig').splitlines() if s.strip()]
assert len(status) == 2 and all(s['status'] == 200 and s['pass'] and s['sameLocalInstance'] for s in status)
report = {'date':'2026-10-31','versionName':'0.6.0-dev07','versionCode':14,'applicationId':'com.speakpen.app',
    'apkSha256':sha(apk),'apkBytes':apk.stat().st_size,'signerSha256':signer,'sameSignerAsDev06':True,
    'unpaired':True,'knownCredentialScan':'pass','matchingSourceInputs':len(paths),'buildMirror':str(mirror),
    'android':totals,'lintIssues':0,'node':'not rerun: no server code changes',
    'realServiceStatus':status,'paidAiAsrTests':'not run','deviceTests':'not run: adb has no device',
    'scope':'Stronger scaffold output contract, common placeholder compatibility, explicit failure messages',
    'limit':'Original failed model response unavailable; exact rejected field and live fix effectiveness unverified'}
(out/'verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(out/'SHA256SUMS.txt').write_text(sha(apk)+'  '+apk.name+'\n',encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))
