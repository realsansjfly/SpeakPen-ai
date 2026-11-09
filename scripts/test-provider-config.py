import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('provider_config', Path(__file__).resolve().parents[1] / 'deploy/configure-provider.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
tts_spec = importlib.util.spec_from_file_location('tts_config', Path(__file__).resolve().parents[1] / 'deploy/configure-tts.py')
tts_module = importlib.util.module_from_spec(tts_spec)
tts_spec.loader.exec_module(tts_module)


class ProviderConfigTest(unittest.TestCase):
    def test_non_sk_key_and_custom_url_replace_duplicates_but_keep_service_token(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / 'service.env'
            p.write_text('DASHSCOPE_API_KEY=old\nDASHSCOPE_API_KEY=stale\nSERVICE_ACCESS_TOKEN=keep\nDASHSCOPE_BASE_URL=https://old.example/v1\n', encoding='utf-8')
            module.update_config(p, 'gateway.test-token+/=', 'https://maas.qianwenaiapi.com/compatible-mode/v1/')
            raw = p.read_text(encoding='utf-8')
            self.assertEqual(raw.count('DASHSCOPE_API_KEY='), 1)
            self.assertIn("DASHSCOPE_API_KEY='gateway.test-token+/='", raw)
            self.assertIn("DASHSCOPE_BASE_URL='https://maas.qianwenaiapi.com/compatible-mode/v1'", raw)
            self.assertIn('SERVICE_ACCESS_TOKEN=keep', raw)

    def test_invalid_key_or_url_leaves_original_unchanged(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / 'service.env'
            p.write_text('SERVICE_ACCESS_TOKEN=keep\n', encoding='utf-8')
            for key in ['', 'bad\nkey', 'bad key', "bad'key"]:
                with self.assertRaises(ValueError):
                    module.update_config(p, key)
            for url in ['http://example.com/v1', 'https://a:b@example.com/v1', 'https://example.com/v1?key=x']:
                with self.assertRaises(ValueError):
                    module.update_config(p, 'valid.test', url)
            self.assertEqual(p.read_text(encoding='utf-8'), 'SERVICE_ACCESS_TOKEN=keep\n')

    def test_shell_characters_are_quoted_literally(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / 'service.env'; p.write_text('', encoding='utf-8')
            module.update_config(p, 'fixture$VAR`command`', chat_model='test-model')
            self.assertIn("DASHSCOPE_API_KEY='fixture$VAR`command`'", p.read_text(encoding='utf-8'))

    def test_independent_tts_key_preserves_chat_credentials_and_replaces_duplicates(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / 'service.env'
            p.write_text("DASHSCOPE_API_KEY='keep-chat'\nDASHSCOPE_TTS_API_KEY=old\nDASHSCOPE_TTS_API_KEY=stale\n", encoding='utf-8')
            tts_module.update_tts_config(p, 'official.tts-key+/=', voice='Cherry')
            raw = p.read_text(encoding='utf-8')
            self.assertIn("DASHSCOPE_API_KEY='keep-chat'", raw)
            self.assertEqual(raw.count('DASHSCOPE_TTS_API_KEY='), 1)
            self.assertIn("DASHSCOPE_TTS_API_KEY='official.tts-key+/='", raw)
            self.assertIn("DASHSCOPE_TTS_MODEL='qwen3-tts-flash'", raw)
            self.assertIn("DASHSCOPE_TTS_VOICE='Cherry'", raw)

    def test_invalid_tts_secret_or_voice_leaves_original_unchanged(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / 'service.env'; p.write_text('DASHSCOPE_API_KEY=keep\n', encoding='utf-8')
            for key in ['', 'bad key', "bad'key", 'bad\nkey']:
                with self.assertRaises(ValueError):
                    tts_module.update_tts_config(p, key)
            with self.assertRaises(ValueError):
                tts_module.update_tts_config(p, 'valid-key', voice='bad voice')
            self.assertEqual(p.read_text(encoding='utf-8'), 'DASHSCOPE_API_KEY=keep\n')


if __name__ == '__main__':
    unittest.main()
