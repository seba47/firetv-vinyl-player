# Vinilo TV

Tocadiscos de fondo para Fire TV: elegís un tema, un disco o una playlist desde el celular y suena
YouTube Music sin parar, con un vinilo girando en la tele. App nativa Android TV (Kotlin + Compose +
Media3) con extracción en el dispositivo vía [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor).
Sin servidor propio. Uso personal, sideload.

## Cómo se usa

- **Elegir qué suena:** apretá **≡** en el control → aparece un QR → escanealo con el celular (mismo
  WiFi) → buscá un tema, disco o playlist → "Poner en la tele".
- Suena la semilla (un disco va en orden, "Lado A · tema 3") y cuando termina **sigue enganchando
  temas parecidos al azar**, sin repetir. Nunca se corta.
- Control remoto: **→** siguiente · **OK** pausa · **↑↓** estaciones recientes · **≡** elegir música ·
  **Atrás** sale (la música sigue sonando de fondo; play/pausa del control y "Alexa, pausa" andan).
- A los 3 minutos sin tocar nada queda solo el tocadiscos (salvapantallas propio).
- **Fondo de la tele** (desde la página del celular): *Living* (oscuro, con la luz de la tapa; por
  defecto) o *Estufa de yeso* (pared blanca con el relieve de la estufa donde está colgada la tele,
  tocadiscos centrado), con brillo del yeso ajustable para que de noche no encandile.
- Toques visuales (polvo, haz de luz —apagado—, alabeo del vinilo, cambio de lado A/B en los discos): cada uno
  se apaga con su llave en `app/src/main/java/app/vinilotv/ui/Touches.kt` y `scripts/install.sh`.

## Desde la Mac

```bash
scripts/smoke-test.sh "Soda Stereo"          # ¿NewPipeExtractor anda? (desde la Mac, sin tele)
scripts/firetv-connect.sh 192.168.x.y        # una vez: conecta adb al Fire TV (guarda la IP en .firetv)
scripts/install.sh "Cerati Crimen"           # compila, instala y abre buscando eso
scripts/play.sh "Spinetta"                   # cambia lo que suena sin reinstalar
scripts/logs.sh                              # logs de la app
scripts/mockup.sh                            # mockups de diseño: http://localhost:8765/demo.html
```

## Cómo está hecho

| Pieza | Archivo |
|---|---|
| Búsqueda, discos, playlists, mix y audio (NewPipeExtractor) | `app/.../YouTube.kt` |
| La radio: cola, enganche, precarga del siguiente, saltear temas que fallan | `app/.../Radio.kt` |
| Reproducción en segundo plano (Media3 MediaSession) | `app/.../PlaybackService.kt` |
| Página y API para el celular (red local, puerto 8080) | `app/.../WebServer.kt`, `app/src/main/assets/web/index.html` |
| Pantalla del tocadiscos (Compose) | `app/.../ui/` |
| Mockups de diseño | `design/mockup/` |

Las URLs de audio de YouTube vencen (~6 h) y quedan atadas a la IP: cada tema se resuelve justo antes
de sonar. Tipografías: Young Serif, Familjen Grotesk y Big Shoulders Text (OFL, `docs/licencias/`).

## Docs

- [docs/setup-mac.md](docs/setup-mac.md) — qué hace falta en la Mac.
- [docs/firetv.md](docs/firetv.md) — activar modo desarrollador y adb por WiFi en el Fire TV.
- [docs/actualizar-extractor.md](docs/actualizar-extractor.md) — cuando YouTube rompe algo.
- [docs/rendimiento.md](docs/rendimiento.md) — qué traba la GPU del Fire TV y cómo medirlo (`scripts/perf.sh`).
