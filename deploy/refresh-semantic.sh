#!/usr/bin/env bash
# Update only retrieval; existing chat, keys, HTTPS and quota files stay in place.
set -euo pipefail
cd /opt/speakpen
exec 9>/var/lock/speakpen-semantic-install.lock
flock -n 9 || { echo 'Semantic update already running'; exit 1; }
snapshot=/opt/speakpen-semantic/snapshots/$(date +%Y%m%d-%H%M%S)
install -d "$snapshot"
cp -a /opt/speakpen-semantic/service.py "$snapshot/service.py"
cp -a /var/lib/speakpen-semantic/index.sqlite "$snapshot/index.sqlite"
cp -a /etc/systemd/system/speakpen-semantic.service "$snapshot/speakpen-semantic.service"
restore() {
  cp -a "$snapshot/service.py" /opt/speakpen-semantic/service.py
  cp -a "$snapshot/index.sqlite" /var/lib/speakpen-semantic/index.sqlite
  cp -a "$snapshot/speakpen-semantic.service" /etc/systemd/system/speakpen-semantic.service
  systemctl daemon-reload
  systemctl restart speakpen-semantic
  echo "Retrieval update failed; restored $snapshot" >&2
}
trap restore ERR
systemctl stop speakpen-semantic
install -m 0644 semantic/service.py /opt/speakpen-semantic/service.py
# Search requests safely use the existing local-word fallback during reindexing.
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=2 nice -n 10 /opt/speakpen-semantic/venv/bin/python /opt/speakpen-semantic/service.py index /opt/speakpen/semantic/assets
chown speakpen:speakpen /var/lib/speakpen-semantic/index.sqlite
install -m 0644 deploy/speakpen-semantic.service /etc/systemd/system/speakpen-semantic.service
systemctl daemon-reload
systemctl restart speakpen-semantic
systemctl is-active --quiet speakpen-semantic
trap - ERR
echo "Retrieval rollback snapshot: $snapshot"
