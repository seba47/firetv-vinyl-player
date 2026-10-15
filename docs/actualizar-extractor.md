# Cuando YouTube rompe algo

NewPipeExtractor usa APIs no oficiales y se rompe cada tanto. Síntomas: "Falló la búsqueda",
"No se pudo resolver", o errores 403 al reproducir.

1. Confirmar desde la Mac: `scripts/smoke-test.sh`. Si falla acá también, es el extractor.
2. Ver si hay versión nueva: https://github.com/TeamNewPipe/NewPipeExtractor/releases
   - Si el arreglo todavía no salió en un release, usar el hash de commit que usa NewPipe en
     `gradle/libs.versions.toml` de https://github.com/TeamNewPipe/NewPipe (rama `dev`).
3. Cambiar **una línea**: `newpipe-extractor = "..."` en `gradle/libs.versions.toml`.
4. `scripts/smoke-test.sh` → `scripts/install.sh`.
