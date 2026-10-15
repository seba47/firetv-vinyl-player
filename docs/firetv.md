# Fire TV: modo desarrollador y adb por WiFi

## 1. Confirmar el modelo

Configuración → **Mi Fire TV** → **Acerca de**. Tiene que ser un Fire TV con Fire OS (Android):
por ejemplo *Fire TV Stick 4K (2nd Gen)* → Fire OS 8 (Android 11, API 30).
Los modelos con **Vega OS** (p. ej. *Fire TV Stick 4K Select*) **no** sirven: no corren apps Android.

## 2. Activar opciones de desarrollador

1. Configuración → Mi Fire TV → **Acerca de**.
2. Pararse sobre el **nombre del dispositivo** (primera opción) y apretar el botón central **7 veces**.
   Aparece "Ya sos desarrollador" (o similar).
3. Volver atrás: ahora en Mi Fire TV hay **Opciones de desarrollador**.
4. Adentro:
   - **Depuración ADB** → Activada.
   - **Apps de origen desconocido** (o "Instalar apps desconocidas") → Activada.

## 3. Averiguar la IP

Configuración → Mi Fire TV → Acerca de → **Red** → "Dirección IP". Conviene fijarla en el router
(reserva DHCP) para no tener que volver a buscarla.

## 4. Conectar desde la Mac

```bash
scripts/firetv-connect.sh 192.168.x.y
```

La tele muestra **"¿Permitir depuración USB?"** con la huella de la Mac: marcar
**"Permitir siempre desde esta computadora"** y **Aceptar** con el control. La IP queda en
`.firetv` (ignorado por git), así que después alcanza con `scripts/install.sh`.

## Problemas comunes

- `failed to connect` / `connection refused`: la depuración ADB se apaga sola a veces después de
  actualizaciones de Fire OS; volver a activarla. Verificar que la Mac y la tele estén en la misma red.
- `unauthorized`: no se aceptó el cartel en la tele. `adb disconnect` y reconectar.
- La app no aparece en el inicio: Configuración → Aplicaciones → Administrar aplicaciones
  instaladas → Vinilo TV → Abrir (o `scripts/play.sh`).
