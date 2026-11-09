"""Extract supplied DOCX as data, retaining complete dialogue and explicit provenance."""
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile

source = Path(r'C:\Users\Realsansjfly\Downloads\英语口语场景对话示例.docx')
with zipfile.ZipFile(source) as archive:
    root = ET.fromstring(archive.read('word/document.xml'))
ns = {'w': 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
lines = [''.join(t.text or '' for t in p.findall('.//w:t', ns)).strip() for p in root.findall('.//w:p', ns)]
keys = iter(['cafe', 'shopping', 'directions', 'hotel', 'campus'])
scenes = {}
current = None
for line in lines:
    if re.match(r'^[1-5]\. ', line):
        current = next(keys)
        scenes[current] = {'title': line[3:], 'dialogue': []}
    elif current and ':' in line:
        scenes[current]['dialogue'].append(line)
output = Path('app/src/main/assets/scenarios/material.json')
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps({'source': source.name, 'sha256': hashlib.sha256(source.read_bytes()).hexdigest(), 'scenes': scenes}, ensure_ascii=False, indent=2), encoding='utf8')
print({key: len(scene['dialogue']) for key, scene in scenes.items()})
