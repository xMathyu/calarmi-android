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

## Compartir (Firebase)

Alarmas y cuentas regresivas compartidas, invitaciones y personas de confianza van por Firebase
(proyecto `calarmi`), con el mismo contrato que iOS y la web: `../calarmi-firebase/DESIGN.md`
(colecciones y campos) y `firestore.rules` (quién escribe qué). El código está en `sharing/`.

- **Sesión anónima y perezosa**: Firebase Auth se abre la primera vez que la persona invita,
  acepta o activa «Personas de confianza», y antes pide el nombre («¿Cómo te llamas?»). Hasta
  entonces no hay sesión ni token de push. Nada de Firebase se toca antes del primer desbloqueo
  (`AppContainer.isUserUnlocked`): el motor de alarmas sigue igual en el arranque directo.
- **Dueño**: «Invitar amigos» crea `shares/{id}` (el id es el de la alarma o la cuenta) y una
  invitación `invites/{token}` en un lote; cada guardado sube el contenido (`revision` + 1), la
  foto si cambió y la vista previa. Borrar o «Dejar de compartir» borra el share y revoca la
  invitación. Los repositorios avisan por `SyncHooks`; lo que llega de Firebase se escribe directo
  en la base y no rebota.
- **Invitado**: el enlace `https://calarmi.mathyusolutions.com/s/<token>/` (App Link) o
  `calarmi://s/<token>` abre la vista previa; «Aceptar» se une con el token. La copia local es de
  solo lectura salvo los avisos, que son de esa persona (`personalLeadTimes`). Si el dueño cambia
  la hora, sale un aviso («Ana cambió el horario…»).
- **Personas de confianza**: el teléfono de quien delega refleja sus alarmas en
  `delegations/{uid}/reminders` y aplica lo que cambian sus ayudantes (gana el `updatedAt` más
  nuevo); quien ayuda las edita con el mismo editor, directo en ese documento.
- **Sincronizar**: con la app a la vista, listeners de Firestore (caché persistente); con la app
  cerrada, un push de datos de alta prioridad (Cloud Functions) despierta `ShareSyncWorker`, que
  lee el cambio, reprograma y avisa.
- Room pasó a la versión 2 (`MIGRATION_1_2`, columnas nuevas; nada se borra). Esquemas en
  `app/schemas/`.
- La web tiene que publicar `/.well-known/assetlinks.json` con las huellas SHA-256 de la llave de
  subida, la de firma de Play y la de debug, o los enlaces se abren en el navegador.

## Qué no está en la v1 de Android (y por qué)

- **Sincronizar entre dispositivos**: en iOS es iCloud. En Android, la copia de seguridad de
  Google guarda las alarmas, las cuentas, los ajustes y las fotos y las restaura al cambiar de
  teléfono, pero no sincroniza en vivo.
- **Siri, Live Activities, Isla Dinámica, control del Centro de control**: sin equivalente
  directo. En su lugar: atajos del ícono (Nueva alarma, Asistente), notificación con cuenta
  regresiva antes de sonar y widgets.
- **Guardar las alarmas en el Calendario** (iOS `CalendarMirrorService`): Calarmi en Android
  solo lee el calendario.
