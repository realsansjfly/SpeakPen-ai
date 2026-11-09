#!/usr/bin/env bash
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo '请用 sudo 运行。'; exit 1; }
domain=${1:-}
email=${2:-}
[[ $domain =~ ^[a-zA-Z0-9][a-zA-Z0-9.-]*\.[a-zA-Z]{2,}$ && $domain != *..* && $email == *@* ]] || {
    echo '用法: bash deploy/configure-https.sh speakpen.你的域名.com 你的邮箱'; exit 1;
}
cd /opt/speakpen
[[ -f /etc/nginx/sites-available/speakpen ]] || { echo '请先完成安装。'; exit 1; }
if grep -q 'ssl_certificate' /etc/nginx/sites-available/speakpen; then
    echo '已有 HTTPS 配置，未覆盖。续期请使用 certbot renew；更换域名按教程操作。'; exit 1
fi
cp -a /etc/nginx/sites-available/speakpen /etc/nginx/sites-available/speakpen.before-https
sed "s/__DOMAIN__/$domain/" deploy/nginx.conf.template > /etc/nginx/sites-available/speakpen
nginx -t
systemctl reload nginx
certbot --nginx --non-interactive --agree-tos --redirect --email "$email" --domains "$domain"
systemctl enable --now certbot.timer
curl --fail --show-error --silent "https://$domain/health"
printf '\nHTTPS 已配置。请按文档检查自动续期及带令牌的 /v1/status。\n'
