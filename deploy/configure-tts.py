"""Atomically save an independent official DashScope TTS key from stdin."""
import argparse
import os
from pathlib import Path
import tempfile


def update_tts_config(path: Path, key: str, model: str = 'qwen3-tts-flash', voice: str = 'Cherry'):
    key = key.strip()
    if not 1 <= len(key) <= 4096 or any(not 33 <= ord(c) <= 126 or c == "'" for c in key):
        raise ValueError('Key 不能为空，且不能包含空白、控制字符或单引号。')
    for label, value in [('模型 ID', model), ('音色', voice)]:
        if not value or len(value) > 120 or any(not 33 <= ord(c) <= 126 or c == "'" for c in value):
            raise ValueError(f'{label}不能为空或包含空白／单引号。')
    updates = {
        'DASHSCOPE_TTS_API_KEY': "'" + key + "'",
        'DASHSCOPE_TTS_MODEL': "'" + model + "'",
        'DASHSCOPE_TTS_VOICE': "'" + voice + "'",
    }
    lines = path.read_text(encoding='utf-8').splitlines()
    output = [line for line in lines if line.split('=', 1)[0] not in updates]
    output.extend(name + '=' + value for name, value in updates.items())
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=path.parent, delete=False) as stream:
            temporary = Path(stream.name)
            os.chmod(temporary, 0o600)
            stream.write('\n'.join(output) + '\n')
        os.replace(temporary, path)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()


if __name__ == '__main__':
    import sys
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--env-file', type=Path, default=Path('/etc/speakpen/speakpen.env'))
    parser.add_argument('--model', default='qwen3-tts-flash')
    parser.add_argument('--voice', default='Cherry')
    args = parser.parse_args()
    try:
        update_tts_config(args.env_file, sys.stdin.read(), args.model, args.voice)
    except ValueError as error:
        sys.exit(str(error))
