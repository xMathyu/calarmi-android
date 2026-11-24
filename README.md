# Calarmi para Android

Port de Calarmi (iOS, `../Calarm`) a Android: Kotlin + Jetpack Compose + Material 3.

| | |
|---|---|
| Paquete | `com.mathyusolutions.calarmi` (debug: `…calarmi.debug`) |
| Versión | `versionName` 1.1, `versionCode` 3 |
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

## CI/CD (GitHub Actions)

Repo privado `xMathyu/calarmi-android`.

- **CI** (`.github/workflows/ci.yml`): cada push a `main` y cada pull request corren las pruebas
  unitarias y arman el APK de debug (queda como artefacto). No necesita secretos.
- **CD** (`.github/workflows/publicar.yml`): un tag `v*` (`git tag v1.1.1 && git push origin
  v1.1.1`) o «Run workflow» compila el AAB firmado y lo publica en Google Play (por defecto en la
  prueba cerrada, `alpha`, enviado a revisión). `versionCode` = 100 + número de corrida; el
  `versionName` sale del tag. El mapa de R8 va dentro del AAB y Play lo toma solo.
- Publicar no usa llaves de Google: Workload Identity Federation cambia el token de GitHub por uno
  de la cuenta de servicio `calarmi-play-publisher@calarmi.iam.gserviceaccount.com`, invitada en
  Play Console. Preparación (una vez): `tools/ci/configurar-gcp.sh` (Google Cloud + variables del
  repo) y `tools/ci/configurar-secretos.sh` (la llave de subida, en los secretos del repo).

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

- **Cuenta obligatoria** (`account/`, DESIGN.md › Identidad): la primera pantalla, antes de la
  bienvenida, es iniciar sesión con **Google** (Credential Manager, `default_web_client_id` de
  `google-services.json`) o **correo y contraseña** («Crear cuenta», «Iniciar sesión»,
  «¿Olvidaste tu contraseña?»). El nombre que ven los demás sale de la cuenta (proveedor →
  `displayName` → el guardado → el correo → «Usuario de Calarmi») y se guarda en Firebase Auth,
  `users/{uid}` y la copia local `SharingProfile.name`. Ajustes › Cuenta: nombre y correo,
  editar el nombre, «Cerrar sesión» y «Borrar cuenta» (función `deleteAccount`). Una sesión
  anónima de versiones anteriores se **vincula** al entrar (conserva el `uid` y lo compartido);
  si entra otra cuenta (`lastUid` distinto), se borran las copias de lo que otros compartieron y
  lo de personas de confianza, y lo propio compartido queda como propio.
- **El motor de alarmas no depende de la sesión**: `RingingActivity`, los receptores,
  `RingService`, los widgets y el planificador suenan sin cuenta, sin red y en el arranque
  directo. Nada de Firebase se toca antes del primer desbloqueo (`AppContainer.isUserUnlocked`);
  antes de eso la app solo pide desbloquear.
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
- **Probar cuentas sin tocar el proyecto real**: con los emuladores de Firebase corriendo en
  `../calarmi-firebase` (`npm --prefix functions run build` y luego
  `JAVA_HOME=/opt/homebrew/opt/openjdk firebase emulators:start --only auth,firestore,functions --project calarmi`),
  `tools/ws-build.sh :app:assembleDebug -Pcalarmi.firebaseEmulator=10.0.2.2` hace un Debug que
  usa esos emuladores (Auth 9099, Firestore 8080, Functions 5001). Sin la propiedad, y siempre en
  Release, va al proyecto real. Google no se puede probar así (pide una cuenta de Google real).
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
