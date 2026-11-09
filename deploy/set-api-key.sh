#!/usr/bin/env bash
set -euo pipefail
[[ $EUID -eq 0 && -f /etc/speakpen/speakpen.env ]] || { echo '请先以 root 完成安装。'; exit 1; }
IFS= read -r -s -p '粘贴平台 API Key（输入不显示，回车完成）: ' api_key
printf '\n'
umask 077
# Supply through stdin: never place the provider key in process arguments or shell history.
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
arguments=()
if [[ -n ${1:-} ]]; then arguments+=(--base-url "$1"); fi
if [[ -n ${2:-} ]]; then arguments+=(--chat-model "$2"); fi
printf '%s' "$api_key" | python3 "$script_dir/configure-provider.py" "${arguments[@]}"
unset api_key
systemctl restart speakpen
echo '已保存并重启。执行 systemctl status speakpen --no-pager 查看状态。'
