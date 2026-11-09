#!/usr/bin/env bash
set -euo pipefail
cd /opt/speakpen
exec 9>/var/lock/speakpen-semantic-install.lock
flock -n 9 || { echo 'Semantic installation already running'; exit 1; }
apt-get install -y python3-venv
install -d /opt/speakpen-semantic/model /var/lib/speakpen-semantic
python3 -m venv /opt/speakpen-semantic/venv
/opt/speakpen-semantic/venv/bin/pip install --disable-pip-version-check -r semantic/requirements.txt
install -m 0644 semantic/service.py /opt/speakpen-semantic/service.py
# Quantized multilingual E5 small, MIT. No PyTorch, GPU or vector database daemon.
for item in tokenizer.json onnx/model_quantized.onnx; do
  target=/opt/speakpen-semantic/model/$(basename "$item")
  if [[ ! -s "$target" ]]; then
    curl --silent --show-error --fail --location --retry 3 --connect-timeout 15 --max-time 900 "https://hf-mirror.com/Xenova/multilingual-e5-small/resolve/main/$item" -o "$target.part"
    mv "$target.part" "$target"
  fi
done
OPENBLAS_NUM_THREADS=1 /opt/speakpen-semantic/venv/bin/python /opt/speakpen-semantic/service.py index /opt/speakpen/semantic/assets
chown -R speakpen:speakpen /var/lib/speakpen-semantic
install -m 0644 deploy/speakpen-semantic.service /etc/systemd/system/speakpen-semantic.service
systemctl daemon-reload
systemctl enable --now speakpen-semantic
