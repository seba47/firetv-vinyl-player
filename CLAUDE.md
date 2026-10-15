# Vinilo TV — tocadiscos de fondo para Fire TV

## Objetivo
App personal para un **Fire TV Stick 4K** (conectado a una tele Samsung de 42") que muestre un
**tocadiscos** y vaya pasando temas de YouTube / YouTube Music **de fondo, sin parar**, como una
radio. Uso personal, sin publicar en ninguna tienda (sideload).

## Decisiones tomadas (no re-discutir)
- **Standalone y lo más simple posible**: la app hace todo en el Fire TV, sin server propio, sin
  celular de puente, sin túneles. Es un proyecto independiente de la skill "la rocola" (repo
  youtube-music-alexa-skill), aunque sirve de referencia.
- **Extracción de YouTube en el dispositivo con NewPipeExtractor** (librería Java del proyecto
  NewPipe): búsqueda, URLs de audio, temas relacionados / mix. Corre con la IP residencial de la
  tele, que es la que mejor anda con YouTube.
- **App nativa Android TV**: Kotlin + Jetpack Compose for TV + Media3 (ExoPlayer +
  MediaSessionService). Se descartó la web/WebView: para audio de fondo durante horas, lo nativo
  sigue sonando, responde a los botones del control y al Alexa del control vía MediaSession.
- Sin restricción de formato (no hay Alexa en el medio): Opus/WebM o AAC, lo que sea mejor.
- Las URLs de audio de YouTube vencen (~6 h): resolver cada tema poco antes de reproducirlo,
  nunca precargar toda la cola.

## Experiencia buscada
- **Pantalla**: tocadiscos visto un poco desde arriba. El disco gira con la **tapa del álbum como
  etiqueta central**; el brazo se apoya al empezar un tema y se levanta al cambiar. Título y
  artista en letra grande (se mira desde el sillón), lo que viene en chiquito.
- **Paleta apagada, cálida**: madera nogal o grafito cálido de fondo, texto crema, detalles en
  latón/ámbar tenue, disco negro mate con brillo suave. Nada saturado ni neón.
- **Estaciones infinitas**: una semilla (artista o tema) arma una radio; cuando quedan pocos temas
  se piden más (mix/relacionados). Nunca se termina.
- **Control remoto**: derecha = siguiente, centro = pausa, arriba/abajo = cambiar de estación.
  Evitar tipear con el teclado de la tele.
- Mantener la pantalla prendida mientras suena (que no entre el protector de pantalla).

## Restricciones y riesgos
- NewPipeExtractor usa APIs no oficiales: **se rompe cada tanto** cuando YouTube cambia algo.
  Diseñar para que actualizar la dependencia sea trivial (una línea + rebuild + reinstalar).
- Va contra los ToS de YouTube: uso estrictamente personal, **repo privado**.
- **Modelo del Fire TV confirmado**: Fire TV Stick 4K (2.ª gen), modelo AFTKM, Fire OS 8.1.8.2
  (Android 11, API 30). Acepta sideload. (Los "4K Select" con Vega OS no sirven.)
- Instalación por **adb por WiFi** desde la Mac: Fire TV → Mi Fire TV → Acerca de → apretar 7 veces
  el nombre del dispositivo → Opciones de desarrollador → activar depuración ADB y apps de origen
  desconocido → `adb connect <ip-del-firetv>:5555`.

## Plan por fases
0. ✅ **Spike** (hecho: extracción, Opus 160 kbps y sideload andan en el Fire TV real) (validar lo riesgoso primero): app mínima que busca un tema con NewPipeExtractor y lo
   reproduce con ExoPlayer, instalada en el Fire TV real. Confirma extracción, audio y sideload.
1. **Estación infinita** + control remoto + MediaSession (sigue sonando en segundo plano, play/pausa
   del control, "Alexa, pausa" desde el control).
2. **El tocadiscos**: animación del disco, brazo, tapa como etiqueta, paleta. Hacer un mockup
   visual antes de programarlo.
3. **Elegir qué suena**: búsqueda desde el celular vía QR (ver pregunta 2) + estaciones recientes.
4. **Pulido**: recuperación de errores (saltear temas que fallan), reconexión, cómo actualizar
   NewPipeExtractor, README de instalación.

## Preguntas abiertas (preguntarme antes de avanzar en lo que dependa de ellas)
1. ~~Modelo exacto del Fire TV~~ → confirmado (ver arriba).
2. ~~¿Cómo elegir qué suena?~~ → **decidido**: se busca **desde el celular**. La tele muestra un QR
   → página web que sirve la propia app en la red local → se busca en YT Music y se elige un
   **tema, un disco o una playlist** como semilla. Suena la semilla (disco/playlist en orden) y
   cuando termina **sigue enganchando al azar** temas parecidos (mix de YT Music `RDAMVM<id>` /
   relacionados de lo último que sonó, sin repetir). El celular es solo el teclado: la música no
   depende de él. Las semillas usadas quedan como estaciones recientes (↑/↓ en el control).
3. ¿Hace falta mi biblioteca personal de YT Music (playlists, me gusta) o alcanza con búsqueda +
   radios públicas? NewPipeExtractor no usa login.

## Entorno de desarrollo (Mac)
- `adb` está en `~/.dotnet/tools/adb`.
- El Homebrew de la Mac está en `/usr/local` sobre Apple Silicon y **compila todo desde fuente**
  (tarda horas): no usarlo para instalar herramientas; preferir binarios oficiales.
- Verificar si hay Android Studio / JDK / Android SDK instalados antes de asumir.

## Cómo trabajar conmigo
- Español informal; respuestas directas y honestas, sin suavizar.
- Desarrollador (fuerte en .NET/mobile): no explicar lo básico, sí las particularidades de Fire TV,
  Android TV y YouTube.
- Antes de pasos que requieran acciones mías en dispositivos o consolas, instrucciones concretas
  paso a paso.
- Todo lo que se haga a mano se vuelca a scripts + docs en el repo: tiene que poder replicarse en
  otra máquina y compartirse con amigos. Nada personal en el repo (secretos, IPs, seriales).
- Git: commits con mi email noreply de GitHub `5150551+seba47@users.noreply.github.com`; repo
  privado en github.com/seba47; no quiero que Copilot trabaje en el repo.
