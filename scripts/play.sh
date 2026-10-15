#!/usr/bin/env bash
# Abre la app en el Fire TV y le pide que busque/toque algo, sin tipear en la tele.
# Uso: scripts/play.sh ["artista tema"]
source "$(dirname "$0")/env.sh"
[[ "${1:-}" == "--emulator" ]] && shift
TARGET="$(firetv_target)"
args=(-n "$APP_ID/.MainActivity")
if [[ -n "${1:-}" ]]; then args+=(--es q "$1"); fi
"$ADB" -s "$TARGET" shell am start -S "${args[@]}"
