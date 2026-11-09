"""Fetch public model artifacts on the workstation when the cloud egress is limited."""
import hashlib
import json
from pathlib import Path
import urllib.request

target = Path('.local/semantic-model')
target.mkdir(parents=True, exist_ok=True)
records = []
expected = {'tokenizer.json': '0b44a9d7b51c3c62626640cda0e2c2f70fdacdc25bbbd68038369d14ebdf4c39',
    'model_quantized.onnx': 'f80102d3f2a1229f387d3c81909990d8945513e347b0eab049f7de3c6f98c193'}
for filename in ['tokenizer.json', 'onnx/model_quantized.onnx']:
    destination = target / Path(filename).name
    if not destination.exists():
        with urllib.request.urlopen('https://hf-mirror.com/Xenova/multilingual-e5-small/resolve/main/' + filename + '?download=true', timeout=60) as response:
            revision = response.headers.get('x-repo-commit', '')
            with destination.with_suffix(destination.suffix + '.part').open('wb') as output:
                while block := response.read(1024 * 1024):
                    output.write(block)
            destination.with_suffix(destination.suffix + '.part').replace(destination)
    else:
        revision = 'existing'
    digest = hashlib.sha256(destination.read_bytes()).hexdigest()
    if digest != expected[destination.name]:
        raise ValueError('Model checksum mismatch: ' + destination.name)
    records.append({'file': destination.name, 'sha256': digest, 'bytes': destination.stat().st_size, 'revision': revision})
    print(destination.name, destination.stat().st_size, flush=True)
(target / 'provenance.json').write_text(json.dumps({'repository': 'Xenova/multilingual-e5-small', 'artifacts': records}, indent=2))
