#!/usr/bin/env bash
# Compila, instala en el Fire TV y abre la app.
# Uso: scripts/install.sh ["búsqueda inicial"]
source "$(dirname "$0")/env.sh"
[[ "${1:-}" == "--emulator" ]] && shift
TARGET="$(firetv_target)"
[[ "$TARGET" == emulator-* ]] || "$ADB" connect "$TARGET" >/dev/null
"$(dirname "$0")/build.sh"
"$ADB" -s "$TARGET" install -r "$APK"
"$(dirname "$0")/play.sh" "${1:-}"
