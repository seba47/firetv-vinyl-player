#!/usr/bin/env bash
# Levanta un emulador "tele" 1920x1080 en la Mac para ver la app sin el Fire TV.
# Usa la imagen de Android TV si está instalada; si no, una de teléfono en apaisado.
# Instalar la de Android TV (≈1 GB, binario oficial de Google):
#   "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "system-images;android-31;android-tv;arm64-v8a"
# Uso: scripts/emulator.sh            (arranca y espera a que bootee)
source "$(dirname "$0")/env.sh"
AVD=vinilo-tv
TV_IMG="system-images;android-31;android-tv;arm64-v8a"
PHONE_IMG="system-images;android-31;google_apis_playstore;arm64-v8a"
AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
EMULATOR="$ANDROID_HOME/emulator/emulator"

img_dir() { echo "$ANDROID_HOME/${1//;//}"; }
if [[ -d "$(img_dir "$TV_IMG")" ]]; then IMG="$TV_IMG"; DEVICE=tv_1080p
elif [[ -d "$(img_dir "$PHONE_IMG")" ]]; then IMG="$PHONE_IMG"; DEVICE=""
else echo "No hay imagen de emulador. Instalá la de TV (ver arriba)." >&2; exit 1; fi

CONFIG="$HOME/.android/avd/$AVD.avd/config.ini"
if [[ ! -f "$CONFIG" ]] || ! grep -q "image.sysdir.1=${IMG//;//}" "$CONFIG"; then
  echo "Creando AVD $AVD con $IMG"
  echo no | "$AVDMANAGER" create avd -f -n "$AVD" -k "$IMG" ${DEVICE:+-d "$DEVICE"}
  # Pantalla de tele: 1920x1080 apaisado, densidad de TV (xhdpi = 320, como el Fire TV 4K a 1080p).
  cat >> "$CONFIG" <<CFG
hw.lcd.width=1920
hw.lcd.height=1080
hw.lcd.density=320
hw.initialOrientation=landscape
hw.keyboard=yes
hw.dPad=yes
hw.ramSize=4096
CFG
fi

if ! "$ADB" devices | grep -q '^emulator-'; then
  nohup "$EMULATOR" -avd "$AVD" -no-snapshot-save -no-boot-anim >/tmp/vinilo-emulator.log 2>&1 &
fi
"$ADB" wait-for-device
until [[ "$("$ADB" -e shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do sleep 2; done
"$ADB" -e shell settings put system user_rotation 1 >/dev/null 2>&1 || true
echo "Emulador listo. Instalar y abrir: scripts/install.sh --emulator"
