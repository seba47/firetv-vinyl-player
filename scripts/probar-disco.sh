#!/usr/bin/env bash
# Prueba desde la Mac si un disco/playlist/tema de YouTube Music se puede reproducir.
# Uso: scripts/probar-disco.sh "https://music.youtube.com/playlist?list=..."
source "$(dirname "$0")/env.sh"
cd "$ROOT"
./gradlew :app:testDebugUnitTest --tests "app.vinilotv.ExtractionSmokeTest.specificAlbum" -Purl="${1:?Pasá la URL del disco}"
