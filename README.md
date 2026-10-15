# Vinilo TV

Tocadiscos de fondo para Fire TV: va pasando temas de YouTube Music sin parar, como una radio.
App nativa Android TV (Kotlin + Compose + Media3) con extracción en el dispositivo vía
[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor). Uso personal, sideload.

> Estado: **fase 0 (spike)** — busca un tema y lo reproduce. Todavía sin diseño.

## Rápido

```bash
scripts/smoke-test.sh "Soda Stereo"          # ¿NewPipeExtractor anda? (desde la Mac, sin tele)
scripts/firetv-connect.sh 192.168.x.y        # una vez: conecta adb al Fire TV (guarda la IP en .firetv)
scripts/install.sh "Cerati Crimen"           # compila, instala y abre buscando eso
scripts/play.sh "Spinetta"                   # cambia lo que suena sin reinstalar
scripts/logs.sh                              # logs de la app
```

Control remoto (spike): **derecha** = siguiente resultado, **centro** = pausa.

## Docs

- [docs/setup-mac.md](docs/setup-mac.md) — qué hace falta en la Mac.
- [docs/firetv.md](docs/firetv.md) — activar modo desarrollador y adb por WiFi en el Fire TV.
- [docs/actualizar-extractor.md](docs/actualizar-extractor.md) — cuando YouTube rompe algo.
