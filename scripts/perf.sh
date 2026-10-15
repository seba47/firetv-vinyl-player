#!/usr/bin/env bash
# Mide la fluidez de la app en el Fire TV durante N segundos (por defecto 20).
# Uso: scripts/perf.sh [segundos]
source "$(dirname "$0")/env.sh"
TARGET="$(firetv_target)"
SECS="${1:-20}"
"$ADB" -s "$TARGET" shell dumpsys gfxinfo "$APP_ID" reset >/dev/null
sleep "$SECS"
"$ADB" -s "$TARGET" shell dumpsys gfxinfo "$APP_ID" | grep -E "Total frames|Janky|50th percentile|90th percentile|Slow"
echo "Cuadros por segundo ≈ total / $SECS (60 es lo ideal)."
"$ADB" -s "$TARGET" shell top -b -n 1 -o %CPU,ARGS | grep "$APP_ID" | head -1 | sed 's/^/CPU: /'
