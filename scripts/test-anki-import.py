"""Regression checks for APKG conversion: HTML, bilingual numbering and duplicates."""
import importlib.util
import json
import sqlite3
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('anki_import', Path(__file__).with_name('import-anki-vocabulary.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class AnkiImportTest(unittest.TestCase):
    def test_html_is_inert_and_readable(self):
        self.assertEqual(module.plain('<div>n. 词义&nbsp;一</div><script>steal()</script><br>词义二[sound:x.mp3]'), 'n. 词义 一\n词义二')

    def test_missing_translation_does_not_shift_later_examples(self):
        value = module.examples({'英语例句': '(1) One.<br>(2) Two.<br>(3) Three.', '中文例句': '(1) 一。<br>(3) 三。'})
        self.assertEqual(value, ['One.\n一。', 'Two.', 'Three.\n三。'])

    def test_modern_database_wins_and_duplicate_senses_are_retained(self):
        with tempfile.TemporaryDirectory() as tmp:
            db = Path(tmp) / 'collection.db'
            con = sqlite3.connect(db)
            con.execute('create table col(models text)')
            con.execute('insert into col values (?)', (json.dumps({'1': {'flds': [{'name': x} for x in ['英语单词', '中文释义', '英语例句', '中文例句']]}}),))
            con.execute('create table notes(id integer, mid integer, flds text, tags text)')
            for i, meaning in enumerate(['n. 苹果', 'n. 苹果树']):
                con.execute('insert into notes values (?,1,?,?)', (i, '\x1f'.join(['apple', meaning, '(1) Apple.', '(1) 苹果。']), ''))
            con.commit(); con.close()
            package = Path(tmp) / 'sample.apkg'
            with zipfile.ZipFile(package, 'w') as archive:
                archive.writestr('collection.anki2', b'dummy old-format placeholder')
                archive.writestr('collection.anki21', db.read_bytes())
                archive.writestr('media', '{}')
            entries, stats = module.compile_package(package, 'cet4')
            self.assertEqual(stats['mergedNotes'], 1)
            self.assertEqual(len(entries), 1)
            self.assertIn('苹果树', entries[0]['meaning'])
            self.assertEqual(entries[0]['lexical']['details'][0]['text'], 'Apple.\n苹果。')


if __name__ == '__main__':
    unittest.main()
