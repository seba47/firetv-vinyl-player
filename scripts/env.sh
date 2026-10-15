# shellcheck shell=bash
# Entorno común de los scripts. Se usa con: source "$(dirname "$0")/env.sh"
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# JDK: el JBR que trae Android Studio (arm64 en Apple Silicon). Se puede pisar con JAVA_HOME.
if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
  for c in "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
           "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"; do
    [[ -x "$c/bin/java" ]] && { export JAVA_HOME="$c"; break; }
  done
fi
[[ -n "${JAVA_HOME:-}" ]] || { echo "No encuentro un JDK. Instalá Android Studio (ver docs/setup-mac.md)." >&2; exit 1; }

# Android SDK
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
[[ -d "$ANDROID_HOME/platform-tools" ]] || { echo "No encuentro el Android SDK en $ANDROID_HOME (ver docs/setup-mac.md)." >&2; exit 1; }
ADB="$ANDROID_HOME/platform-tools/adb"

APP_ID="app.vinilotv"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"

# Destino de adb: el Fire TV (por defecto) o el emulador si se pasa --emulator / VINILO_TARGET=emulator.
# IP del Fire TV: variable FIRETV_IP o archivo .firetv en la raíz (está en .gitignore).
firetv_target() {
  if [[ "${VINILO_TARGET:-}" == "emulator" ]]; then
    "$ADB" devices | awk '/^emulator-/{print $1; exit}' | grep . || { echo "No hay emulador corriendo: scripts/emulator.sh" >&2; exit 1; }
    return
  fi
  local ip="${FIRETV_IP:-}"
  [[ -z "$ip" && -f "$ROOT/.firetv" ]] && ip="$(tr -d '[:space:]' < "$ROOT/.firetv")"
  [[ -n "$ip" ]] || { echo "Falta la IP del Fire TV: scripts/firetv-connect.sh <ip>" >&2; exit 1; }
  [[ "$ip" == *:* ]] || ip="$ip:5555"
  echo "$ip"
}

# --emulator como primer argumento de cualquier script = usar el emulador.
if [[ "${1:-}" == "--emulator" ]]; then export VINILO_TARGET=emulator; shift; fi
