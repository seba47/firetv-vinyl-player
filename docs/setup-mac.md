# Setup de la Mac

Todo con binarios oficiales; **no usar Homebrew** para esto.

| Qué | Cómo | Notas |
|---|---|---|
| Android Studio | https://developer.android.com/studio (dmg para Apple Silicon) | Trae el JDK (JBR 21, arm64) que usan los scripts. |
| Android SDK | Lo instala Studio en `~/Library/Android/sdk` (Settings → Languages & Frameworks → Android SDK) | Hace falta *Platform-Tools* y *SDK Platform 37*. Las build-tools que falten las baja Gradle solo. |
| Gradle | Nada: viene el wrapper (`./gradlew`) | La primera compilación baja Gradle 9.6 y dependencias (~unos minutos). |

`scripts/env.sh` detecta el JDK de Android Studio y el SDK. Para usar otros, exportar `JAVA_HOME`
y/o `ANDROID_HOME` antes de correr los scripts.

> Ojo en Apple Silicon: si el JDK por defecto del sistema es x86_64 corre en Rosetta y Gradle anda
> lento. Los scripts fuerzan el JBR de Studio para evitarlo.

## Emulador (opcional)

`scripts/emulator.sh` crea un AVD 1920×1080 para ver la app sin la tele. Usa la imagen oficial de
Android TV si está instalada:

```bash
"$HOME/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager" "system-images;android-31;android-tv;arm64-v8a"
```
