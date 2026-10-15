#!/usr/bin/env bash
# Compila el APK de debug (app/build/outputs/apk/debug/app-debug.apk).
source "$(dirname "$0")/env.sh"
cd "$ROOT"
./gradlew :app:assembleDebug "$@"
echo "APK: $APK"
