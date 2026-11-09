"""Compile the owner's APKG notes to inert UTF-8 assets; never execute card HTML.

Uses only Python's standard library. Media and scheduling are left in the APKG.
Run from anywhere: python scripts/import-anki-vocabulary.py
"""
import hashlib
import json
import re
import sqlite3
import tempfile
import zipfile
from collections import Counter
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'app/src/main/assets/vocabulary'
PACKAGES = [('cet4', '四级.apkg', '四级 Anki 词表'),
            ('cet6', '六级.apkg', '六级 Anki 词表'),
            ('ielts', '_IELTS_538.apkg', '雅思同义替换')]


class PlainText(HTMLParser):
    """Retain block boundaries, decode entities, discard scripts/styles/media."""
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.parts = []
        self.hidden = 0

    def handle_starttag(self, tag, attrs):
        if tag in ('script', 'style'):
            self.hidden += 1
        if not self.hidden and tag in ('br', 'p', 'div', 'li', 'hr', 'tr', 'h1', 'h2', 'h3'):
            self.parts.append('\n')

    def handle_endtag(self, tag):
        if tag in ('script', 'style'):
            self.hidden = max(0, self.hidden - 1)
        elif not self.hidden and tag in ('p', 'div', 'li', 'tr', 'h1', 'h2', 'h3', 'br'):
            self.parts.append('\n')

    def handle_data(self, data):
        if not self.hidden:
            self.parts.append(data)


def plain(raw):
    parser = PlainText()
    parser.feed(re.sub(r'\[sound:[^\]]*\]', '', raw))
    lines = [re.sub(r'[\s\u00a0]+', ' ', line).strip() for line in ''.join(parser.parts).splitlines()]
    return '\n'.join(line for line in lines if line)


def numbered(raw):
    """Keep numbered English/Chinese examples aligned; missing numbers stay missing."""
    text = plain(raw)
    matches = list(re.finditer(r'(?:^|\n)\s*[（(](\d+)[）)]\s*', text))
    if not matches:
        return {'': text} if text else {}
    return {m[1]: text[m.end():matches[i + 1].start() if i + 1 < len(matches) else len(text)].strip()
            for i, m in enumerate(matches)}


def examples(fields):
    en, zh = numbered(fields.get('英语例句', '')), numbered(fields.get('中文例句', ''))
    return ['\n'.join(filter(None, [en.get(k), zh.get(k)])) for k in dict.fromkeys([*en, *zh])]


def unique_text(values):
    return '\n\n'.join(dict.fromkeys(v for v in values if v))


def compile_package(path, level):
    with zipfile.ZipFile(path) as archive, tempfile.TemporaryDirectory() as tmp:
        # Modern exports may contain a dummy anki2 database: prefer the real anki21.
        member = 'collection.anki21' if 'collection.anki21' in archive.namelist() else 'collection.anki2'
        data = archive.read(member)
        if not data.startswith(b'SQLite format 3\0'):
            raise ValueError('Unsupported compressed Anki database; export a legacy-compatible APKG.')
        database = Path(tmp) / 'collection.db'
        database.write_bytes(data)
        con = sqlite3.connect(database.as_uri() + '?mode=ro', uri=True)
        try:
            models = json.loads(con.execute('SELECT models FROM col').fetchone()[0])
            rows = con.execute('SELECT id,mid,flds,tags FROM notes ORDER BY id').fetchall()
        finally:
            con.close()
        entries = {}
        coverage = Counter()
        for note_id, model_id, raw, tags in rows:
            names = [f['name'] for f in models[str(model_id)]['flds']]
            fields = dict(zip(names, raw.split('\x1f'), strict=True))
            word = plain(fields.get('英语单词', fields.get('背面', ''))).strip()
            if not word or not re.search('[A-Za-z]', word):
                raise ValueError(f'Note {note_id} has no English expression')
            meaning = plain(fields.get('中文释义', fields.get('正面', '')))
            samples = examples(fields)
            details = []
            if samples:
                details.append({'title': '例句', 'text': '\n\n'.join(samples)})
            for field, title in [('背面1', '同义替换'), ('vocabulary简明', 'Vocabulary 简明'),
                                 ('vocabulary扩展', 'Vocabulary 扩展'), ('柯林斯解释', '柯林斯解释'),
                                 ('Back', '卡片背面 · 词典摘录'), ('笔记', '笔记')]:
                value = plain(fields.get(field, ''))
                if value:
                    details.append({'title': title, 'text': value})
                    coverage[title] += 1
            key = re.sub(r'\s+', ' ', word).lower()
            entry = dict(id='w-' + hashlib.sha256(key.encode()).hexdigest()[:20], expression=word,
                         meaning=meaning, example=samples[0] if samples else '', tags=tags.strip(),
                         original='', line=0, aiMeaning='', aiExample='', aiProvenance='',
                         lexical=dict(phonetic=plain(fields.get('英美音标', '')), levels=level,
                                      collins=fields.get('柯林斯星级', '').count('★'),
                                      source=f'{path.name} · note {note_id}', details=details))
            coverage['释义'] += bool(meaning)
            coverage['例句'] += bool(samples)
            if key in entries:
                old = entries[key]
                old['meaning'] = unique_text([old['meaning'], meaning])
                old['lexical']['source'] += f', {note_id}'
                for detail in details:
                    previous = next((d for d in old['lexical']['details'] if d['title'] == detail['title']), None)
                    if previous:
                        previous['text'] = unique_text([previous['text'], detail['text']])
                    else:
                        old['lexical']['details'].append(detail)
            else:
                entries[key] = entry
        return [entries[k] for k in sorted(entries)], dict(notes=len(rows), entries=len(entries),
                    mergedNotes=len(rows) - len(entries), mediaFiles=len(json.loads(archive.read('media'))),
                    coverage=dict(coverage))


def main():
    manifest = {'schema': 1, 'builtBy': 'scripts/import-anki-vocabulary.py', 'books': []}
    for level, filename, name in PACKAGES:
        path = ROOT / filename
        entries, stats = compile_package(path, level)
        raw = (json.dumps({'entries': entries}, ensure_ascii=False, separators=(',', ':')) + '\n').encode()
        asset = f'anki-{level}.json'
        (DEST / asset).write_bytes(raw)
        source_hash = hashlib.sha256(path.read_bytes()).hexdigest()
        manifest['books'].append(dict(id=f'anki-{level}', level=level, name=name,
            version=f'anki-{source_hash[:16]}-v1', file=asset, fileName=filename,
            sourceSha256=source_hash, sha256=hashlib.sha256(raw).hexdigest(), bytes=len(raw), **stats))
        print(f'{filename}: {stats["notes"]} notes -> {len(entries)} entries; {len(raw):,} bytes')
    (DEST / 'anki-manifest.json').write_bytes((json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode())


if __name__ == '__main__':
    main()
