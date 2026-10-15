# Rendimiento en el Fire TV

El Fire TV Stick 4K (2.ª gen) tiene una GPU de gama baja (PowerVR GE9215) y 1,7 GB de RAM que el
sistema de Amazon usa casi entera. La primera versión del tocadiscos andaba a ~7 cuadros por segundo;
con lo de abajo llega a ~60. Medir con:

```bash
scripts/perf.sh        # cuadros, percentiles y CPU de la app durante 20 s
```

## Qué la trababa y qué hacer en su lugar

| No | Sí |
|---|---|
| Desenfocar (`BlurMaskFilter`) en cada cuadro | Pre-desenfocar una vez en una imagen (`bakeArmShadows`) |
| Redibujar lo estático (veta, surcos, brillo) en cada cuadro | Pintarlo una vez en una imagen (`Modifier.baked`) |
| Recortar la tapa en círculo (`clip(CircleShape)`) mientras gira | Recortarla una vez por tapa (`bakeLabel`) y solo girar la imagen |
| Varias capas de pantalla completa (fondo + tapa + viñeta) | Una imagen chica ya combinada, estirada (`bakeBackdrop`) |
| Leer estado que cambia en cada cuadro en el cuerpo de un `@Composable` | Leerlo en lambdas de dibujo (`Canvas`, `graphicsLayer`) o con `derivedStateOf` |
| Crear `Brush` de degradé dentro del dibujo | Degradés como constantes (cada uno nuevo es un shader nuevo) |
| Imágenes del tamaño de la pantalla casi transparentes | Del tamaño justo de lo que se ve |

La música va por ExoPlayer en otro hilo: aunque la pantalla se trabe, no se corta.

Si la tele tiene la memoria al límite (otras apps abiertas, swap lleno), cada medición da distinto:
cerrar apps desde Configuración → Aplicaciones antes de comparar.
