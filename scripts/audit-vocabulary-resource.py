"""Read fixed upstream data only; never imports or executes upstream code."""
import base64, csv, hashlib, io, json, pathlib, urllib.request

root = pathlib.Path(__file__).resolve().parents[1]
audit = root / 'docs/research/vocabulary-2026-10-31'
audit.mkdir(parents=True, exist_ok=True)
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
def get(path, raw=False):
    request = urllib.request.Request('https://api.github.com/repos/skywind3000/ECDICT/' + path,
        headers={'User-Agent': 'SpeakPen-resource-audit', 'Accept': 'application/vnd.github.raw+json' if raw else 'application/vnd.github+json'})
    with opener.open(request, timeout=45) as response:
        data = response.read()
    return data if raw else json.loads(data)

commit = 'ab22ad36302cafc6349a050e2b60894855208055'
evidence = {'repository': 'https://github.com/skywind3000/ECDICT', 'commit': commit, 'files': {}}
for name in ['LICENSE', 'README.md', 'ecdict.csv']:
    data = get('contents/' + name + '?ref=' + commit, raw=True)
    if data.startswith(b'{"'):
        value = json.loads(data)
        data = base64.b64decode(value['content'])
    evidence['files'][name] = {'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)}
    if name != 'ecdict.csv':
        (audit / name.replace('README.md', 'UPSTREAM-README.md')).write_bytes(data)
    else:
        # Only word forms and community exam tags are distributed, no definitions,
        # phonetics, examples, corpus rankings, or dictionary-specific annotations.
        rows = list(csv.DictReader(io.StringIO(data.decode('utf-8-sig'))))
        for tag, title in [('cet4','四级'), ('cet6','六级'), ('ielts','雅思')]:
            tagged = [r['word'].strip() for r in rows if tag in r['tag'].split()]
            words = sorted(set(w for w in tagged if w and len(w) <= 120 and all(c.isascii() and (c.isalpha() or c in " '-.") for c in w)))
            asset = root / 'app/src/main/assets/vocabulary' / (tag + '.txt')
            asset.parent.mkdir(parents=True, exist_ok=True)
            asset.write_text('\n'.join(words) + '\n', encoding='utf-8')
            evidence[tag] = {'name': title + ' · 社区词表', 'taggedRows': len(tagged), 'entries': len(words),
                'excluded': len(tagged)-len(words), 'sha256': hashlib.sha256(asset.read_bytes()).hexdigest()}
        evidence['csvRows'] = len(rows)
(audit / 'audit.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding='utf-8')
assets = root / 'app/src/main/assets/vocabulary'
(assets / 'ECDICT-LICENSE.txt').write_bytes((audit / 'LICENSE').read_bytes())
(assets / 'manifest.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(evidence, ensure_ascii=False, indent=2))
