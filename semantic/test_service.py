import json
from pathlib import Path
import tempfile
import unittest

import numpy as np
from service import Index, rerank, validate, build, create_server


class FakeEmbedder:
    def encode(self, texts):
        return np.array([[1., 0., 0.] for _ in texts], dtype=np.float32)


class RetrievalTest(unittest.TestCase):
    def test_http_auth_limits_and_failure(self):
        import threading
        import urllib.request
        import urllib.error
        class MockIndex:
            version = 'test-version'
            rows = [1]
            def search(self, value):
                validate(value)
                return {'version': self.version, 'candidates': []}
        server = create_server(MockIndex(), 'a' * 64, 0)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base = f'http://127.0.0.1:{server.server_port}'
        def request(path, data=None, auth=True):
            headers = {'Authorization': 'Bearer ' + 'a' * 64} if auth else {}
            return urllib.request.urlopen(urllib.request.Request(base + path, data=data, headers=headers), timeout=2)
        try:
            with self.assertRaises(urllib.error.HTTPError) as error:
                request('/health', auth=False)
            self.assertEqual(error.exception.code, 401)
            with request('/health') as response:
                self.assertTrue(json.load(response)['ready'])
            with request('/search', json.dumps({'query': 'hotel', 'count': 4}).encode()) as response:
                self.assertEqual(json.load(response)['version'], 'test-version')
            for body, status in [(b'{}', 400), (b'x' * 17000, 413), (b'not json', 400)]:
                with self.assertRaises(urllib.error.HTTPError) as error:
                    request('/search', body)
                self.assertEqual(error.exception.code, status)
            with self.assertRaises(urllib.error.HTTPError) as error:
                request('/other', b'{}')
            self.assertEqual(error.exception.code, 404)
        finally:
            server.shutdown(); server.server_close(); thread.join()

    def test_limits(self):
        self.assertEqual(validate({'query': '找旅馆', 'count': 4, 'books': ['anki-cet4']})[:2], ('找旅馆', 4))
        for value in [None, {}, {'query': ''}, {'query': 'x' * 3001}, {'query': 'x', 'count': True},
                      {'query': 'x', 'count': 5}, {'query': 'x', 'books': ['arbitrary']}, {'query': 'x', 'level': 'EXPENSIVE'}]:
            with self.assertRaises(ValueError):
                validate(value)

    def test_rank_book_keyword_level_and_deduplicate(self):
        rows = [{'id': '1', 'book': 'anki-cet4', 'expression': 'reservation', 'meaning': '预订'},
                {'id': '2', 'book': 'anki-cet6', 'expression': 'reservation', 'meaning': '预订'},
                {'id': '3', 'book': 'anki-cet4', 'expression': 'apple', 'meaning': '苹果'}]
        ranked = rerank(rows, [.8, .8, .81], 'reservation 酒店预订', 4, ['anki-cet4'], 'SIMPLE')
        self.assertEqual([r['id'] for r in ranked], ['1', '3'])
        self.assertEqual(len(rerank(rows, [.8] * 3, 'x', 4, [], 'DAILY')), 2)

    def test_index_atomic_checksum_and_lookup(self):
        import hashlib
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            asset = root / 'words.json'
            asset.write_text(json.dumps({'entries': [{'id': '1', 'expression': 'reservation', 'meaning': '预订'}]}))
            manifest = {'books': [{'id': 'anki-cet4', 'file': asset.name, 'sha256': hashlib.sha256(asset.read_bytes()).hexdigest()}]}
            (root / 'anki-manifest.json').write_text(json.dumps(manifest))
            version = build(root, root, FakeEmbedder())
            index = Index(root, FakeEmbedder())
            self.assertEqual(index.search({'query': '找住的地方', 'count': 4, 'books': ['anki-cet4']})['candidates'][0]['expression'], 'reservation')
            self.assertEqual(index.version, version)
            asset.write_text('changed')
            with self.assertRaises(ValueError):
                build(root, root, FakeEmbedder())
            self.assertEqual(Index(root, FakeEmbedder()).version, version)

    def test_busy_worker_does_not_run_parallel_inference(self):
        import threading
        import urllib.request
        import urllib.error
        entered, release = threading.Event(), threading.Event()
        class SlowIndex:
            version = 'slow'
            rows = []
            def search(self, value):
                entered.set()
                release.wait(2)
                return {'version': self.version, 'candidates': []}
        server = create_server(SlowIndex(), 'a' * 64, 0)
        serving = threading.Thread(target=server.serve_forever, daemon=True)
        serving.start()
        def query():
            req = urllib.request.Request(f'http://127.0.0.1:{server.server_port}/search', data=b'{"query":"hotel"}',
                headers={'Authorization': 'Bearer ' + 'a' * 64})
            return urllib.request.urlopen(req, timeout=3)
        responses = []
        def first_query():
            with query() as response:
                responses.append(response.status)
        first = threading.Thread(target=first_query)
        first.start()
        try:
            self.assertTrue(entered.wait(1))
            with self.assertRaises(urllib.error.HTTPError) as error:
                query()
            self.assertEqual(error.exception.code, 503)
        finally:
            release.set(); first.join()
            server.shutdown(); server.server_close(); serving.join()
        self.assertEqual(responses, [200])

    def test_model_pipeline_version_invalidates_existing_index(self):
        import hashlib
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            asset = root / 'words.json'
            asset.write_text(json.dumps({'entries': [{'id': '1', 'expression': 'hotel', 'meaning': '旅馆'}]}))
            manifest = {'books': [{'id': 'anki-cet4', 'file': asset.name, 'sha256': hashlib.sha256(asset.read_bytes()).hexdigest()}]}
            (root / 'anki-manifest.json').write_text(json.dumps(manifest))
            first = build(root, root, FakeEmbedder())
            with patch('service.PIPELINE_VERSION', 'a-new-embedding-model'):
                second = build(root, root, FakeEmbedder())
            self.assertNotEqual(first, second)
            self.assertEqual(Index(root, FakeEmbedder()).version, second)


if __name__ == '__main__':
    unittest.main()
