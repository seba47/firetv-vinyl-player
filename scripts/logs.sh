#!/usr/bin/env bash
# Muestra los logs de la app en el Fire TV (Ctrl+C para salir).
source "$(dirname "$0")/env.sh"
[[ "${1:-}" == "--emulator" ]] && shift
TARGET="$(firetv_target)"
pid="$("$ADB" -s "$TARGET" shell pidof "$APP_ID" | tr -d '\r' || true)"
if [[ -n "$pid" ]]; then
  "$ADB" -s "$TARGET" logcat -v time --pid="$pid"
else
  echo "La app no está corriendo; muestro solo el tag ViniloTV y errores de AndroidRuntime."
  "$ADB" -s "$TARGET" logcat -v time ViniloTV:V AndroidRuntime:E '*:S'
fi
