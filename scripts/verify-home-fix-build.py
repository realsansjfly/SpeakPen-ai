"""Verify the private dev10 artifact and write only redacted build facts."""
from pathlib import Path
import hashlib, json, os, re, subprocess, sys, zipfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
mirror = Path(sys.argv[1]).resolve()
out = root / 'deliverables/home-webview-fix-2026-11-09'
apk = out / 'SpeakPen-0.6.0-dev10-cloud-debug.apk'
previous = root / 'deliverables/home-cylinder-2026-11-09/SpeakPen-0.6.0-dev09-cloud-debug.apk'
bt = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/build-tools/35.0.0'
env = dict(os.environ, JAVA_HOME='C:/EmojiJdk17')
def run(args):
    result = subprocess.run(args, env=env, capture_output=True, text=True, encoding='utf-8', errors='replace')
    assert result.returncode == 0, 'Verification tool failed: '+str(args[0])
    return result.stdout
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def cert(path):
    return re.search(r'certificate SHA-256 digest: (\w+)', run([str(bt/'apksigner.bat'), 'verify', '--print-certs', str(path)])).group(1)

identity = run([str(bt/'aapt2.exe'), 'dump', 'badging', str(apk)]).splitlines()[0]
assert "name='com.speakpen.app'" in identity and "versionCode='17'" in identity and "versionName='0.6.0-dev10'" in identity
signer = cert(apk)
assert signer == cert(previous), 'Signing certificate changed'
sources = [p for p in (root/'app/src').rglob('*') if p.is_file()]+[root/'app/build.gradle.kts']
assert all(sha(p) == sha(mirror/p.relative_to(root)) for p in sources), 'Build source differs from current source'
connection = json.loads((root/'.local/cloud-connection.json').read_text(encoding='utf-8-sig'))
config = (mirror/'app/build/generated/source/buildConfig/debug/com/speakpen/app/BuildConfig.java').read_text()
assert re.search(r'FIXED_SERVICE\s*=\s*true', config)
assert '"'+connection['baseUrl']+'"' in config and '"'+connection['accessToken']+'"' in config, 'Cloud configuration mismatch'
assets = [p for p in (root/'app/src/main/assets').rglob('*') if p.is_file()]
with zipfile.ZipFile(apk) as z:
    for p in assets:
        assert z.read('assets/'+p.relative_to(root/'app/src/main/assets').as_posix()) == p.read_bytes(), 'Asset mismatch'
    html = z.read('assets/home/topic-cylinder.html')
    assert connection['accessToken'].encode() not in html
    assert b'speakpen.invalid' not in html
    for name in ['nunito_regular.ttf', 'nunito_bold.ttf']:
        assert z.read('res/font/'+name) == (root/'app/src/main/res/font'/name).read_bytes(), 'Font resource mismatch'
    dex = b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'))
    assert connection['baseUrl'].encode() in dex and connection['accessToken'].encode() in dex, 'APK cloud configuration missing'
totals = {k:0 for k in ['tests','failures','errors','skipped']}
for p in (out/'test-results').glob('TEST-*.xml'):
    suite = ET.parse(p).getroot()
    for k in totals: totals[k] += int(suite.get(k,0))
assert totals['tests'] >= 202 and not sum(totals[k] for k in ['failures','errors','skipped'])
assert not ET.parse(out/'reports/lint-results-debug.xml').getroot().findall('issue')
browser = json.loads((out/'browser-verification.json').read_text(encoding='utf-8'))
assert len(browser['checks']) == 18 and not browser['pageErrors']
report = dict(versionName='0.6.0-dev10', versionCode=17, apkSha256=sha(apk), apkBytes=apk.stat().st_size,
    signerSha256=signer, sameSignerAsDev09=True, cloudConfigMatches=True, homeHasNoCredential=True,
    matchingSourceInputs=len(sources), matchingPackagedAssets=len(assets), android=totals, lintIssues=0,
    browserChecks=len(browser['checks']), androidDeviceTested=False,
    limits=['No connected Android device or emulator; device root cause unconfirmed',
            'Browser tests do not validate Android WebView compositing or first-frame latency',
            'Background vocabulary loading inspected in source; no Android startup timing measured'])
(out/'build-verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
(out/'SHA256SUMS.txt').write_text(sha(apk)+'  '+apk.name+'\n', encoding='utf-8')
print(json.dumps(report, ensure_ascii=False, indent=2))
