# Publicar Calarmi en Google Play: lista de pasos

Lista ordenada para publicar la primera versión de Android. Lo que hay que pegar en Play Console
está en `store/` (ver la sección 9). Los textos para los revisores de Google van en inglés, porque
es el idioma en que revisan; debajo de cada uno hay una versión en español para entenderlo.

| Dato | Valor |
|---|---|
| Nombre en Play | Calarmi |
| Paquete (`applicationId`) | `com.mathyusolutions.calarmi` (no se puede cambiar después de subir el primer AAB) |
| Versión | `versionName` 1.1 · `versionCode` 3 (cada AAB que subas necesita un `versionCode` mayor) |
| Android | `minSdk` 26 (Android 8.0) · `targetSdk` 36 |
| Tipo | App gratuita con compras dentro de la app (Calarmi Plus) |
| Productos | `plus_yearly` (suscripción anual con 1 semana gratis) · `plus_lifetime` (pago único) |
| Web | https://calarmi.mathyusolutions.com |
| Contacto | xmathyu@gmail.com |

## Estado (23 nov 2026)

- App creada (ID 4975790279101536661). Ficha en en-US (predeterminada), es-419, es-ES, es-US,
  pt-BR y de-DE, con gráfico de funciones y capturas por idioma (de-DE usa las capturas en inglés).
- Contenido de la app completo: privacidad, anuncios, acceso, público, seguridad de los datos,
  salud («sin funciones de salud»), pantalla completa y alarmas exactas («Despertador»),
  clasificación IARC (ESRB Everyone, PEGI 3, USK 0; ClassInd de Brasil 14+ por «Users interact»).
- Prueba cerrada «Alpha»: versión **3 (1.1)**, 178 países, lista «Testers Sinchi - prueba
  cerrada» (2 personas), comentarios a xmathyu@gmail.com. **Enviada a revisión.**
- Pendiente: perfil de pagos (cuenta de comerciante) → productos de Plus (sección 7); 12 testers
  × 14 días → acceso a producción (8.3); subir `dist/Calarmi-1.1-3-mapping.txt` (~100 MB) en
  *Paquetes de la app › 3 › Descargas › Archivo de desofuscación*, a mano (la subida automatizada
  de Claude en Chrome tiene un tope de 10 MB).
- Por ese mismo tope, el AAB subido es `dist/Calarmi-1.1-N-upload.aab`: el mismo bundle sin el
  mapa de R8 dentro, vuelto a firmar con la llave de subida.

---

## 0. Antes de entrar a Play Console

- [ ] **Publicar la sección de Android en la política de privacidad** de la web
      (`store/privacy-policy-android.md`). Play revisa que la URL hable de la app de Android, y
      hoy la página dice «iPhone» e «iCloud» en todas partes.
- [ ] **Capturas de pantalla del teléfono.** No están en `store/` (no se pueden hacer sin la app
      corriendo). Play pide **mínimo 2** por idioma; para que Play pueda recomendar la app,
      **4 o más** de al menos 1080 px. PNG o JPEG, 9:16, cada lado entre 320 y 3840 px (lo ideal:
      1080 × 1920 o 1080 × 2400 sin la barra de navegación tapando nada). Sugerencia, en el mismo
      orden que en la App Store: lista con el despertador · reto (cálculo) · cuentas regresivas ·
      editor de un cumpleaños · widgets · calendario con «Unirse». **No** usar capturas de iPhone ni
      la del asistente si el teléfono de las capturas no tiene Gemini Nano.
- [ ] **Clave de subida (upload key)** creada y guardada (paso 3).
- [ ] **Build de release probado en un teléfono real**: el release tiene R8 (`isMinifyEnabled`), así
      que hay que probar justo ese build (alarmas, reinicio, compras con cuenta de prueba, widgets).
- [ ] **Revisar el manifiesto final** (el que junta las bibliotecas). Después de
      `./gradlew :app:bundleRelease`:
      ```sh
      find app/build/intermediates -path '*release*' -name AndroidManifest.xml | xargs grep -h "uses-permission" | sort -u
      ```
      Debe salir solo lo esperado: los permisos del manifiesto propio más `com.android.vending.BILLING`
      e `INTERNET`/`ACCESS_NETWORK_STATE` de las bibliotecas de Google. Si aparece
      `com.google.android.gms.permission.AD_ID`, `READ_MEDIA_IMAGES` o `READ_EXTERNAL_STORAGE`, quitarlo
      con `tools:node="remove"`: cambiaría las respuestas de los pasos 5.6, 5.7 y la política de fotos.
- [ ] **Páginas de 16 KB.** Si el AAB trae bibliotecas nativas (`.so`), Play exige que estén alineadas
      a 16 KB. Play Console lo avisa en *Explorador de App Bundle*; con Android Studio se ve en
      *Build › Analyze APK*.

---

## 1. Cuenta de desarrollador: personal u organización

Las dos cuestan **25 USD una sola vez** y piden verificar identidad, correo y teléfono. La
diferencia importante para Calarmi:

| | Cuenta **personal** | Cuenta de **organización** (Mathyu Solutions) |
|---|---|---|
| Qué piden | Documento de identidad, nombre legal y dirección | **Número D-U-N-S** de la empresa, nombre legal y dirección iguales a los de D-U-N-S, teléfono y web de la empresa |
| Prueba obligatoria antes de producción | **Sí**, si la cuenta se creó después del 13 de noviembre de 2023: una **prueba cerrada con al menos 12 testers inscritos durante 14 días seguidos**, y luego solicitar el acceso a producción | No |
| Tiempo hasta publicar | 14 días de prueba como mínimo + revisión de la solicitud (Google habla de hasta 7 días) + revisión de la app | Lo que tarde Dun & Bradstreet en dar el D-U-N-S (gratis; puede tardar hasta 30 días) + verificación + revisión de la app |
| Qué ve el público | El nombre de desarrollador que elijas y el país; si vendes en la UE, también dirección y teléfono (ver «comerciante») | Nombre, dirección, teléfono y correo de la organización |

**Recomendación:** si Mathyu Solutions ya tiene D-U-N-S (si la cuenta de Apple es de organización,
Apple lo pidió y es el mismo número), crear la cuenta de **organización**: se salta los 14 días de
prueba cerrada. Si no hay D-U-N-S y no se quiere esperar, ir por la **personal** y empezar la
prueba cerrada cuanto antes. Cambiar de tipo de cuenta después no es inmediato (en general
significa crear la otra cuenta y transferirle la app), así que conviene decidirlo ahora.

### Camino A: cuenta personal

1. [ ] Crear la cuenta en https://play.google.com/console/signup con la cuenta de Google que vaya
       a ser la dueña (mejor una dedicada, no la personal del día a día). Tipo: *Yourself*.
2. [ ] Pagar los 25 USD y verificar identidad (documento), correo de contacto y teléfono.
3. [ ] Confirmar que tienes acceso a un **teléfono Android real**: Google lo pide para verificar la
       cuenta desde la app de Play Console.
4. [ ] Seguir con los pasos 2 a 7. En el paso 8, hacer la **prueba cerrada de 12 testers × 14
       días** y después solicitar el acceso a producción (8.3).

### Camino B: cuenta de organización

1. [ ] Conseguir (o recuperar) el **D-U-N-S** de Mathyu Solutions: https://www.dnb.com/duns-number/lookup.html.
       El nombre legal y la dirección tienen que ser idénticos a los que pongas en Play.
2. [ ] Crear la cuenta en https://play.google.com/console/signup. Tipo: *An organization or
       business*. Datos: D-U-N-S, nombre legal, dirección, teléfono y correo de la organización,
       web (https://mathyusolutions.com o https://calarmi.mathyusolutions.com).
3. [ ] Pagar los 25 USD y completar la verificación de la organización.
4. [ ] Seguir con los pasos 2 a 8 (sin la prueba obligatoria de 14 días).

### En los dos caminos

- [ ] **Perfil de pagos** (*Configuración › Perfil de pagos*), necesario para vender Calarmi Plus:
      cuenta bancaria (la del BCP sirve: Perú está entre los países donde se puede vender, con
      moneda PEN), datos fiscales de EE. UU. (W-8BEN para persona, W-8BEN-E para empresa; igual que
      con Apple, con el RUC como número fiscal extranjero) y aceptar el acuerdo de distribución.
- [ ] **Estado de comerciante (UE, Digital Services Act)**: *Configuración › Detalles de la cuenta
      de desarrollador*. Como Calarmi vende compras dentro de la app, la respuesta honesta es
      **«soy comerciante»**: Google verifica y **publica en la ficha, para la UE, una dirección y un
      teléfono**. Si es una cuenta personal, piensa qué dirección quieres que salga. Si no se
      declara, la app no se puede distribuir en la UE.
- [ ] **Tarifa del 15 %**: las suscripciones pagan 15 % desde el primer día; para los pagos únicos
      (`plus_lifetime`), inscribirse en el nivel del 15 % para el primer millón de USD al año en
      *Configuración › Cuentas de desarrollador asociadas* (crear el grupo de cuentas y aceptar).
      Google ha cambiado esta estructura en algunos países en 2026: comprobar la tabla vigente.

---

## 2. Crear la app

*Inicio › Crear app*:

- [ ] **Nombre de la app:** `Calarmi`
- [ ] **Idioma predeterminado:** **Inglés (Estados Unidos) – en-US**. Es la ficha que ve quien
      tenga un idioma sin traducción (igual que en la App Store, donde el idioma principal es
      en-US). Luego se añaden es-419, pt-BR y de-DE (paso 6).
- [ ] **App o juego:** App
- [ ] **Gratis o de pago:** **Gratis**. (Una app gratuita no puede pasar a ser de pago después; las
      compras dentro de la app sí están permitidas.)
- [ ] Aceptar las declaraciones (políticas del programa para desarrolladores y leyes de exportación
      de EE. UU.).

---

## 3. Firma: Play App Signing y clave de subida

Con Play App Signing, Google guarda la clave con la que se firma la app que se instala, y tú firmas
cada AAB con una **clave de subida** que solo vive en tu Mac. Si pierdes la clave de subida, se
puede reemplazar pidiéndolo a Google; la de firma de la app no se pierde nunca porque la tiene
Google.

1. [ ] Crear la clave de subida, **fuera del repo**:
       ```sh
       mkdir -p ~/keys && keytool -genkeypair -v \
         -keystore ~/keys/calarmi-upload.jks -alias upload \
         -keyalg RSA -keysize 4096 -validity 10000
       ```
2. [ ] Crear `keystore.properties` en la raíz del repo de Android (ya está en `.gitignore`, igual
       que `*.jks`):
       ```properties
       storeFile=/Users/mathyu/keys/calarmi-upload.jks
       storePassword=…
       keyAlias=upload
       keyPassword=…
       ```
3. [ ] Guardar el `.jks` y las contraseñas en el gestor de contraseñas **y** en una copia fuera de
       la Mac.
4. [ ] Generar el AAB firmado y comprobar la firma:
       ```sh
       ./gradlew :app:bundleRelease
       keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab
       ```
       Sin `keystore.properties` el AAB sale sin firmar y Play lo rechaza.
5. [ ] Al subir el primer AAB (paso 4), aceptar **Play App Signing** con la clave que genera Google
       (la opción predeterminada).
6. [ ] Después, en *Prueba y lanza › Configuración › Integridad de la app*, anotar el
       **SHA-256 del certificado de firma de la app** (el de Google, no el de la clave de subida).
       Hará falta si algún día se añade `/.well-known/assetlinks.json` (ver `website-changes.md`).

El AAB lleva dentro el archivo de mapeo de R8, así que Play desofusca solo los informes de fallos.

---

## 4. Primer AAB en prueba interna

Va antes de los formularios porque Play **no deja crear las compras dentro de la app hasta que se
sube un AAB que tenga la biblioteca de Billing** (el permiso `com.android.vending.BILLING`).

1. [ ] *Prueba y lanza › Pruebas › Prueba interna › Crear versión*.
2. [ ] Aceptar Play App Signing (paso 3.5) y subir `app-release.aab`.
3. [ ] Nombre de la versión: `1.0.19 (1)`.
4. [ ] Notas de la versión: pegar `store/listing/release_notes_play_console.txt` entero (lleva las
       etiquetas `<es-419>…</es-419>` de cada idioma).
5. [ ] *Testers*: crear una lista de correo con tu cuenta y las de quien vaya a probar (hasta 100).
6. [ ] Guardar, revisar y lanzar. La prueba interna está disponible en minutos y no necesita
       que la ficha esté completa.

---

## 5. Contenido de la app (formularios de políticas)

*Política › Contenido de la app*. Respuestas sugeridas:

### 5.1 Política de privacidad
- [ ] URL: `https://calarmi.mathyusolutions.com/en/privacy/` (en inglés, el idioma de los
      revisores y el predeterminado de la ficha). Tiene que estar publicada con la sección de
      Android **antes** de enviar a revisión.

### 5.2 Anuncios
- [ ] **No, mi app no contiene anuncios.**

### 5.3 Acceso a la app
- [ ] **Toda la funcionalidad está disponible sin restricciones de acceso.** No hay inicio de
      sesión. (Las funciones de Plus se desbloquean pagando con Google Play, no con una cuenta, así
      que no hace falta dar credenciales.)

### 5.4 Clasificación de contenido (cuestionario IARC)
- [ ] Correo de contacto: xmathyu@gmail.com
- [ ] Categoría: **Todos los demás tipos de apps** (utilidad/productividad; no es un juego, aunque
      tenga minijuegos para apagar la alarma).
- [ ] Violencia: **No** (los retos Arcade lanzan objetos contra despertadores; no hay personajes
      heridos ni sangre). Sexualidad: **No**. Lenguaje ofensivo: **No**. Sustancias controladas:
      **No** (recordar una pastilla no es mostrar consumo de drogas). Apuestas o simulación de
      apuestas: **No**. Humor grosero: **No**.
- [ ] ¿Los usuarios pueden interactuar o intercambiar contenido entre ellos? **Sí**: se comparten
      alarmas y cuentas regresivas (con nombre, notas y foto) con quien acepta una invitación, y las
      personas de confianza administran las alarmas de otra persona. Solo por enlace de invitación,
      sin chat ni perfiles públicos. ¿Comparte la ubicación del usuario con otros? **No**. ¿Permite comprar
      bienes digitales? **Sí** (Calarmi Plus). ¿Acceso sin restricciones a internet / navegador?
      **No** (los botones «Unirse» e «Ir» abren otras apps).
- [ ] Resultado esperado: *Apto para todo público* / PEGI 3 / USK 0 / ClassInd Livre, con el
      elemento interactivo «Compras dentro de la app».

### 5.5 Público objetivo y contenido
- [ ] Grupos de edad: **13–15, 16–17 y 18 o más**. No marcar ningún grupo menor de 13: la app no
      está diseñada para niños, y marcarlos la sometería a la política de Familias.
- [ ] ¿La ficha podría atraer involuntariamente a niños? **No.**

### 5.6 Seguridad de los datos

Resumen del razonamiento (definiciones de Google: «recopilar» = que el dato salga del teléfono;
lo que se procesa solo en el teléfono no se declara):

| Dato | ¿Se declara? | Por qué |
|---|---|---|
| Alarmas, cuentas regresivas, categorías, ajustes | No | Solo en el teléfono (Room / preferencias). |
| Fotos | No | Se eligen con el selector de fotos y se copian en el almacenamiento de la app; nunca salen. |
| Tonos importados (Plus) | No | Igual que las fotos. |
| Eventos del calendario | No | Se leen en el teléfono; solo se guardan los avisos por evento, en el teléfono. |
| Pasos (reto «Caminar») | No | Sensor en el teléfono, en memoria, no se guardan. |
| Lo que se le pide al asistente | No | Gemini Nano / intérprete local, en el teléfono. |
| Código QR registrado | No | Se guarda en el teléfono. |
| **Nombre** (el de «¿Cómo te llamas?») | **Sí** | Se guarda en Firebase (`users/{uid}` y en cada share o lista de personas de confianza) para que lo vean las personas con las que se comparte. Solo si la persona comparte o acepta algo. |
| **Alarmas y cuentas regresivas compartidas** (título, notas, fechas, avisos, ícono…) | **Sí** | Las que la persona comparte y, con «Personas de confianza» activado, una copia de todas sus alarmas: Firestore (`shares/`, `invites/`, `delegations/`). |
| **Fotos** de lo compartido | **Sí** | La foto de una alarma o cuenta compartida cuyo ícono es una foto (`shares/{id}/photo/current`, ≤ 600 KB) y su miniatura en la invitación (≤ 60 KB). |
| **IDs**: uid anónimo de Firebase Auth, ID de instalación de Firebase, token de FCM | **Sí** | Para saber de quién es cada cosa y mandar los avisos push de cambios (`users/{uid}.tokens`). Solo tras compartir o aceptar algo (FCM tiene `firebase_messaging_auto_init_enabled=false`). |
| Compras (historial, datos de pago) | No | Los datos de pago los recoge Google Play directamente (excepción de servicios de pago: la app nunca los ve). La app solo pregunta a Google Play si hay una compra y guarda en el teléfono «tiene Plus». No hay servidor nuestro que reciba el historial. |
| Copia de seguridad de Google | No | Es la copia del sistema a la cuenta de Google del usuario, bajo las condiciones de Google; la app ni nosotros accedemos a ella, y en Android 9+ con bloqueo de pantalla va cifrada de extremo a extremo. Se explica en la política de privacidad. |
| **Diagnósticos de ML Kit** (asistente GenAI y escáner de códigos) | **Sí** | Las bibliotecas de Google ML Kit envían a Google información del dispositivo y de la app, métricas de rendimiento, códigos de error e identificadores por instalación, para «diagnósticos y analítica de uso» ([divulgación de datos de ML Kit](https://developers.google.com/ml-kit/android-data-disclosure)). Lo que recoge un SDK dentro de la app cuenta como recogido por la app. |

Respuestas del formulario:

- [ ] ¿Tu app recopila o comparte alguno de los tipos de datos requeridos? **Sí** (ML Kit y lo
      compartido en Firebase).
- [ ] ¿Todos los datos recopilados se encriptan en tránsito? **Sí** (ML Kit y Firebase usan HTTPS/TLS).
- [ ] ¿Qué métodos de creación de cuenta admite tu app? **Mi app no permite que los usuarios
      creen una cuenta.** (La sesión de Firebase es anónima: un uid al azar por instalación, sin
      correo, contraseña ni inicio de sesión. Si algún día se vincula a Google o Apple, esto cambia
      y Play exige un enlace para borrar la cuenta.)
- [ ] ¿Ofreces una forma de solicitar que se borren los datos? **Sí**: en la app, borrar o «Dejar de
      compartir» borra el share, su foto y anula la invitación; «Salir» quita a la persona; apagar
      «Personas de confianza» borra la lista y el espejo. El nombre y los tokens (`users/{uid}`) se
      borran a pedido por correo (xmathyu@gmail.com), como dice la política. Si Play pide una URL de
      borrado, usar la de la política de privacidad.
- [ ] Tipos de datos a marcar (Firebase actúa como proveedor de servicios: **no** cuenta como
      «compartido»; que el dato llegue a las personas que la persona invita es una transferencia
      iniciada por ella, que Google tampoco considera «compartir»):
  - **Información personal › Nombre**: recopilado **sí** · compartido **no** · efímero **no** ·
    **opcional** (solo al compartir) · finalidad **Funcionalidad de la app**.
  - **Fotos y videos › Fotos**: recopilado **sí** · compartido **no** · efímero **no** · opcional ·
    finalidad **Funcionalidad de la app**.
  - **Actividad en la app › Otro contenido generado por el usuario** (alarmas y cuentas
    compartidas): recopilado **sí** · compartido **no** · efímero **no** · opcional · finalidad
    **Funcionalidad de la app**.
  - **Información y rendimiento de la app › Diagnóstico** (ML Kit): recopilado **sí** · compartido
    **no** · procesado de forma efímera **no** · obligatorio (el usuario no puede desactivarlo) ·
    finalidad **Estadísticas**.
  - **IDs de dispositivo u otros IDs** (ML Kit; y uid anónimo, ID de instalación de Firebase y
    token de FCM): recopilado **sí** · compartido **no** · efímero **no** · obligatorio (por ML Kit)
    · finalidades **Funcionalidad de la app** y **Estadísticas**.
  - Nada más.

Antes de enviar, abrir *Política › Contenido de la app › Seguridad de los datos*: Play Console
muestra avisos si algún SDK del AAB declara en el SDK Index algo distinto. Si aparece uno, revisar
esta tabla en lugar de ignorarlo.

> Si la sección de Seguridad de los datos marca «recopila datos», la ficha no puede decir «sin
> analítica de terceros»: por eso las descripciones de `store/listing/` dicen «sin anuncios, sin
> cuenta, sin servidores nuestros» y no hablan de analítica.
>
> Con lo compartido por Firebase, «sin servidores nuestros» deja de ser exacto para lo que se
> comparte: revisar esa frase de `store/listing/*/full_description.txt` (p. ej. «tus alarmas se
> quedan en tu teléfono; lo que compartes pasa por Firebase de Google») antes de publicar esta
> versión.

### 5.7 ID de publicidad
- [ ] **No**, la app no usa el ID de publicidad (comprobar antes que el manifiesto final no tenga
      `com.google.android.gms.permission.AD_ID`, paso 0).

### 5.8 Apps gubernamentales
- [ ] **No.**

### 5.9 Funciones financieras
- [ ] **Mi app no ofrece ninguna de estas funciones financieras.** (Recordar pagos no es una
      función financiera.)

### 5.10 Apps de salud (formulario obligatorio para todas las apps)
- [ ] Respuesta pedida: **Mi app no ofrece funciones de salud.**
- **23 nov 2026: Google rechazó la versión 3** («Play Console Requirements»: ciertas apps solo
  las publican cuentas de organización) porque estaba marcada *Gestión de medicamentos y
  tratamientos*. Con una cuenta personal: **«sin funciones de salud»** y la ficha sin
  medicamentos ni pastillas (se cambiaron por hábitos, gimnasio, plantas). No volver a marcarla
  salvo que la app pase a una cuenta de organización.
- [ ] **A decidir:** la lista de Google incluye *Gestión de medicamentos y tratamientos*, descrita
      como «puede incluir identificadores de pastillas, **recordatorios de medicamentos** y gestión
      de recetas», y la ficha anuncia «Medicamentos y citas» y «la pastilla de las 8». Si el revisor
      lo ve incoherente, la salida es marcar esa categoría (la documentación adicional se pide sobre
      todo a dispositivos médicos regulados; revisarlo al marcarla) o quitar los medicamentos de la
      ficha. El reto de pasos no
      cuenta como *Actividad y fitness*: no registra actividad.

### 5.11 Otras declaraciones
- [ ] Apps de noticias: **No.** · Apps de rastreo de contactos / estado de COVID-19: **No.**

### 5.12 Declaraciones de permisos

Play Console las pide en *Contenido de la app* cuando detecta el permiso en el AAB. Los nombres de
los formularios cambian a veces; si alguno no aparece, es que Play no lo pide.

#### `USE_EXACT_ALARM` (alarmas exactas)
- [ ] Funcionalidad principal: **Despertador / alarma** (*Alarm clock*).
- [ ] Texto para pegar:
      > Calarmi is an alarm clock app. Its core functionality is user-set alarms that must ring at
      > the exact minute the user chose: wake-up alarms, medication reminders, birthdays, bills and
      > alerts before calendar events. Alarms are scheduled with AlarmManager.setAlarmClock(), are
      > shown in the system as the next alarm, ring full screen over the lock screen and are
      > rescheduled after a reboot. An inexact alarm could ring minutes late, which defeats the
      > purpose of an alarm clock.

      *(es: Calarmi es una app de despertador; su función principal son alarmas que tienen que
      sonar en el minuto exacto. Se programan con `setAlarmClock()`, aparecen como próxima alarma
      del sistema, suenan a pantalla completa y se reprograman al reiniciar. Una alarma inexacta
      podría sonar minutos tarde.)*

`SCHEDULE_EXACT_ALARM` solo se declara hasta Android 12L (`maxSdkVersion="32"`), donde
`USE_EXACT_ALARM` no existe; no tiene formulario propio.

#### Intención de pantalla completa (`USE_FULL_SCREEN_INTENT`)
- [ ] ¿La funcionalidad principal de tu app requiere intenciones de pantalla completa? Elegir
      **Funcionalidad de alarma / despertador** (*Alarm functionality*). En Android 14 o posterior,
      Play solo concede el permiso automáticamente a apps de alarmas o llamadas.
- [ ] Texto si lo pide:
      > When an alarm goes off, Calarmi shows a full-screen alarm screen over the lock screen with
      > the alarm's title, photo and Stop/Snooze buttons (or the wake-up challenge the user picked,
      > or Join/Go for calendar events), and plays the alarm sound until the user acts. This is
      > the standard alarm clock experience; a heads-up notification is easy to miss while the
      > phone is locked or the user is asleep.

#### Servicio en primer plano `systemExempted` (`FOREGROUND_SERVICE_SYSTEM_EXEMPTED`)

`RingService` corre en primer plano **solo mientras una alarma está sonando**. Android permite el
tipo `systemExempted` a las apps que tienen `SCHEDULE_EXACT_ALARM` o `USE_EXACT_ALARM`. Si el
formulario de servicios en primer plano lo lista, declararlo así (si no hay un caso de uso
predefinido que encaje, elegir *Otro*):

- [ ] Descripción de la funcionalidad:
      > The foreground service runs only while an alarm is ringing. It is started by the exact
      > alarm the user scheduled (AlarmManager.setAlarmClock), plays the alarm sound at alarm
      > volume and vibrates, keeps the full-screen alarm and its ongoing notification alive, and
      > stops as soon as the user stops or snoozes the alarm, solves the wake-up challenge, or the
      > ringing time limit is reached. It never runs in the background at other times.
- [ ] Impacto en el usuario si se pospone o se interrumpe:
      > If the system deferred or killed this service, the alarm would stop ringing or never ring:
      > the user would miss a wake-up alarm, a medication reminder or a meeting. The task cannot be
      > deferred because it must happen at the exact time the user chose.
- [ ] **Video** (enlace de YouTube «no listado» o de Google Drive con acceso «cualquiera con el
      enlace»), 1 a 2 minutos, grabado con un teléfono Android 14 o posterior y con sonido:
      1. Abrir Calarmi y crear una alarma para dentro de 1–2 minutos (mostrar la hora en pantalla).
      2. Poner el teléfono en **silencio** (mostrar el control de volumen o el ícono) y
         **bloquearlo**.
      3. Esperar: la alarma **enciende la pantalla y aparece a pantalla completa sobre la pantalla
         bloqueada**, suena y vibra; bajar la cortina para mostrar la notificación persistente de la
         alarma que suena.
      4. Tocar **Detener** (o resolver el reto) y mostrar que el sonido para y la notificación
         desaparece: el servicio terminó.
      5. Opcional: repetir con «Posponer» para mostrar que el servicio también se detiene ahí.

      Para que se oiga, grabarlo con otra cámara apuntando al teléfono, o con
      `scrcpy --record=alarma.mp4` (graba también el audio en Android 11 o posterior). La grabación
      de pantalla del propio teléfono no captura bien la pantalla de bloqueo ni el sonido de alarma.

#### `ACTIVITY_RECOGNITION`: quitado en versionCode 2

Con ese permiso en el AAB, Play exige el formulario de *Apps de salud* sin la opción «mi app no
ofrece funciones de salud» («si no tienes funciones de salud, quita el permiso»). El reto «Caminar»
cuenta ahora los pasos solo con el acelerómetro (`StepPeakDetector`), sin permisos. No volver a
añadirlo sin declarar *Actividad y fitness*.

#### `READ_CALENDAR`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `VIBRATE`, `POST_NOTIFICATIONS`
No tienen formulario en Play. `READ_CALENDAR` es opcional y de solo lectura (alarmas de los eventos
del calendario); ya está explicado en la política de privacidad.

---

## 6. Ficha de la tienda

*Crecimiento › Presencia en la tienda › Ficha principal*. Primero el idioma predeterminado
(en-US), luego *Gestionar traducciones › Añadir idiomas*: **Español (Latinoamérica) – es-419**,
**Portugués (Brasil) – pt-BR**, **Alemán – de-DE**. No usar la traducción automática de Google: los
textos están en `store/listing/`.

Por idioma (`store/listing/<idioma>/`):
- [ ] **Nombre de la app** ← `title.txt` (máx. 30)
- [ ] **Descripción breve** ← `short_description.txt` (máx. 80)
- [ ] **Descripción completa** ← `full_description.txt` (máx. 4000)

Gráficos:
- [ ] **Ícono de la app** ← `store/graphics/icon-512.png` (512 × 512, PNG de 32 bits). Es el mismo
      para todos los idiomas.
- [ ] **Gráfico de funciones** (1024 × 500, PNG de 24 bits sin alfa):
      en-US ← `feature-graphic-1024x500-en.png` · es-419 ← `feature-graphic-1024x500.png` ·
      pt-BR ← `feature-graphic-1024x500-pt.png` · de-DE ← `feature-graphic-1024x500-de.png`.
      (Sin video promocional, nada lo tapa.)
- [ ] **Capturas del teléfono** (paso 0), mínimo 2 por idioma. Las de tablet son opcionales.

*Crecimiento › Presencia en la tienda › Configuración de la ficha*:
- [ ] Categoría: **Productividad**.
- [ ] Etiquetas (hasta 5): las más cercanas a *Despertador*, *Recordatorios*, *Calendario*,
      *Cuenta regresiva*, *Productividad* que ofrezca la lista.
- [ ] Datos de contacto: correo **xmathyu@gmail.com** (obligatorio y público), web
      **https://calarmi.mathyusolutions.com**, teléfono opcional (si te declaraste comerciante en la
      UE, se mostrará el verificado).

Validar los textos antes de pegarlos (comprueba los límites y que no se cuele nada solo de iOS):
```sh
python3 store/listing/check.py
```

Recuento al 2026-11-23:

| Idioma | Nombre | Breve | Completa | Notas |
|---|---|---|---|---|
| es-419 | 30/30 | 80/80 | 3126/4000 | 486/500 |
| en-US | 26/30 | 74/80 | 2902/4000 | 431/500 |
| pt-BR | 27/30 | 80/80 | 2808/4000 | 437/500 |
| de-DE | 30/30 | 76/80 | 3291/4000 | 487/500 |

Notas sobre los textos:
- La app de Android no habla alemán (solo es, en, pt-BR): la ficha alemana lo dice al final, como
  la de iOS, y no cita botones en alemán.
- La ficha pt-BR **no menciona el asistente**, igual que en iOS: no está probado en portugués.
- Ninguna ficha promete compartir, invitar, personas de confianza, Siri, Live Activities ni iCloud.

---

## 7. Calarmi Plus: productos

Requisitos: perfil de pagos activo (paso 1) y un AAB con Billing subido a algún track (paso 4).
Los IDs tienen que ser **exactamente** los de `PlusStore.kt` y **no se pueden reutilizar** si se
borran.

### 7.1 Suscripción `plus_yearly`
*Monetizar › Productos › Suscripciones › Crear suscripción*:

- [ ] ID del producto: `plus_yearly`
- [ ] Nombre: `Calarmi Plus` (igual en todos los idiomas)
- [ ] Beneficios (aparecen en el centro de suscripciones de Play), por idioma:

| | es-419 | en-US | pt-BR | de-DE |
|---|---|---|---|---|
| 1 | Retos Arcade para despertar | Arcade wake-up challenges | Desafios Arcade para acordar | Arcade-Aufgaben zum Aufwachen |
| 2 | Tus propios tonos de alarma | Your own alarm tones | Seus próprios toques | Eigene Wecktöne |
| 3 | Cuentas regresivas sin límite | Unlimited countdowns | Contagens regressivas ilimitadas | Unbegrenzte Countdowns |
| 4 | Íconos de colores | Colored app icons | Ícones coloridos | Farbige App-Symbole |

- [ ] Descripción (si la pide): es «Retos Arcade, tus tonos, cuentas regresivas sin límite e íconos
      de colores.» · en «Arcade challenges, your own tones, unlimited countdowns and colored icons.» ·
      pt «Desafios Arcade, seus toques, contagens ilimitadas e ícones coloridos.» · de
      «Arcade-Aufgaben, eigene Töne, unbegrenzte Countdowns und farbige Symbole.»
- [ ] **Plan básico**: ID `anual` · **Renovación automática** · período de facturación **1 año** ·
      período de gracia y suspensión de cuenta con los valores predeterminados · volver a
      suscribirse: permitido. El código no usa el ID del plan básico: compra la oferta con prueba
      gratis si la persona tiene derecho a ella y, si no, el plan básico.
- [ ] Precios del plan básico: poner **9,99 USD** y dejar que Play convierta; luego ajustar a mano
      los países donde Apple ya tiene precio, para que cueste lo mismo en las dos tiendas:

| País / región | Anual (`plus_yearly`) | De por vida (`plus_lifetime`) |
|---|---|---|
| Estados Unidos | 9,99 USD | 24,99 USD |
| México | 199 MXN | 499 MXN |
| Brasil | 59,90 BRL | 149,90 BRL |
| Zona euro (Alemania, España…) | 9,99 EUR | 29,99 EUR |
| Perú | 44,90 PEN | conversión de Play (Apple: comprobar en App Store Connect) |
| Resto | conversión de Play | conversión de Play |

- [ ] **Oferta**: *Añadir oferta* en el plan `anual` · ID `prueba-1-semana` · elegibilidad
      **Adquisición de clientes nuevos › Nunca tuvo esta suscripción** · fase **Prueba gratuita de
      1 semana** · sin más fases.
- [ ] **Activar** el plan básico y la oferta (se crean como borrador).

### 7.2 Pago único `plus_lifetime`
*Monetizar › Productos › Productos únicos › Crear producto único*:

- [ ] ID del producto: `plus_lifetime`
- [ ] Nombre y descripción:

| | Nombre | Descripción |
|---|---|---|
| es-419 | Calarmi Plus de por vida | Calarmi Plus para siempre con un solo pago: retos Arcade, tus propios tonos, cuentas regresivas sin límite e íconos de colores. |
| en-US | Calarmi Plus Lifetime | Calarmi Plus forever with a single payment: Arcade challenges, your own tones, unlimited countdowns and colored icons. |
| pt-BR | Calarmi Plus vitalício | Calarmi Plus para sempre com um único pagamento: desafios Arcade, seus próprios toques, contagens regressivas ilimitadas e ícones coloridos. |
| de-DE | Calarmi Plus auf Lebenszeit | Calarmi Plus für immer mit einer einzigen Zahlung: Arcade-Aufgaben, eigene Töne, unbegrenzte Countdowns und farbige App-Symbole. |

- [ ] **Opción de compra**: tipo **Comprar**, ID `de-por-vida`, precio **24,99 USD** (y la tabla de
      arriba). Si Play Console pregunta, marcarla como **compatible con versiones anteriores**:
      `PlusStore` lee el precio con `oneTimePurchaseOfferDetails`, que devuelve justamente esa
      opción.
- [ ] Activar el producto.
- [ ] No hay que marcarlo como «no consumible»: la app solo lo **reconoce** (`acknowledgePurchase`)
      y nunca lo consume, así que se queda comprado para siempre. (Si una compra no se reconoce en
      3 días, Google la reembolsa sola; la app lo hace al recibirla y cada vez que vuelve a consultar
      las compras.)

### 7.3 Probar las compras sin pagar
- [ ] *Configuración › Prueba de licencias*: añadir las cuentas de Gmail de quien pruebe. Esas
      cuentas pagan con tarjetas de prueba, y la suscripción anual se renueva cada 30 minutos (la
      prueba gratis dura 3 minutos).
- [ ] Probar desde el track interno: comprar la anual (con prueba), cancelarla, comprar la de por
      vida, desinstalar y reinstalar, y **Restaurar compras**.
- [ ] Ojo: los testers de la prueba cerrada que **no** estén en *Prueba de licencias* pagan de
      verdad si compran.

---

## 8. Pruebas y lanzamiento

### 8.1 Prueba interna (ya creada en el paso 4)
Para el equipo. Sirve para Billing, para probar el build de release y para que Play genere el
informe previo al lanzamiento (*Prueba y lanza › Informe previo al lanzamiento*: fallos en
teléfonos reales de Google).

### 8.2 Prueba cerrada (obligatoria con cuenta personal)
- [ ] *Prueba y lanza › Pruebas › Prueba cerrada › Crear track* (o usar *Alpha*).
- [ ] Testers: una **Lista de correo** o, más cómodo, un **Grupo de Google** (p. ej.
      `calarmi-testers@googlegroups.com`) al que la gente se une sola. Apuntar a **15–20 personas**
      para tener margen sobre las 12: si alguien se sale antes de los 14 días, no cuenta, y los 14
      días tienen que ser seguidos.
- [ ] Países: los mismos que producción.
- [ ] Subir el mismo AAB (o uno nuevo con `versionCode` mayor), enviar a revisión y, cuando esté
      aprobado, mandar a los testers el **enlace de inscripción** (*Testers › Cómo se unen los
      testers*). Cada uno tiene que aceptar desde ese enlace con la cuenta de Google de su teléfono
      Android e instalar la app desde Play.
- [ ] Pedirles que la usen de verdad: crear alarmas y dejarlas sonar con el teléfono bloqueado y en
      silencio, reiniciar el teléfono con una alarma pendiente, probar retos, el calendario, las
      cuentas regresivas y los widgets, y mandar comentarios. Subir al menos una actualización
      durante la prueba con lo que reporten: Google pregunta por ello.
- [ ] Mantener a 12 o más inscritos **14 días seguidos**.

### 8.3 Solicitar acceso a producción (solo cuenta personal)
*Panel › Solicitar acceso a producción*, cuando la prueba cerrada cumpla los 14 días. Borrador de
respuestas (en inglés; ajustar con lo que haya pasado de verdad en la prueba):

- *Recruiting testers:* «Easy: friends, family and existing Calarmi users on iPhone who also use
  Android, invited through a Google Group.»
- *Did testers use all features?* «Yes: alarms (including on silent, locked and after a reboot),
  recurrence, wake-up challenges, calendar alarms, countdowns and widgets, and the Plus purchase
  flow with license testers.»
- *Feedback and how it was collected:* resumir lo que dijeron (por correo / grupo) y qué se cambió.
- *Target audience:* «Adults and teenagers who need reliable reminders: medication, birthdays,
  bills, shifts and meetings, besides waking up.»
- *Value:* «Alarms that ring at alarm volume even on silent, full screen over the lock screen, for
  everything beyond waking up; wake-up challenges; calendar events with a real alarm and
  Join/Go buttons; private by design: no account, no ads, no servers.»
- *First-year installs:* una estimación honesta (por ejemplo, la misma escala que en iOS).
- *Changes made after testing / why it's ready:* las correcciones de la prueba y que las alarmas
  sonaron a tiempo en los teléfonos de los testers.

### 8.4 Producción
- [ ] *Prueba y lanza › Producción › Países o regiones*: todos los disponibles (la UE solo si se
      completó el estado de comerciante).
- [ ] *Crear versión*: promover el AAB probado; notas ← `release_notes_play_console.txt`.
- [ ] **Lanzamiento por fases**: empezar en **20 %**, mirar *Android vitals* (fallos, ANR y, sobre
      todo, reseñas de «no sonó») 2–3 días, subir a 50 % y luego a 100 %. Se puede pausar si algo
      falla.
- [ ] La primera revisión de una app nueva puede tardar varios días (Google habla de hasta 7 o
      más). *Descripción general de la publicación* muestra el estado; con la publicación
      gestionada activada, tú decides cuándo sale una vez aprobada.

---

## 9. Archivos de `store/` y dónde va cada uno

| Archivo | Dónde va en Play Console |
|---|---|
| `listing/<idioma>/title.txt` | Ficha principal › Nombre de la app (por idioma) |
| `listing/<idioma>/short_description.txt` | Ficha principal › Descripción breve |
| `listing/<idioma>/full_description.txt` | Ficha principal › Descripción completa |
| `listing/<idioma>/release_notes.txt` | Notas de la versión de ese idioma (fuente) |
| `listing/release_notes_play_console.txt` | Crear versión › Notas de la versión (se pega entero, con las etiquetas); lo genera `check.py` |
| `listing/check.py` | Nada: valida longitudes y palabras de iOS antes de pegar |
| `graphics/icon-512.png` | Ficha principal › Gráficos › Ícono de la app |
| `graphics/feature-graphic-1024x500.png` | Ficha principal › Gráficos › Gráfico de funciones (es-419) |
| `graphics/feature-graphic-1024x500-en.png` / `-pt` / `-de` | Gráfico de funciones de en-US / pt-BR / de-DE |
| `graphics/make_graphics.swift` | Nada: regenera los PNG (`swift store/graphics/make_graphics.swift`) |
| `graphics/fonts/` | Nada: Nunito (licencia OFL, `Nunito-OFL.txt`), la letra de la web, para `make_graphics.swift` |
| `privacy-policy-android.md` | En la web (las URL de *Contenido de la app › Política de privacidad*) |
| `website-changes.md` | En la web (botón de Google Play, ayuda, invitaciones) |
| `play-console.md` | Esta lista |

---

## 10. Decisiones pendientes

1. **Tipo de cuenta** (paso 1): organización con D-U-N-S (sin prueba de 14 días) o personal (con
   12 testers × 14 días).
2. **Estado de comerciante en la UE**: qué dirección y teléfono se publican.
3. **Formulario de salud** (5.10): «sin funciones de salud» como se pidió, o *Gestión de
   medicamentos y tratamientos*, porque la ficha anuncia recordatorios de pastillas.
4. **Seguridad de los datos** (5.6): declarar los diagnósticos de ML Kit (recomendado, es lo que
   dice Google que hacen esas bibliotecas) o «no recopila datos». Lo segundo solo sería exacto sin
   ML Kit GenAI ni el escáner de códigos.
5. **El asistente en portugués y alemán**: la ficha pt-BR no lo menciona y la alemana dice que
   entiende inglés y español. Cambiarlo cuando se pruebe en un teléfono Android con cada idioma.
6. **Idioma predeterminado de la ficha**: en-US, como en la App Store, o es-419 si se prefiere que
   el resto del mundo vea la ficha en español.
7. **Precio de por vida en Perú**: Apple no lo tiene anotado en `AppStoreMetadata.md`.
