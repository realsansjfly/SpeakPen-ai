"""Verify a cloud APK against its build mirror; output only redacted facts."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('mirror', type=Path)
parser.add_argument('apk', type=Path)
parser.add_argument('--previous', required=True, type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
mirror, apk = args.mirror.resolve(), args.apk.resolve()
out = apk.parent
bt = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/build-tools/35.0.0'
env = dict(os.environ)
if Path('C:/EmojiJdk17/bin/java.exe').exists():
    env['JAVA_HOME'] = 'C:/EmojiJdk17'

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def run(arguments):
    result = subprocess.run(arguments, env=env, capture_output=True, text=True, encoding='utf-8', errors='replace')
    assert result.returncode == 0, 'Android verification tool failed: ' + str(arguments[0])
    return result.stdout

gradle = (root / 'app/build.gradle.kts').read_text(encoding='utf-8-sig')
version = re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1)
code = int(re.search(r'versionCode\s*=\s*(\d+)', gradle).group(1))
with tempfile.TemporaryDirectory(prefix='SpeakPen-verify-') as directory:
    copied = Path(directory) / 'current.apk'
    previous = Path(directory) / 'previous.apk'
    shutil.copyfile(apk, copied)
    shutil.copyfile(args.previous, previous)
    identity = run([str(bt / 'aapt2.exe'), 'dump', 'badging', str(copied)]).splitlines()[0]
    assert "name='com.speakpen.app'" in identity
    assert f"versionCode='{code}'" in identity and f"versionName='{version}'" in identity
    def cert(path):
        output = run([str(bt / 'apksigner.bat'), 'verify', '--print-certs', str(path)])
        return re.search(r'certificate SHA-256 digest: (\w+)', output).group(1)
    signer = cert(copied)
    assert signer == cert(previous), 'Signing certificate changed; upgrade would fail'

connection = json.loads((root / '.local/cloud-connection.json').read_text(encoding='utf-8-sig'))
config = (mirror / 'app/build/generated/source/buildConfig/debug/com/speakpen/app/BuildConfig.java').read_text()
assert re.search(r'FIXED_SERVICE\s*=\s*true', config), 'APK must be built with -Cloud'
assert '"' + connection['baseUrl'].rstrip('/') + '"' in config, 'Cloud URL mismatch'
assert '"' + connection['accessToken'] + '"' in config, 'Cloud app token mismatch'
sources = [p for p in (root / 'app/src').rglob('*') if p.is_file()] + [root / 'app/build.gradle.kts']
assert all(sha(path) == sha(mirror / path.relative_to(root)) for path in sources), 'Build mirror differs from source'
assets = [p for p in (root / 'app/src/main/assets').rglob('*') if p.is_file()]
with zipfile.ZipFile(apk) as archive:
    dex = b''.join(archive.read(name) for name in archive.namelist() if name.endswith('.dex'))
    assert connection['baseUrl'].rstrip('/').encode() in dex, 'APK has no cloud URL'
    assert connection['accessToken'].encode() in dex, 'APK has no cloud app token'
    for path in assets:
        assert archive.read('assets/' + path.relative_to(root / 'app/src/main/assets').as_posix()) == path.read_bytes()
    assert connection['accessToken'].encode() not in archive.read('assets/home/topic-cylinder.html')

totals = dict(tests=0, failures=0, errors=0, skipped=0)
for path in (out / 'test-results').glob('TEST-*.xml'):
    suite = ET.parse(path).getroot()
    for key in totals:
        totals[key] += int(suite.get(key, 0))
assert totals['tests'] > 0 and not sum(totals[k] for k in ['failures', 'errors', 'skipped'])
assert not ET.parse(out / 'reports/lint-results-debug.xml').getroot().findall('issue')
report = dict(versionName=version, versionCode=code, apkSha256=sha(apk), apkBytes=apk.stat().st_size,
              signerSha256=signer, sameSignerAsPrevious=True, fixedCloud=True, cloudConfigMatches=True,
              packagedCloudConfigVerified=True, matchingSourceInputs=len(sources), matchingPackagedAssets=len(assets),
              android=totals, lintIssues=0, androidDeviceTested=False)
(out / 'build-verification.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
(out / 'SHA256SUMS.txt').write_text(sha(apk) + '  ' + apk.name + '\n', encoding='utf-8')
print(json.dumps(report, indent=2))
