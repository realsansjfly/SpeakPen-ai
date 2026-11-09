#!/usr/bin/env bash
# Run as root after extracting the deployment archive into /opt/speakpen.
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo '请用 sudo bash deploy/install-ubuntu.sh'; exit 1; }
cd /opt/speakpen
[[ -f server/src.mjs && -f deploy/speakpen.service ]] || { echo '请先完整解压到 /opt/speakpen'; exit 1; }
source /etc/os-release
[[ ${ID:-} == ubuntu && ${VERSION_ID:-} == 24.04 ]] || { echo '此脚本适用 Ubuntu 24.04；其他系统请按文档手动配置。'; exit 1; }
apt-get update
apt-get install -y ca-certificates curl gnupg nginx certbot python3-certbot-nginx openssl nano
if ! command -v node >/dev/null || ! node -e 'process.exit(Number(process.versions.node.split(".")[0]) >= 22 ? 0 : 1)'; then
    install -d -m 0755 /etc/apt/keyrings
    curl --fail --show-error --silent --location https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key -o /tmp/speakpen-nodesource.key
    gpg --batch --yes --dearmor -o /etc/apt/keyrings/nodesource.gpg /tmp/speakpen-nodesource.key
    chmod 0644 /etc/apt/keyrings/nodesource.gpg
    printf '%s\n' 'deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_22.x nodistro main' > /etc/apt/sources.list.d/speakpen-node.list
    apt-get update
    apt-get install -y nodejs
fi
/usr/bin/node -e 'if (Number(process.versions.node.split(".")[0]) < 22) process.exit(1)'
id -u speakpen >/dev/null 2>&1 || useradd --system --home-dir /var/lib/speakpen --shell /usr/sbin/nologin speakpen
install -d -m 0700 /etc/speakpen
if [[ ! -f /etc/speakpen/speakpen.env ]]; then
    umask 077
    token=$(openssl rand -hex 32)
    cat > /etc/speakpen/speakpen.env <<EOF
HOST=127.0.0.1
PORT=8787
REQUIRE_AUTH=true
PROVIDER_PRIORITY=dashscope
DASHSCOPE_API_KEY=
DASHSCOPE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
DASHSCOPE_CHAT_MODEL=qwen3.7-flash-2026-07-15
DASHSCOPE_ASR_MODEL=qwen3-asr-flash
DASHSCOPE_TTS_API_KEY=
DASHSCOPE_TTS_MODEL=qwen3-tts-flash
DASHSCOPE_TTS_VOICE=Cherry
SERVICE_ACCESS_TOKEN=$token
MAX_REQUESTS_PER_MINUTE=30
MAX_DAILY_REQUESTS=500
USAGE_FILE=/var/lib/speakpen/usage.json
DISABLE_STREAMING=false
EOF
fi
chmod 0600 /etc/speakpen/speakpen.env
install -m 0644 deploy/speakpen.service /etc/systemd/system/speakpen.service
if [[ ! -f /etc/nginx/sites-available/speakpen ]]; then
    sed 's/__DOMAIN__/_/' deploy/nginx.conf.template > /etc/nginx/sites-available/speakpen
fi
ln -sfn /etc/nginx/sites-available/speakpen /etc/nginx/sites-enabled/speakpen
if [[ -L /etc/nginx/sites-enabled/default ]]; then unlink /etc/nginx/sites-enabled/default; fi
nginx -t
systemctl daemon-reload
systemctl enable speakpen nginx
systemctl restart nginx
echo '安装完成。执行 bash deploy/set-api-key.sh，然后检查 http://公网IP/health。'
