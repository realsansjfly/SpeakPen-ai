from pathlib import Path
import zipfile, json
workspace=Path(__file__).resolve().parents[1]
values={}
for line in (workspace/'server/.env').read_text(encoding='utf-8-sig').splitlines():
 if '=' in line and not line.lstrip().startswith('#'):
  key,value=line.split('=',1);values[key]=value
secret=values['DASHSCOPE_API_KEY']
needles=[secret.encode(),secret.encode('utf-16le')]
found=[]
for directory in ['app/src','server/public','docs','scripts','design']:
 for path in (workspace/directory).rglob('*'):
  if path.is_file() and path.stat().st_size<8*1024*1024:
   data=path.read_bytes()
   if any(needle in data for needle in needles):found.append(str(path.relative_to(workspace)))
with zipfile.ZipFile(workspace/'deliverables/SpeakPen-0.4.0-debug.apk') as apk:
 dex=b''.join(apk.read(name) for name in apk.namelist() if name.endswith('.dex'))
 assert not any(needle in dex for needle in needles),'Provider credential found in APK'
 connection=json.loads((workspace/'.local/connection.json').read_text(encoding='utf-8-sig'))
 assert connection['baseUrl'].encode() in dex,'Service URL not embedded'
 assert connection['accessToken'].encode() in dex,'Local service pairing missing'
 for name in apk.namelist():
  if any(needle in apk.read(name) for needle in needles):found.append('APK:'+name)
assert not found,'Provider credential outside .env: '+', '.join(found)
print('CREDENTIAL_SCAN_PASS: provider key absent from APK, source, website and documentation; service pairing embedded')


