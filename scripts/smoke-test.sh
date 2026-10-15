#!/usr/bin/env bash
# Prueba NewPipeExtractor desde la Mac: busca, resuelve el audio y baja un pedacito.
# Uso: scripts/smoke-test.sh ["artista tema"]
source "$(dirname "$0")/env.sh"
cd "$ROOT"
./gradlew :app:testDebugUnitTest --tests app.vinilotv.ExtractionSmokeTest -Pquery="${1:-}"
