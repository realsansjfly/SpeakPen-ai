"""Update server-only provider credentials supplied on stdin, without vendor prefix assumptions."""
import argparse
import os
from pathlib import Path
import tempfile
from urllib.parse import urlsplit


def update_config(path, key, base_url=None, chat_model=None):
    key = key.strip()
    if not 1 <= len(key) <= 4096 or any(not 33 <= ord(c) <= 126 or c == "'" for c in key):
        raise ValueError('Key 不能为空，且不能包含空白、控制字符或单引号。无需 sk- 前缀。')
    # Single quotes are interpreted literally by both systemd EnvironmentFile and bash source.
    updates = {'DASHSCOPE_API_KEY': "'" + key + "'"}
    if base_url is not None:
        base_url = base_url.strip().rstrip('/')
        parsed = urlsplit(base_url)
        if (parsed.scheme != 'https' or not parsed.hostname or parsed.username is not None or
                parsed.password is not None or parsed.query or parsed.fragment or
                not parsed.path.endswith('/v1') or any(c.isspace() or c in "'\\" for c in base_url)):
            raise ValueError('Base URL 必须是无账号、查询参数或片段的 HTTPS /v1 地址。')
        updates['DASHSCOPE_BASE_URL'] = "'" + base_url + "'"
    if chat_model is not None:
        if not chat_model or any(not 33 <= ord(c) <= 126 or c == "'" for c in chat_model):
            raise ValueError('模型 ID 不能为空或含空白／单引号。')
        updates['DASHSCOPE_CHAT_MODEL'] = "'" + chat_model + "'"
    lines = path.read_text(encoding='utf-8').splitlines()
    output = []
    for line in lines:
        name = line.split('=', 1)[0]
        if name not in updates:
            output.append(line)
    output.extend(name + '=' + value for name, value in updates.items())
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=path.parent, delete=False) as f:
            temporary = Path(f.name)
            os.chmod(temporary, 0o600)
            f.write('\n'.join(output) + '\n')
        os.replace(temporary, path)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()


if __name__ == '__main__':
    import sys
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--env-file', type=Path, default=Path('/etc/speakpen/speakpen.env'))
    parser.add_argument('--base-url')
    parser.add_argument('--chat-model')
    args = parser.parse_args()
    try:
        update_config(args.env_file, sys.stdin.read(), args.base_url, args.chat_model)
    except ValueError as error:
        sys.exit(str(error))
