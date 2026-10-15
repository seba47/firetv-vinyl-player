#!/usr/bin/env bash
# Conecta adb al Fire TV por WiFi. La primera vez la tele muestra "¿Permitir depuración USB?":
# marcar "Permitir siempre desde esta computadora" y Aceptar.
# Uso: scripts/firetv-connect.sh [ip]   (la IP se guarda en .firetv, que no va al repo)
source "$(dirname "$0")/env.sh"
if [[ $# -ge 1 ]]; then echo "$1" > "$ROOT/.firetv"; fi
TARGET="$(firetv_target)"
"$ADB" connect "$TARGET"
for _ in {1..30}; do
  state="$("$ADB" -s "$TARGET" get-state 2>/dev/null || true)"
  [[ "$state" == "device" ]] && break
  echo "Esperando autorización en la tele… (aceptá el cartel con el control)"
  sleep 2
done
[[ "$state" == "device" ]] || { echo "No se pudo conectar ($state). Ver docs/firetv.md." >&2; exit 1; }
echo "Conectado a $TARGET"
"$ADB" -s "$TARGET" shell getprop ro.product.model
"$ADB" -s "$TARGET" shell getprop ro.build.version.sdk | sed 's/^/API /'
"$ADB" -s "$TARGET" shell getprop ro.build.version.name 2>/dev/null || true
