# Política de privacidad: sección para Android (propuesta)

Texto propuesto para las páginas de privacidad de la web (`/privacidad/`, `/en/privacy/`,
`/pt/privacidade/`, `/de/datenschutz/`, que salen de `calarm-web/src/components/Privacy.astro` con
las claves `pv.*` de `src/i18n/ui.ts`). **No se ha tocado la web**: esto es para copiarlo allá.

Google Play revisa que la URL de la política de privacidad hable de la app que publica, por eso la
sección de Android tiene que estar publicada **antes** de enviar la app a revisión (ver
`play-console.md`, paso 5).

Describe solo lo que hace la versión 1.0.19 (versionCode 1) para Android, según el código de
`app/`:

- Datos solo en el teléfono (Room + archivos privados de la app); sin cuenta, sin servidores
  nuestros, sin anuncios, sin ID de publicidad.
- No hay iCloud, ni sincronización entre dispositivos, ni alarmas o cuentas regresivas
  compartidas, ni invitaciones, ni personas de confianza.
- Copia de seguridad de Google activada en el manifiesto (`allowBackup`, `data_extraction_rules.xml`):
  base de datos, preferencias y archivos (fotos y tonos), menos `alarm_state.xml`.
- Permisos: notificaciones, alarmas exactas, pantalla completa, servicio en primer plano,
  arranque, calendario (opcional, solo lectura) y reconocimiento de actividad (reto «Caminar»).
- Fotos con el selector de fotos de Android (sin permiso de galería) y tonos con el selector de
  archivos (sin permiso de almacenamiento); el reto QR con el escáner de códigos de Google Play
  services (sin permiso de cámara).
- Asistente en el dispositivo: Gemini Nano (ML Kit GenAI Prompt API, vía AICore) donde está
  disponible, y un intérprete local en el resto.
- Compras con Google Play Billing.
- Las bibliotecas de Google ML Kit (GenAI y escáner de códigos) envían a Google datos técnicos de
  diagnóstico, según la [divulgación de datos de ML Kit](https://developers.google.com/ml-kit/android-data-disclosure).
  Es lo único que sale del teléfono por culpa de un SDK, y por eso aparece en la sección
  «Seguridad de los datos» de Play (ver `play-console.md`).

## Cambios necesarios en el texto actual (los cuatro idiomas)

El texto de hoy está escrito solo para iPhone. Con la app de Android publicada, estas frases quedan
inexactas:

| Clave | Hoy (es) | Propuesta (es) |
|---|---|---|
| `pv.lead` | «…funcionar por completo en tu dispositivo…» | Sin cambios (ya vale para los dos). |
| `pv.tldr3` | «Sin anuncios, sin analítica de terceros y sin identificadores publicitarios.» | «Sin anuncios, sin rastreo y sin identificadores publicitarios.» (en Android, ML Kit de Google envía diagnósticos técnicos; «sin analítica de terceros» dejaría de ser exacto) |
| `pv.tldr4` | «Tus alarmas, fotos y eventos se quedan en tu iPhone.» | «Tus alarmas, fotos y eventos se quedan en tu teléfono.» |
| `pv.s1_p` | «…revocarlo en cualquier momento desde Ajustes de iOS.» | «…revocarlo en cualquier momento desde los ajustes de tu teléfono.» |
| `pv.s1b_p` | «…lee los eventos de la app Calendario de Apple…» | «…lee los eventos del calendario de tu teléfono (Calendario de Apple en iPhone; Google Calendar u otros calendarios sincronizados en Android)…» |
| `pv.s1c_h` / `pv.s1c_p` | Solo AlarmKit | Añadir al final: «En Android, Calarmi usa las alarmas exactas del sistema (ver la sección de Android).» |
| `pv.s2_p` | «Esta tabla corresponde a la sección "Privacidad de la app" en App Store Connect.» | «Esta tabla corresponde a la sección "Privacidad de la app" del App Store. Para Android, ver la tabla de la sección "Calarmi en Android".» |
| `pv.s3_p` | «…se guarda localmente usando SwiftData…» / compras con Apple | Dejarlo como está para iPhone y que la sección de Android cubra su caso. |
| `pv.s5_p` | «Calarmi tiene clasificación 4+…» | «Calarmi es apta para todas las edades (4+ en el App Store; "Apto para todo público" o equivalente en Google Play), aunque no está dirigida a niños. No recolectamos conscientemente…» |
| `pv.updated` | 11 de noviembre de 2026 | La fecha en que se publique la sección de Android. |

Además, el nombre que se pide en iOS desde la 1.0.18 (se guarda en la base pública de CloudKit,
ver `AppStoreMetadata.md` › Privacy) todavía no aparece en la política. No es de Android, pero
conviene añadirlo en el mismo cambio.

Estructura sugerida: una sección nueva `<section id="android">` después de la 4 («Compartir con
personas de confianza»), con claves `pv.and_*`, y renumerar las que siguen (5 → 6, etc.). La
tabla de Android usa las mismas columnas que la de iOS (`pv.th1`…`pv.th4`), con «Sale del
teléfono» en lugar de «Vinculado a ti».

---

## Español (es)

### 5. Calarmi en Android

Todo lo anterior vale también para Calarmi en Android, con estas diferencias.

**Dónde se guardan tus datos.** Tus alarmas, cuentas regresivas, categorías, ajustes, fotos y
tonos importados se guardan en el almacenamiento privado de la app, en tu teléfono. En Android no
hay sincronización con iCloud ni con ningún servidor nuestro, y todavía no se pueden compartir
alarmas ni cuentas regresivas, invitar a otras personas ni tener personas de confianza.

**Permisos.** Puedes revisarlos y quitarlos en Ajustes de Android › Apps › Calarmi.

- *Notificaciones, Alarmas y recordatorios, y Alarmas a pantalla completa:* para que cada alarma
  suene a su hora exacta, al volumen de alarma, y aparezca sobre la pantalla bloqueada. Mientras
  suena, un servicio en primer plano mantiene el sonido. Calarmi también vuelve a programar tus
  alarmas cuando reinicias el teléfono, incluso antes de desbloquearlo.
- *Calendario (opcional, solo lectura):* si activas las alarmas del calendario, Calarmi lee los
  eventos de los calendarios sincronizados en tu teléfono (Google Calendar u otros) para ponerles
  alarma y detectar enlaces de Teams, Zoom o Google Meet y direcciones. No modifica tus eventos y
  no los envía fuera del teléfono. Solo guarda, en el teléfono, los avisos que elijas para cada
  evento.
- *Actividad física (opcional):* solo para el reto «Caminar», que cuenta tus pasos con el sensor
  del teléfono mientras suena la alarma. Los pasos no se guardan ni se envían.
- *Fotos y audio:* Calarmi no pide acceso a tu galería ni a tus archivos. Cuando eliges una foto o
  un tono, Android te muestra su propio selector y Calarmi recibe solo lo que elegiste; guarda una
  copia (la foto, reducida) dentro de la app.
- *Cámara:* Calarmi no pide permiso de cámara. El reto del código QR usa el escáner de códigos de
  Google Play services, que lee el código en el teléfono y le devuelve a Calarmi solo su contenido.
  Calarmi guarda en el teléfono el código que registras, para compararlo.

**Asistente.** El asistente funciona dentro del teléfono: en los teléfonos compatibles usa
Gemini Nano, el modelo de Google que corre en el dispositivo; en los demás, un intérprete más
sencillo integrado en la app. Lo que le pides no se envía a nosotros ni, por parte de Calarmi, a
ningún servidor.

**Diagnósticos de las bibliotecas de Google.** El asistente (ML Kit GenAI) y el escáner de
códigos (ML Kit) son bibliotecas de Google que pueden enviar a Google datos técnicos de
diagnóstico y uso: modelo y versión del sistema del teléfono, versión de la app, rendimiento,
errores, tamaño de las peticiones y un identificador por instalación que no te identifica a ti.
No incluyen el texto que escribes ni tus alarmas. Google los usa para medir y mejorar esas
bibliotecas, según su [divulgación de datos de ML Kit](https://developers.google.com/ml-kit/android-data-disclosure).
Nosotros no recibimos esos datos.

**Copia de seguridad de Google.** Si tienes activada la copia de seguridad de Android, el sistema
puede incluir los datos de Calarmi (alarmas, cuentas regresivas, ajustes, fotos y tonos) en la
copia de tu cuenta de Google y restaurarlos en un teléfono nuevo. Esa copia la gestiona Google en
tu cuenta, y nosotros no tenemos acceso a ella. Según Google, en Android 9 o posterior con bloqueo
de pantalla se cifra con tu PIN, patrón o contraseña. Puedes desactivarla en los ajustes de
Android.

**Compras de Calarmi Plus.** Las procesa Google Play. No recibimos tu nombre, tu correo ni tus datos
de pago: la app solo le pregunta a Google Play si tienes una compra activa y guarda en el teléfono
si tienes Plus. La suscripción se gestiona y cancela en Google Play › Pagos y suscripciones. A esos
datos se les aplica la [Política de Privacidad de Google](https://policies.google.com/privacy).

**Lo que Calarmi para Android no hace.** No muestra anuncios, no usa el ID de publicidad, no te
pide una cuenta, no accede a tu ubicación, tus contactos ni tu micrófono, y no tiene servidores
nuestros que guarden tus datos.

**Borrar tus datos.** Borra una alarma o una cuenta regresiva en la app y desaparece del teléfono.
Para borrarlo todo, desinstala Calarmi o borra su almacenamiento en Ajustes de Android › Apps ›
Calarmi. La copia de seguridad de Google se gestiona en tu cuenta de Google.

| Tipo de dato | Uso | Sale del teléfono | Tracking |
|---|---|---|---|
| Fotos y tonos que eliges | Mostrar la foto de una alarma o una cuenta regresiva; hacer sonar tu tono. Guardados en el teléfono. | No (salvo en tu copia de seguridad de Google, si la tienes activada) | No |
| Eventos del calendario (lectura) | Poner alarma a tus eventos y detectar reuniones y direcciones. | No | No |
| Pasos (reto «Caminar») | Contar pasos mientras suena la alarma. No se guardan. | No | No |
| Lo que le pides al asistente | Crear o cambiar alarmas, en el teléfono. | No | No |
| Diagnósticos técnicos de ML Kit | Que Google mida y mejore sus bibliotecas. No los recibimos. | Sí, a Google | No |
| Compras de Calarmi Plus | Saber si tienes Plus. Las procesa Google Play. | Las gestiona Google Play | No |

---

## English (en)

### 5. Calarmi on Android

Everything above also applies to Calarmi on Android, with these differences.

**Where your data is stored.** Your alarms, countdowns, categories, settings, photos and imported
tones are stored in the app's private storage, on your phone. On Android there is no sync with
iCloud or with any server of ours, and you can't yet share alarms or countdowns, invite other
people or have trusted helpers.

**Permissions.** You can review and remove them in Android Settings › Apps › Calarmi.

- *Notifications, Alarms & reminders, and Full-screen alarms:* so every alarm rings at its exact
  time, at alarm volume, and shows over the lock screen. While it rings, a foreground service keeps
  the sound going. Calarmi also schedules your alarms again when you restart your phone, even
  before you unlock it.
- *Calendar (optional, read only):* if you turn on calendar alarms, Calarmi reads the events from
  the calendars synced to your phone (Google Calendar or others) to give them alarms and detect
  Teams, Zoom or Google Meet links and addresses. It doesn't modify your events and doesn't send
  them off the phone. It only stores, on the phone, the alerts you choose for each event.
- *Physical activity (optional):* only for the "Walk" challenge, which counts your steps with the
  phone's sensor while the alarm rings. Steps are neither stored nor sent anywhere.
- *Photos and audio:* Calarmi doesn't ask for access to your gallery or your files. When you pick a
  photo or a tone, Android shows you its own picker and Calarmi only receives what you picked; it
  keeps a copy (the photo, downsized) inside the app.
- *Camera:* Calarmi doesn't ask for camera permission. The QR code challenge uses the Google Play
  services code scanner, which reads the code on your phone and hands Calarmi only its contents.
  Calarmi stores the code you register on your phone, to compare against it.

**Assistant.** The assistant runs on your phone: on supported phones it uses Gemini Nano, Google's
on-device model; on the rest, a simpler parser built into the app. What you ask it is never sent
to us or, by Calarmi, to any server.

**Diagnostics from Google's libraries.** The assistant (ML Kit GenAI) and the code scanner
(ML Kit) are Google libraries that may send Google technical diagnostics and usage data: the
phone's model and OS version, the app version, performance, errors, the size of requests and a
per-installation identifier that doesn't identify you. They don't include the text you type or
your alarms. Google uses them to measure and improve those libraries, as described in its
[ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure). We don't
receive this data.

**Google backup.** If Android backup is turned on, the system may include Calarmi's data (alarms,
countdowns, settings, photos and tones) in the backup to your Google Account and restore it on a
new phone. Google manages that backup in your account, and we have no access to it. According to
Google, on Android 9 or later with a screen lock it's encrypted with your PIN, pattern or password.
You can turn it off in Android settings.

**Calarmi Plus purchases.** Google Play processes them. We never receive your name, email or
payment details: the app only asks Google Play whether you have an active purchase and stores on
the phone whether you have Plus. You manage and cancel the subscription in Google Play › Payments &
subscriptions. [Google's Privacy Policy](https://policies.google.com/privacy) applies to that data.

**What Calarmi for Android doesn't do.** It shows no ads, doesn't use the advertising ID, doesn't
ask for an account, doesn't access your location, contacts or microphone, and has no servers of
ours storing your data.

**Deleting your data.** Delete an alarm or a countdown in the app and it's gone from the phone. To
delete everything, uninstall Calarmi or clear its storage in Android Settings › Apps › Calarmi. The
Google backup is managed in your Google Account.

| Data type | Use | Leaves the phone | Tracking |
|---|---|---|---|
| Photos and tones you pick | Show an alarm's or a countdown's photo; play your tone. Stored on the phone. | No (except in your Google backup, if it's on) | No |
| Calendar events (read) | Give your events alarms and detect meetings and addresses. | No | No |
| Steps ("Walk" challenge) | Count steps while the alarm rings. Not stored. | No | No |
| What you ask the assistant | Create or change alarms, on the phone. | No | No |
| ML Kit technical diagnostics | Let Google measure and improve its libraries. We don't receive them. | Yes, to Google | No |
| Calarmi Plus purchases | Know whether you have Plus. Processed by Google Play. | Handled by Google Play | No |

---

## Português (pt)

### 5. Calarmi no Android

Tudo o que está acima também vale para o Calarmi no Android, com estas diferenças.

**Onde seus dados ficam.** Seus alarmes, contagens regressivas, categorias, ajustes, fotos e
toques importados ficam no armazenamento privado do app, no seu celular. No Android não há
sincronização com o iCloud nem com nenhum servidor nosso, e ainda não é possível compartilhar
alarmes ou contagens regressivas, convidar outras pessoas nem ter pessoas de confiança.

**Permissões.** Você pode revisá-las e removê-las em Configurações do Android › Apps › Calarmi.

- *Notificações, Alarmes e lembretes, e Alarmes em tela cheia:* para que cada alarme toque na hora
  exata, no volume de alarme, e apareça sobre a tela bloqueada. Enquanto toca, um serviço em
  primeiro plano mantém o som. O Calarmi também agenda seus alarmes de novo quando você reinicia o
  celular, mesmo antes de desbloquear.
- *Agenda (opcional, somente leitura):* se você ativar os alarmes da agenda, o Calarmi lê os
  eventos das agendas sincronizadas no seu celular (Google Agenda ou outras) para colocar alarme
  neles e detectar links do Teams, Zoom ou Google Meet e endereços. Não modifica seus eventos e não
  os envia para fora do celular. Só guarda, no celular, os avisos que você escolher para cada
  evento.
- *Atividade física (opcional):* só para o desafio "Caminhar", que conta seus passos com o sensor do
  celular enquanto o alarme toca. Os passos não são guardados nem enviados.
- *Fotos e áudio:* o Calarmi não pede acesso à sua galeria nem aos seus arquivos. Quando você
  escolhe uma foto ou um toque, o Android mostra o próprio seletor e o Calarmi recebe só o que você
  escolheu; ele guarda uma cópia (a foto, reduzida) dentro do app.
- *Câmera:* o Calarmi não pede permissão de câmera. O desafio do código QR usa o leitor de códigos
  do Google Play Services, que lê o código no celular e devolve ao Calarmi só o conteúdo dele. O
  Calarmi guarda no celular o código que você registrar, para comparar.

**Assistente.** O assistente funciona dentro do celular: nos aparelhos compatíveis usa o Gemini
Nano, o modelo do Google que roda no dispositivo; nos demais, um interpretador mais simples
integrado ao app. O que você pede não é enviado para nós nem, por parte do Calarmi, para nenhum
servidor.

**Diagnósticos das bibliotecas do Google.** O assistente (ML Kit GenAI) e o leitor de códigos
(ML Kit) são bibliotecas do Google que podem enviar ao Google dados técnicos de diagnóstico e uso:
modelo e versão do sistema do celular, versão do app, desempenho, erros, tamanho das solicitações e
um identificador por instalação que não identifica você. Não incluem o texto que você digita nem
seus alarmes. O Google os usa para medir e melhorar essas bibliotecas, conforme a
[divulgação de dados do ML Kit](https://developers.google.com/ml-kit/android-data-disclosure). Nós
não recebemos esses dados.

**Backup do Google.** Se o backup do Android estiver ativado, o sistema pode incluir os dados do
Calarmi (alarmes, contagens regressivas, ajustes, fotos e toques) no backup da sua Conta do Google
e restaurá-los em um celular novo. Esse backup é gerenciado pelo Google na sua conta, e nós não
temos acesso a ele. Segundo o Google, no Android 9 ou posterior com bloqueio de tela, ele é
criptografado com seu PIN, padrão ou senha. Você pode desativá-lo nas configurações do Android.

**Compras do Calarmi Plus.** Quem processa é o Google Play. Não recebemos seu nome, seu e-mail nem
seus dados de pagamento: o app só pergunta ao Google Play se você tem uma compra ativa e guarda no
celular se você tem o Plus. A assinatura é gerenciada e cancelada no Google Play › Pagamentos e
assinaturas. A esses dados se aplica a [Política de Privacidade do Google](https://policies.google.com/privacy).

**O que o Calarmi para Android não faz.** Não mostra anúncios, não usa o ID de publicidade, não pede
conta, não acessa sua localização, seus contatos nem seu microfone, e não tem servidores nossos
guardando seus dados.

**Apagar seus dados.** Apague um alarme ou uma contagem regressiva no app e ele some do celular.
Para apagar tudo, desinstale o Calarmi ou limpe o armazenamento dele em Configurações do Android ›
Apps › Calarmi. O backup do Google é gerenciado na sua Conta do Google.

| Tipo de dado | Uso | Sai do celular | Rastreamento |
|---|---|---|---|
| Fotos e toques que você escolhe | Mostrar a foto de um alarme ou de uma contagem regressiva; tocar seu toque. Guardados no celular. | Não (exceto no seu backup do Google, se estiver ativado) | Não |
| Eventos da agenda (leitura) | Colocar alarme nos seus eventos e detectar reuniões e endereços. | Não | Não |
| Passos (desafio "Caminhar") | Contar passos enquanto o alarme toca. Não são guardados. | Não | Não |
| O que você pede ao assistente | Criar ou mudar alarmes, no celular. | Não | Não |
| Diagnósticos técnicos do ML Kit | O Google medir e melhorar suas bibliotecas. Nós não os recebemos. | Sim, para o Google | Não |
| Compras do Calarmi Plus | Saber se você tem o Plus. Processadas pelo Google Play. | Gerenciadas pelo Google Play | Não |

---

## Deutsch (de)

Pendiente: la sección alemana se puede traducir de la inglesa cuando se haga el cambio en la web.
La app de Android no está en alemán, pero la página `/de/datenschutz/` existe y Play muestra la
misma URL de política a todos los idiomas, así que conviene que también la tenga.

## Puntos que hay que confirmar antes de publicar

1. **Nombres de los retos y de los permisos.** El texto usa los de `strings_core.xml` a
   2026-11-23 («Caminar» / "Walk" / "Caminhar"; «Alarmas y recordatorios», «Alarmas a pantalla
   completa»). Si cambian en la app, cambiarlos aquí.
2. **Diagnósticos de ML Kit.** La lista de datos sale de la página de divulgación de ML Kit
   (consultada el 2026-11-23). Si Google la cambia, o si se quita alguna de esas bibliotecas,
   actualizar el párrafo y la tabla, y la sección «Seguridad de los datos» de Play a la vez.
3. **Auto-zoom del escáner.** ML Kit solo envía datos de la sesión de escaneo si se activa
   `enableAutoZoom()` en `GmsBarcodeScannerOptions`. Si el reto QR lo activa, el párrafo ya lo
   cubre («datos técnicos de diagnóstico y uso»).
