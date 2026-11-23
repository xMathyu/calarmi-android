# Política de privacidad: sección para Android (propuesta)

Texto propuesto para las páginas de privacidad de la web (`/privacidad/`, `/en/privacy/`,
`/pt/privacidade/`, `/de/datenschutz/`, que salen de `calarm-web/src/components/Privacy.astro` con
las claves `pv.*` de `src/i18n/ui.ts`). **No se ha tocado la web**: esto es para copiarlo allá.

Google Play revisa que la URL de la política de privacidad hable de la app que publica, por eso la
sección de Android tiene que estar publicada **antes** de enviar la app a revisión (ver
`play-console.md`, paso 5).

Describe lo que hace Calarmi para Android con lo compartido por Firebase (rama `firebase-sharing`),
según el código de `app/`:

- Lo propio sigue solo en el teléfono (Room + archivos privados de la app); sin cuenta con
  contraseña, sin anuncios, sin ID de publicidad.
- **Lo compartido vive en Firebase** (proyecto `calarmi` de Google Cloud, Firestore en `nam5`,
  Cloud Functions en `us-central1`; contrato en `calarmi-firebase/DESIGN.md`): las alarmas y
  cuentas regresivas que la persona comparte (con su foto, si tiene), las invitaciones, la lista
  de personas de confianza y el espejo de sus alarmas mientras la delegación está activa, el
  nombre que ve la otra gente y los tokens de push (FCM). Todo eso solo existe si la persona
  comparte, acepta una invitación o activa «Personas de confianza»: hasta entonces la app no abre
  sesión en Firebase ni genera token de push.
- La sesión es **anónima** (Firebase Authentication): un identificador al azar por instalación,
  sin correo ni contraseña.
- Sin iCloud ni sincronización entre dispositivos propios.
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
hay sincronización con iCloud. Lo único que sale del teléfono es lo que decides compartir (abajo).

**Compartir y personas de confianza (Firebase).** Para compartir una alarma o una cuenta
regresiva, aceptar una invitación o tener personas de confianza, Calarmi usa Firebase, un servicio
de Google Cloud (Firestore para los datos, Cloud Functions para avisar a los otros teléfonos y
Firebase Cloud Messaging para los avisos push), en servidores de Google en Estados Unidos. Solo
se usa cuando tú haces una de esas cosas; si nunca compartes nada, Calarmi no se conecta a
Firebase.

- *Tu nombre:* el que escribes en «¿Cómo te llamas?» (o en Ajustes › Tu nombre). Lo ven las
  personas con las que compartes y las que comparten contigo.
- *Lo que compartes:* el contenido de cada alarma o cuenta regresiva que compartes (nombre,
  notas, fechas y horarios, repetición, avisos, categoría, ícono, reto, tono de la app y, si su
  ícono es una foto, esa foto reducida), y una invitación con una vista previa (nombre, fecha,
  zona horaria, idioma, color e ícono, y una miniatura de la foto) que puede leer cualquiera que
  tenga el enlace. Lo ven las personas que aceptan tu invitación. Tus avisos personales de una
  alarma que te compartieron se quedan en tu teléfono.
- *Personas de confianza:* si las activas, una copia de todas tus alarmas (sin fotos) y quiénes
  pueden administrarlas. Si administras las alarmas de otra persona, lo que creas o cambias se
  guarda en su lista, no en tu teléfono.
- *Identificadores:* un identificador anónimo de Firebase Authentication (sin correo ni
  contraseña), el identificador de instalación de Firebase y el token de Firebase Cloud Messaging
  de tu teléfono, con el idioma de la app y la plataforma, para avisar a tu teléfono cuando algo
  compartido cambia.

No vendemos estos datos ni los usamos para publicidad; solo sirven para que lo compartido funcione.
Google los procesa por nosotros según los [términos de Firebase](https://firebase.google.com/terms/data-processing-terms)
y su [Política de Privacidad](https://policies.google.com/privacy).

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
pide una cuenta con correo o contraseña, no accede a tu ubicación, tus contactos ni tu micrófono,
y fuera de lo que compartes no guarda tus datos en ningún servidor.

**Borrar tus datos.** Borra una alarma o una cuenta regresiva en la app y desaparece del teléfono.
Si estaba compartida, borrarla o tocar «Dejar de compartir» la borra también de Firebase (con su
foto) y anula su invitación; «Salir» de algo que te compartieron te quita de la lista. Apagar
«Personas de confianza» borra la copia de tus alarmas y la lista. Para borrar también tu nombre y
tus identificadores de Firebase, escríbenos a xmathyu@gmail.com. Para borrar todo lo del teléfono,
desinstala Calarmi o borra su almacenamiento en Ajustes de Android › Apps › Calarmi. La copia de
seguridad de Google se gestiona en tu cuenta de Google.

| Tipo de dato | Uso | Sale del teléfono | Tracking |
|---|---|---|---|
| Fotos y tonos que eliges | Mostrar la foto de una alarma o una cuenta regresiva; hacer sonar tu tono. Guardados en el teléfono. | No (salvo en tu copia de seguridad de Google, si la tienes activada) | No |
| Eventos del calendario (lectura) | Poner alarma a tus eventos y detectar reuniones y direcciones. | No | No |
| Pasos (reto «Caminar») | Contar pasos mientras suena la alarma. No se guardan. | No | No |
| Lo que le pides al asistente | Crear o cambiar alarmas, en el teléfono. | No | No |
| Diagnósticos técnicos de ML Kit | Que Google mida y mejore sus bibliotecas. No los recibimos. | Sí, a Google | No |
| Compras de Calarmi Plus | Saber si tienes Plus. Las procesa Google Play. | Las gestiona Google Play | No |
| Tu nombre (si compartes) | Que te reconozcan quienes comparten contigo. | Sí, a Firebase (Google Cloud) | No |
| Alarmas y cuentas regresivas que compartes, con su foto | Que les lleguen a quienes invitas, con cada cambio. | Sí, a Firebase (Google Cloud) | No |
| Alarmas reflejadas para tus personas de confianza | Que puedan administrarlas. | Sí, a Firebase (Google Cloud) | No |
| Identificador anónimo, ID de instalación y token de push | Avisar a tu teléfono cuando algo compartido cambia. | Sí, a Firebase (Google Cloud) | No |

---

## English (en)

### 5. Calarmi on Android

Everything above also applies to Calarmi on Android, with these differences.

**Where your data is stored.** Your alarms, countdowns, categories, settings, photos and imported
tones are stored in the app's private storage, on your phone. On Android there is no iCloud sync.
The only thing that leaves the phone is what you choose to share (below).

**Sharing and trusted helpers (Firebase).** To share an alarm or a countdown, accept an invitation
or have trusted helpers, Calarmi uses Firebase, a Google Cloud service (Firestore for the data,
Cloud Functions to notify the other phones and Firebase Cloud Messaging for push notifications),
on Google servers in the United States. It's only used when you do one of those things; if you
never share anything, Calarmi doesn't connect to Firebase.

- *Your name:* the one you type in "What's your name?" (or in Settings › Your name). The people
  you share with, and those who share with you, see it.
- *What you share:* the contents of each alarm or countdown you share (title, notes, dates and
  times, repeat, alerts, category, icon, challenge, built-in tone and, if its icon is a photo, that
  photo downsized), plus an invitation with a preview (title, date, time zone, language, color and
  icon, and a photo thumbnail) that anyone with the link can read. The people who accept your
  invitation see it. Your own alerts on an alarm someone shared with you stay on your phone.
- *Trusted helpers:* if you turn them on, a copy of all your alarms (without photos) and who can
  manage them. If you manage someone else's alarms, what you create or change is stored in their
  list, not on your phone.
- *Identifiers:* an anonymous Firebase Authentication identifier (no email or password), the
  Firebase installation ID and your phone's Firebase Cloud Messaging token, with the app language
  and platform, to notify your phone when something shared changes.

We don't sell this data or use it for advertising; it only makes sharing work. Google processes it
on our behalf under the [Firebase terms](https://firebase.google.com/terms/data-processing-terms)
and its [Privacy Policy](https://policies.google.com/privacy).

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
ask for an account with an email or password, doesn't access your location, contacts or
microphone, and apart from what you share it stores your data on no server.

**Deleting your data.** Delete an alarm or a countdown in the app and it's gone from the phone. If
it was shared, deleting it or tapping "Stop sharing" also deletes it from Firebase (with its photo)
and voids its invitation; "Leave" on something shared with you takes you off its list. Turning off
"Trusted helpers" deletes the copy of your alarms and the list. To also delete your name and your
Firebase identifiers, write to xmathyu@gmail.com. To delete everything on the phone, uninstall
Calarmi or clear its storage in Android Settings › Apps › Calarmi. The Google backup is managed in
your Google Account.

| Data type | Use | Leaves the phone | Tracking |
|---|---|---|---|
| Photos and tones you pick | Show an alarm's or a countdown's photo; play your tone. Stored on the phone. | No (except in your Google backup, if it's on) | No |
| Calendar events (read) | Give your events alarms and detect meetings and addresses. | No | No |
| Steps ("Walk" challenge) | Count steps while the alarm rings. Not stored. | No | No |
| What you ask the assistant | Create or change alarms, on the phone. | No | No |
| ML Kit technical diagnostics | Let Google measure and improve its libraries. We don't receive them. | Yes, to Google | No |
| Calarmi Plus purchases | Know whether you have Plus. Processed by Google Play. | Handled by Google Play | No |
| Your name (if you share) | Let the people who share with you recognize you. | Yes, to Firebase (Google Cloud) | No |
| Alarms and countdowns you share, with their photo | Deliver them, and every change, to the people you invite. | Yes, to Firebase (Google Cloud) | No |
| Alarms mirrored for your trusted helpers | Let them manage them. | Yes, to Firebase (Google Cloud) | No |
| Anonymous identifier, installation ID and push token | Notify your phone when something shared changes. | Yes, to Firebase (Google Cloud) | No |

---

## Português (pt)

### 5. Calarmi no Android

Tudo o que está acima também vale para o Calarmi no Android, com estas diferenças.

**Onde seus dados ficam.** Seus alarmes, contagens regressivas, categorias, ajustes, fotos e
toques importados ficam no armazenamento privado do app, no seu celular. No Android não há
sincronização com o iCloud. A única coisa que sai do celular é o que você decide compartilhar
(abaixo).

**Compartilhar e pessoas de confiança (Firebase).** Para compartilhar um alarme ou uma contagem
regressiva, aceitar um convite ou ter pessoas de confiança, o Calarmi usa o Firebase, um serviço
do Google Cloud (Firestore para os dados, Cloud Functions para avisar os outros celulares e
Firebase Cloud Messaging para as notificações push), em servidores do Google nos Estados Unidos.
Ele só é usado quando você faz uma dessas coisas; se você nunca compartilhar nada, o Calarmi não
se conecta ao Firebase.

- *Seu nome:* o que você digita em "Qual é o seu nome?" (ou em Ajustes › Seu nome). As pessoas
  com quem você compartilha, e as que compartilham com você, o veem.
- *O que você compartilha:* o conteúdo de cada alarme ou contagem regressiva que você compartilha
  (nome, notas, datas e horários, repetição, alertas, categoria, ícone, desafio, toque do app e, se
  o ícone for uma foto, essa foto reduzida), e um convite com uma prévia (nome, data, fuso horário,
  idioma, cor e ícone, e uma miniatura da foto) que qualquer pessoa com o link pode ler. Quem
  aceita seu convite vê isso. Seus alertas pessoais em um alarme que compartilharam com você ficam
  no seu celular.
- *Pessoas de confiança:* se você ativar, uma cópia de todos os seus alarmes (sem fotos) e quem
  pode gerenciá-los. Se você gerencia os alarmes de outra pessoa, o que você cria ou altera fica
  na lista dela, não no seu celular.
- *Identificadores:* um identificador anônimo do Firebase Authentication (sem e-mail nem senha),
  o ID de instalação do Firebase e o token do Firebase Cloud Messaging do seu celular, com o idioma
  do app e a plataforma, para avisar seu celular quando algo compartilhado muda.

Não vendemos esses dados nem os usamos para publicidade; eles só servem para o compartilhamento
funcionar. O Google os processa em nosso nome conforme os [termos do Firebase](https://firebase.google.com/terms/data-processing-terms)
e a [Política de Privacidade](https://policies.google.com/privacy) dele.

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
conta com e-mail ou senha, não acessa sua localização, seus contatos nem seu microfone, e fora o
que você compartilha não guarda seus dados em nenhum servidor.

**Apagar seus dados.** Apague um alarme ou uma contagem regressiva no app e ele some do celular. Se
estava compartilhado, apagá-lo ou tocar em "Parar de compartilhar" também o apaga do Firebase (com
a foto) e anula o convite; "Sair" de algo que compartilharam com você tira você da lista. Desativar
"Pessoas de confiança" apaga a cópia dos seus alarmes e a lista. Para apagar também seu nome e
seus identificadores do Firebase, escreva para xmathyu@gmail.com. Para apagar tudo do celular,
desinstale o Calarmi ou limpe o armazenamento dele em Configurações do Android › Apps › Calarmi. O
backup do Google é gerenciado na sua Conta do Google.

| Tipo de dado | Uso | Sai do celular | Rastreamento |
|---|---|---|---|
| Fotos e toques que você escolhe | Mostrar a foto de um alarme ou de uma contagem regressiva; tocar seu toque. Guardados no celular. | Não (exceto no seu backup do Google, se estiver ativado) | Não |
| Eventos da agenda (leitura) | Colocar alarme nos seus eventos e detectar reuniões e endereços. | Não | Não |
| Passos (desafio "Caminhar") | Contar passos enquanto o alarme toca. Não são guardados. | Não | Não |
| O que você pede ao assistente | Criar ou mudar alarmes, no celular. | Não | Não |
| Diagnósticos técnicos do ML Kit | O Google medir e melhorar suas bibliotecas. Nós não os recebemos. | Sim, para o Google | Não |
| Compras do Calarmi Plus | Saber se você tem o Plus. Processadas pelo Google Play. | Gerenciadas pelo Google Play | Não |
| Seu nome (se você compartilhar) | Que as pessoas que compartilham com você te reconheçam. | Sim, para o Firebase (Google Cloud) | Não |
| Alarmes e contagens regressivas que você compartilha, com a foto | Chegar, com cada alteração, a quem você convida. | Sim, para o Firebase (Google Cloud) | Não |
| Alarmes espelhados para suas pessoas de confiança | Que elas possam gerenciá-los. | Sim, para o Firebase (Google Cloud) | Não |
| Identificador anônimo, ID de instalação e token de push | Avisar seu celular quando algo compartilhado muda. | Sim, para o Firebase (Google Cloud) | Não |

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
3. **Firebase.** Los datos de la sección de compartir salen de `calarmi-firebase/DESIGN.md` y de
   `app/src/main/java/com/mathyusolutions/calarmi/sharing/`. Si el contrato suma campos (p. ej.
   fotos en el espejo de personas de confianza), actualizar el párrafo, la tabla y la sección
   «Seguridad de los datos» de Play a la vez. `pv.tldr4` («Tus alarmas… se quedan en tu teléfono»)
   necesita «…salvo lo que compartes».
4. **Auto-zoom del escáner.** ML Kit solo envía datos de la sesión de escaneo si se activa
   `enableAutoZoom()` en `GmsBarcodeScannerOptions`. Si el reto QR lo activa, el párrafo ya lo
   cubre («datos técnicos de diagnóstico y uso»).
