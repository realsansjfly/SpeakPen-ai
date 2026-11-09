#!/usr/bin/env bash
set -euo pipefail
[[ $EUID -eq 0 && -f /etc/speakpen/speakpen.env ]] || { echo '请先以 root 完成安装。'; exit 1; }
IFS= read -r -s -p '粘贴官方百炼 API Key（输入不显示，回车完成）: ' tts_key
printf '\n'
umask 077
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
model=${1:-qwen3-tts-flash}
voice=${2:-Cherry}
# Supply through stdin: the key never appears in process arguments, shell history or output.
printf '%s' "$tts_key" | python3 "$script_dir/configure-tts.py" --model "$model" --voice "$voice"
unset tts_key
systemctl restart speakpen
echo '官方云端朗读已配置并重启。'
