# Calarmi para Android

Port de Calarmi (iOS, `../Calarm`) a Android: Kotlin + Jetpack Compose + Material 3.

| | |
|---|---|
| Paquete | `com.mathyusolutions.calarmi` (debug: `…calarmi.debug`) |
| Versión | `versionName` 1.0.19 (la misma que iOS), `versionCode` 1 |
| SDK | min 26 (Android 8.0) · compile 37.2 · target 36 |
| Idiomas | inglés (por defecto), español, portugués de Brasil |

## Compilar

Requisitos (ya instalados en este Mac): JDK 17 (`brew install openjdk@17`) y el Android SDK en
`~/Library/Android/sdk` (`local.properties` apunta ahí y no se sube a git).

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :app:assembleDebug          # APK de prueba → app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest      # tests (motor de recurrencia, cuentas regresivas…)
./gradlew :app:bundleRelease          # AAB firmado para Google Play → app/build/outputs/bundle/release/
```

`tools/ws-build.sh [tareas]` hace lo mismo con poca memoria (para varias compilaciones a la vez).

### Firma

El AAB se firma con la **llave de subida** `calarmi-upload.jks` (alias `upload`), con sus
contraseñas en `keystore.properties`. Los dos archivos están en `.gitignore`: **hay que guardar
una copia en un lugar seguro** (gestor de contraseñas). Google Play firma la app con su propia
llave (Play App Signing); si la de subida se pierde, se puede pedir un reseteo en Play Console,
pero tarda días.

## Textos

No hay textos escritos a mano en el código. Cada módulo tiene su tabla en `tools/strings/*.tsv`
(`name`, `en`, `es`, `pt`) y `python3 tools/strings/gen_strings.py` genera los
`res/values*/strings_*.xml`. `core.tsv` sale de `tools/strings/core_src.py`.
`python3 tools/strings/lookup.py "Posponer"` muestra cómo lo tradujo la app de iOS
(`ios_reference.tsv`, exportado de `Localizable.xcstrings`).

El ícono se genera desde el diseño de iOS con `python3 tools/icon/gen_icons.py` (vectores
adaptativos, variantes de color de Plus y monocromo para los íconos temáticos).

## Cómo suena una alarma

`alarm/AlarmPlanner` calcula, desde los datos, el **próximo** disparo (alarmas × horarios ×
avisos, eventos del calendario y lo pospuesto) y lo deja en el sistema con
`AlarmManager.setAlarmClock`: el sistema lo cumple en reposo profundo y enseña el ícono de
alarma. Cada vez que algo cambia (una alarma, un ajuste, la hora del teléfono, un reinicio) se
recalcula. Al llegar, `AlarmReceiver` arranca `RingService` (servicio en primer plano de tipo
`systemExempted`), que reproduce el tono en el canal de **alarma** (suena en silencio) y publica
la notificación de pantalla completa que abre `RingingActivity` sobre el bloqueo.

- Los datos viven en el almacenamiento protegido por dispositivo: una alarma suena aunque el
  teléfono se haya reiniciado de noche y nadie lo haya desbloqueado.
- Con reto, la alarma no deja de sonar hasta resolverlo (tope de 30 min); sin reto, 10 min.
- «Detener» calla también lo pospuesto de la misma alarma; posponer deja una sola pospuesta.
- Probado en el emulador (Android 16): suena a la hora con la pantalla apagada, pospuesto
  vuelve a sonar por la vía de `ALARM_MANAGER_ALARM_CLOCK`, y se reprograma tras reiniciar.

## Qué no está en la v1 de Android (y por qué)

- **Compartir alarmas y cuentas regresivas, invitaciones, personas de confianza**: en iOS van
  sobre CloudKit (iCloud), que no existe en Android. Llevarlo a las dos plataformas pide un
  backend propio, y eso choca con «sin servidores nuestros». Es una decisión pendiente.
- **Sincronizar entre dispositivos**: en iOS es iCloud. En Android, la copia de seguridad de
  Google guarda las alarmas, las cuentas, los ajustes y las fotos y las restaura al cambiar de
  teléfono, pero no sincroniza en vivo.
- **Siri, Live Activities, Isla Dinámica, control del Centro de control**: sin equivalente
  directo. En su lugar: atajos del ícono (Nueva alarma, Asistente), notificación con cuenta
  regresiva antes de sonar y widgets.
- **Guardar las alarmas en el Calendario** (iOS `CalendarMirrorService`): Calarmi en Android
  solo lee el calendario.
