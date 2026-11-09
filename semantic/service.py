"""Small bilingual retrieval worker. Loopback only; no paid inference or user history storage."""
import hashlib
import hmac
import json
import os
import re
import sqlite3
import threading
from contextlib import closing
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

import numpy as np

ROOT = Path(os.environ.get('SEMANTIC_DATA', '/var/lib/speakpen-semantic'))
MODEL = Path(os.environ.get('SEMANTIC_MODEL', '/opt/speakpen-semantic/model'))
MODEL_FILES = {
    'tokenizer.json': '0b44a9d7b51c3c62626640cda0e2c2f70fdacdc25bbbd68038369d14ebdf4c39',
    'model_quantized.onnx': 'f80102d3f2a1229f387d3c81909990d8945513e347b0eab049f7de3c6f98c193',
}
PIPELINE_VERSION = 'e5-small-int8-mean-192-v1:' + ':'.join(MODEL_FILES.values())
DOMAIN_HINTS = [
    ('预订 预约 reservation booking', 'reservation reserve book appointment'),
    ('酒店 旅馆 住宿 hotel accommodation', 'hotel accommodation reception room guest'),
    ('前台 reception', 'reception receptionist'),
    ('果汁 饮料 juice beverage', 'juice beverage drink orange apple'),
    ('问路 路线 directions route', 'route direction intersection pedestrian avenue'),
    ('衣服 尺码 衣物 clothes size jumper', 'clothes size clothing garment fit'),
    ('同学 校园 campus classmate', 'campus classmate lecture university'),
]


def domain_words(query):
    lower = query.lower()
    tokens = set(re.findall(r'[a-z]+', lower))
    words = set()
    for keys, values in DOMAIN_HINTS:
        if any(key in lower if not key.isascii() else key in tokens for key in keys.split()):
            words.update(values.split())
    return words


class Embedder:
    def __init__(self, model=MODEL):
        import onnxruntime as ort
        from tokenizers import Tokenizer
        for filename, digest in MODEL_FILES.items():
            if hashlib.sha256((model / filename).read_bytes()).hexdigest() != digest:
                raise ValueError('model checksum mismatch')
        options = ort.SessionOptions()
        # Dynamic passage lengths otherwise leave hundreds of MB in ORT's arena.
        options.enable_cpu_mem_arena = False
        options.enable_mem_pattern = False
        options.intra_op_num_threads = 2
        options.inter_op_num_threads = 1
        self.session = ort.InferenceSession(str(model / 'model_quantized.onnx'), options, providers=['CPUExecutionProvider'])
        self.tokenizer = Tokenizer.from_file(str(model / 'tokenizer.json'))
        self.tokenizer.enable_truncation(max_length=192)
        self.tokenizer.enable_padding(pad_id=1, pad_token='<pad>')

    def encode(self, texts):
        encoded = self.tokenizer.encode_batch(texts)
        ids = np.array([x.ids for x in encoded], dtype=np.int64)
        mask = np.array([x.attention_mask for x in encoded], dtype=np.int64)
        feed = {'input_ids': ids, 'attention_mask': mask}
        if any(x.name == 'token_type_ids' for x in self.session.get_inputs()):
            feed['token_type_ids'] = np.zeros_like(ids)
        hidden = self.session.run(None, feed)[0]
        vectors = (hidden * mask[:, :, None]).sum(1) / np.maximum(mask.sum(1)[:, None], 1)
        return (vectors / np.maximum(np.linalg.norm(vectors, axis=1, keepdims=True), 1e-9)).astype(np.float32)


def validate(value):
    if not isinstance(value, dict):
        raise ValueError('object required')
    query, count = value.get('query'), value.get('count', 8)
    if not isinstance(query, str) or not query.strip() or len(query) > 3000:
        raise ValueError('query must contain 1-3000 characters')
    if type(count) is not int or count not in (4, 8, 12):
        raise ValueError('count must be 4, 8 or 12')
    books = value.get('books', [])
    if not isinstance(books, list) or len(books) > 3 or any(b not in ('anki-cet4', 'anki-cet6', 'anki-ielts') for b in books):
        raise ValueError('unknown book')
    level = value.get('level', 'DAILY')
    if level not in ('SIMPLE', 'DAILY', 'DEEP'):
        raise ValueError('unknown level')
    return query.strip(), count, books, level


def rerank(rows, similarities, query, count, books, level):
    tokens = set(re.findall(r'[a-z]{3,}', query.lower()))
    related = domain_words(query)
    ranked = []
    for row, similarity in zip(rows, similarities):
        if books and row['book'] not in books:
            continue
        lexical = bool(tokens.intersection(re.findall(r'[a-z]{3,}', row['expression'].lower())))
        meaning_hit = any(len(s) >= 2 and s in query for s in re.split(r'[；，;、\s]+', row['meaning']))
        level_bonus = .025 if (level == 'SIMPLE' and row['book'] == 'anki-cet4') or (level == 'DEEP' and row['book'] != 'anki-cet4') else 0
        score = float(similarity) + .06 * lexical + .04 * meaning_hit + .09 * (row['expression'].lower() in related) + level_bonus
        ranked.append((score, row))
    ranked.sort(key=lambda item: (-item[0], item[1]['expression'], item[1]['id']))
    result, seen = [], set()
    for score, row in ranked:
        if row['expression'].lower() in seen:
            continue
        seen.add(row['expression'].lower())
        result.append({**row, 'score': round(score, 5)})
        if len(result) == count:
            break
    return result


class Index:
    def __init__(self, root=ROOT, embedder=None):
        self.embedder = embedder or Embedder()
        with closing(sqlite3.connect(root / 'index.sqlite')) as db:
            self.version = db.execute('select version from metadata').fetchone()[0]
            records = db.execute('select id, book, expression, meaning, vector from entries order by rowid').fetchall()
        self.rows = [dict(zip(('id', 'book', 'expression', 'meaning'), row[:4])) for row in records]
        self.matrix = np.stack([np.frombuffer(row[4], dtype=np.float32) for row in records])

    def search(self, value):
        query, count, books, level = validate(value)
        vector = self.embedder.encode(['query: ' + query])[0]
        similarity = self.matrix @ vector
        # Limit semantic recall before keyword/level reranking; book filtering happens before top-k.
        eligible = [i for i, row in enumerate(self.rows) if not books or row['book'] in books]
        top = sorted(eligible, key=lambda i: -float(similarity[i]))[:max(64, count * 8)]
        # Bilingual keyword candidates complement vector recall, especially for multi-sense Anki cards.
        hints = domain_words(query)
        top = list(dict.fromkeys(top + [i for i in eligible if self.rows[i]['expression'].lower() in hints]))
        return {'version': self.version, 'candidates': rerank([self.rows[i] for i in top], similarity[top], query, count, books, level)}


def build(assets, root=ROOT, embedder=None):
    root.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((assets / 'anki-manifest.json').read_text(encoding='utf8'))
    version = hashlib.sha256((PIPELINE_VERSION + json.dumps(manifest, sort_keys=True)).encode()).hexdigest()[:16]
    for book in manifest['books']:
        if hashlib.sha256((assets / book['file']).read_bytes()).hexdigest() != book['sha256']:
            raise ValueError('asset checksum mismatch')
    if (root / 'index.sqlite').exists():
        with closing(sqlite3.connect(root / 'index.sqlite')) as existing:
            if existing.execute('select version from metadata').fetchone()[0] == version:
                print('index already current', version, flush=True)
                return version
    embedder = embedder or Embedder()
    target = root / 'index.new.sqlite'
    if target.exists():
        target.unlink()
    with closing(sqlite3.connect(target)) as db, db:
        db.execute('create table metadata(version text)')
        db.execute('insert into metadata values (?)', (version,))
        db.execute('create table entries(id text, book text, expression text, meaning text, vector blob)')
        for book in manifest['books']:
            source = assets / book['file']
            if hashlib.sha256(source.read_bytes()).hexdigest() != book['sha256']:
                raise ValueError('asset checksum mismatch')
            entries = json.loads(source.read_text(encoding='utf8'))['entries']
            for start in range(0, len(entries), 8):
                batch = entries[start:start + 8]
                vectors = embedder.encode(['passage: ' + e['expression'] + ' ' + e['meaning'][:500] + ' ' + e.get('example', '')[:200] for e in batch])
                db.executemany('insert into entries values (?,?,?,?,?)', [(e['id'], book['id'], e['expression'], e['meaning'][:500], vector.tobytes()) for e, vector in zip(batch, vectors)])
            db.commit()
            print('indexed', book['id'], len(entries), flush=True)
    os.replace(target, root / 'index.sqlite')
    return version


def create_server(index, token, port=8791):
    gate = threading.BoundedSemaphore(1)
    if len(token) != 64:
        raise ValueError('service token required')

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def respond(self, status, value):
            data = json.dumps(value, ensure_ascii=False).encode()
            self.send_response(status)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self):
            if not hmac.compare_digest(self.headers.get('Authorization', ''), 'Bearer ' + token):
                return self.respond(401, {'error': 'unauthorized'})
            if self.path != '/health':
                return self.respond(404, {'error': 'not_found'})
            self.respond(200, {'ready': True, 'version': index.version, 'entries': len(index.rows)})

        def do_POST(self):
            self.connection.settimeout(4)
            if not hmac.compare_digest(self.headers.get('Authorization', ''), 'Bearer ' + token):
                return self.respond(401, {'error': 'unauthorized'})
            if self.path != '/search':
                return self.respond(404, {'error': 'not_found'})
            if not gate.acquire(blocking=False):
                return self.respond(503, {'error': 'busy'})
            try:
                length = int(self.headers.get('Content-Length', '0'))
                if length not in range(1, 16385):
                    return self.respond(413, {'error': 'body_limit'})
                self.respond(200, index.search(json.loads(self.rfile.read(length))))
            except (ValueError, KeyError, TypeError):
                self.respond(400, {'error': 'invalid_input'})
            except Exception:
                self.respond(503, {'error': 'unavailable'})
            finally:
                gate.release()

    return ThreadingHTTPServer(('127.0.0.1', port), Handler)


if __name__ == '__main__':
    import sys
    if len(sys.argv) > 1 and sys.argv[1] == 'index':
        print(build(Path(sys.argv[2])))
    else:
        create_server(Index(), os.environ.get('SERVICE_ACCESS_TOKEN', '')).serve_forever()
