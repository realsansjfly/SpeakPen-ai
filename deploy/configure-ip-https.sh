#!/usr/bin/env bash
# Publicly trusted IPv4 HTTPS without a domain. Run after install-ubuntu.sh.
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo '请用 sudo 运行。'; exit 1; }
ip=${1:-}
python3 - "$ip" <<'PY'
import ipaddress, sys
try:
    address = ipaddress.IPv4Address(sys.argv[1])
    assert address.is_global
except (ValueError, AssertionError):
    raise SystemExit('用法: bash deploy/configure-ip-https.sh 服务器公网IPv4')
PY
site=/etc/nginx/sites-available/speakpen
[[ -f $site ]] || { echo '请先安装 SpeakPen。'; exit 1; }
if grep -q 'ssl_certificate' "$site" && ! grep -q '/etc/speakpen/acme/live/speakpen-ip/' "$site"; then
    echo '已有其他 HTTPS 配置，请先检查，不覆盖。'; exit 1
fi
apt-get update
apt-get install -y python3-venv
if [[ ! -x /opt/speakpen-certbot/bin/certbot ]]; then
    python3 -m venv /opt/speakpen-certbot
fi
/opt/speakpen-certbot/bin/pip install --index-url https://mirrors.aliyun.com/pypi/simple --disable-pip-version-check --no-cache-dir --timeout 20 --retries 2 'certbot==5.9.0' 'acme==5.9.0'
certbot=/opt/speakpen-certbot/bin/certbot
"$certbot" --version
install -d -m 0700 /etc/speakpen/acme /var/lib/speakpen-acme /var/log/speakpen-acme
install -d -m 0755 /var/www/speakpen-acme/.well-known/acme-challenge
backup="$site.before-ip-https-$(date +%Y%m%d-%H%M%S)"
cp -a "$site" "$backup"
restore_site() {
    echo "配置失败，恢复 Nginx 配置：$backup"
    cp -a "$backup" "$site"
    nginx -t && systemctl reload nginx
}
trap restore_site ERR
# Add the challenge location while leaving the running application available.
python3 - "$site" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
config = p.read_text()
if '/.well-known/acme-challenge/' not in config:
    if '    location / {' not in config:
        raise SystemExit('未识别的 Nginx 配置，未覆盖。')
    challenge = '''    location ^~ /.well-known/acme-challenge/ {
        root /var/www/speakpen-acme;
        default_type text/plain;
        try_files $uri =404;
    }

'''
    p.write_text(config.replace('    location / {', challenge + '    location / {', 1))
PY
nginx -t
systemctl reload nginx
"$certbot" certonly --webroot --webroot-path /var/www/speakpen-acme \
    --config-dir /etc/speakpen/acme --work-dir /var/lib/speakpen-acme --logs-dir /var/log/speakpen-acme \
    --preferred-profile shortlived --ip-address "$ip" --cert-name speakpen-ip \
    --non-interactive --agree-tos --register-unsafely-without-email --keep-until-expiring
cat > "$site" <<EOF
server {
    listen 80;
    listen [::]:80;
    server_name $ip;
    server_tokens off;
    location ^~ /.well-known/acme-challenge/ {
        root /var/www/speakpen-acme;
        default_type text/plain;
        try_files \$uri =404;
    }
    location / { return 301 https://$ip\$request_uri; }
}
server {
    listen 443 ssl;
    listen [::]:443 ssl;
    server_name $ip;
    server_tokens off;
    ssl_certificate /etc/speakpen/acme/live/speakpen-ip/fullchain.pem;
    ssl_certificate_key /etc/speakpen/acme/live/speakpen-ip/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    client_max_body_size 11m;
    location / {
        proxy_pass http://127.0.0.1:8787;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_buffering off;
        proxy_read_timeout 60s;
        proxy_send_timeout 60s;
    }
}
EOF
nginx -t
systemctl reload nginx
cat > /etc/systemd/system/speakpen-certbot-renew.service <<'EOF'
[Unit]
Description=Renew SpeakPen short-lived IP HTTPS certificate
Wants=network-online.target
After=network-online.target nginx.service

[Service]
Type=oneshot
ExecStart=/opt/speakpen-certbot/bin/certbot renew --config-dir /etc/speakpen/acme --work-dir /var/lib/speakpen-acme --logs-dir /var/log/speakpen-acme --quiet --deploy-hook "/usr/sbin/nginx -t && /usr/bin/systemctl reload nginx"
EOF
cat > /etc/systemd/system/speakpen-certbot-renew.timer <<'EOF'
[Unit]
Description=Check SpeakPen IP certificate renewal every six hours

[Timer]
OnCalendar=*-*-* 00,06,12,18:00:00
RandomizedDelaySec=300
Persistent=true

[Install]
WantedBy=timers.target
EOF
systemctl daemon-reload
systemctl enable --now speakpen-certbot-renew.timer
curl --fail --show-error --silent --resolve "$ip:443:127.0.0.1" "https://$ip/health"
trap - ERR
printf '\nIP HTTPS 已配置。还需从公网验证 443，并运行新版 Certbot 的 renew --dry-run。\n'
