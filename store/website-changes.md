# Cambios en la web para la versión de Android

Lo que necesita `calarm-web` (https://calarmi.mathyusolutions.com) cuando Calarmi salga en Google
Play. **No se ha tocado el repo de la web**: esto es la lista para hacerlo allá. Las rutas son de
`calarm-web/`.

Hoy la web es solo de iPhone: un único botón de App Store (`src/components/StoreButton.astro`,
`src/lib/appStore.ts`) y textos que dicen «iPhone», «iCloud», «Apple Intelligence» y «Siri» como si
valieran para todos.

## Orden sugerido

1. **Política de privacidad** con la sección de Android (`store/privacy-policy-android.md`). Es
   requisito para enviar la app a revisión en Play.
2. **Ayuda** con las preguntas de Android: la app de Android abre `/ayuda/`, `/en/help/`,
   `/pt/ajuda/` y `/de/hilfe/` desde Ajustes › Ayuda.
3. **Botón de Google Play** en la portada, el menú y el pie, **el día que la app esté publicada**
   (antes el enlace da 404 en Play).
4. **Páginas de invitación** con el aviso para Android.

## 1. Enlace y botón de Google Play

- [ ] `src/lib/playStore.ts`, junto a `appStore.ts`:
      ```ts
      /** Calarmi on Google Play. Mirrors the package of the Android app. */
      export const PLAY_PACKAGE = 'com.mathyusolutions.calarmi';
      export const PLAY_STORE_URL = `https://play.google.com/store/apps/details?id=${PLAY_PACKAGE}`;
      ```
      (Se puede añadir `&hl=es-419`, `&hl=pt-BR`… según el idioma de la página, pero Play ya usa el
      idioma del dispositivo.)
- [ ] `StoreButton.astro`: una prop `store: 'apple' | 'google'` con el logo y el texto de cada
      tienda. Para Google Play, usar el **badge oficial** («Disponible en Google Play», «Get it on
      Google Play», «Disponível no Google Play», «Jetzt bei Google Play») de
      https://play.google.com/intl/en_us/badges/ y respetar sus normas: no cambiar colores ni
      proporciones, mismo alto visual que el botón de la App Store, y la atribución en el pie:
      «Google Play y el logotipo de Google Play son marcas de Google LLC.» (en cada idioma).
- [ ] **Portada** (`src/components/Landing.astro`): los dos botones en el héroe (línea ~39) y en el
      llamado final (línea ~214). Con JavaScript se puede poner primero el de la plataforma de quien
      visita (`/android/i.test(navigator.userAgent)`), pero los dos deben verse siempre: hay quien
      mira la web en la computadora.
- [ ] **Menú** (`src/components/Nav.astro`, botón «Descargar»): hoy va a `APP_STORE_URL`. Que en
      Android lleve a `PLAY_STORE_URL` y en el resto a la App Store (o que haga scroll a los botones).
- [ ] **Pie** (`src/components/Footer.astro`): añadir «Google Play» junto a «App Store» en
      «Producto», más la atribución de marcas.
- [ ] **JSON-LD** (`src/components/HomePage.astro`): `operatingSystem: 'iOS 26, Android 8.0+'` y,
      si se quiere, un segundo `offers`/`downloadUrl` con `PLAY_STORE_URL`.
- [ ] **`og-image.png` / capturas**: siguen siendo de iPhone; se pueden dejar. Si se hacen
      capturas de Android para Play, se pueden usar también en una sección «También en Android».

## 2. Textos de la portada (`src/i18n/landing.ts`, los 4 idiomas)

La app de Android tiene menos cosas que la de iPhone. La portada no tiene que esconderlas, pero sí
dejar claro qué es solo de iPhone, para no ganarse reseñas de una estrella en Play.

| Dónde | Hoy (es) | Propuesta |
|---|---|---|
| `meta.description` | «Alarmas que suenan aunque el iPhone esté en silencio… asistente con Apple Intelligence» | «…aunque el teléfono esté en silencio…» y el asistente sin marca de plataforma |
| `hero.badge` | «Alarmas con inteligencia, gratis en el App Store» | «…gratis para iPhone y Android» |
| `hero.sub` | «…aunque el iPhone esté en silencio…» | «…aunque el teléfono esté en silencio…» |
| `hero.ticks` | «Todo queda en tu iPhone» | «Todo queda en tu teléfono» |
| Asistente (`features`, id `asistente`) | «…con Apple Intelligence, y nada sale de tu iPhone» + «También con Siri y Atajos» + «Necesita un iPhone compatible…» | Cuerpo neutro («…y nada sale de tu teléfono») y viñetas: «En iPhone, con Apple Intelligence y Siri» · «En Android, con Gemini Nano en los teléfonos compatibles» |
| Calendario | «Calarmi lee tu Calendario…», «Tus alarmas también aparecen en tu Calendario» | La segunda viñeta es solo de iPhone: marcarla «(iPhone)» o quitarla |
| `bento`: «Isla Dinámica», «En todos tus dispositivos» (iCloud), «Personas de confianza» | — | Añadir una etiqueta «Solo iPhone» en esas tarjetas |
| `bento`: «Tus tonos» | «…tu audio desde Archivos con Calarmi Plus» | «…tu propio audio con Calarmi Plus» |
| `pricing.free.items` | «Alarmas compartidas y personas de confianza», «Asistente con Apple Intelligence y Siri» | Marcar «(iPhone)» en las dos |
| `pricing.note` | «…el App Store te muestra el de tu país… en los ajustes de tu cuenta de Apple» | «…la tienda te muestra el precio de tu país… La suscripción se cancela en los ajustes de tu cuenta de Apple o en Google Play › Pagos y suscripciones.» |
| `privacy.body` | «…se queda en tu iPhone y en tu iCloud» | «…se queda en tu teléfono (y en tu iCloud, en iPhone)» |
| `cta.sub` | «Gratis en el App Store para iPhone con iOS 26.» | «Gratis para iPhone con iOS 26 y Android 8.0 o posterior.» |
| `appLanguageNote` (de) | — | Comprobar que diga que la app (iPhone y Android) está en inglés, español y portugués |

## 3. Ayuda (`src/i18n/help.ts`, los 4 idiomas)

- [ ] `contactBody`: «Cuéntanos qué pasó, tu modelo de teléfono y tu versión de iOS o Android.»
- [ ] Prefijar con «En iPhone:» las respuestas que hoy hablan de Ajustes de iOS, Apple Account e
      iCloud, y añadir el equivalente de Android cuando lo haya.
- [ ] Preguntas nuevas (es; traducir a en, pt y de):
  - **¿Por qué no sonó una alarma en Android?** «Abre Calarmi › Ajustes › Permisos y comprueba que
    estén permitidos Notificaciones, Alarmas y recordatorios, y Alarmas a pantalla completa, y que
    el interruptor «Alarmas activas» esté encendido. Algunos fabricantes (Samsung, Xiaomi, Huawei,
    OnePlus…) cierran las apps en segundo plano para ahorrar batería: en los ajustes de batería del
    teléfono, deja a Calarmi «Sin restricciones».»
  - **¿Cómo cancelo Calarmi Plus en Android?** «En la app Google Play › tu foto › Pagos y
    suscripciones › Suscripciones › Calarmi Plus › Cancelar. Plus sigue activo hasta el final del
    período pagado, y lo que configuraste sigue funcionando después.»
  - **Cambié de teléfono Android, ¿cómo recupero Plus y mis alarmas?** «Plus: abre Calarmi ›
    Ajustes › Calarmi Plus › Restaurar compras con la misma cuenta de Google. Alarmas: vuelven si
    restauraste el teléfono nuevo desde la copia de seguridad de Google; si no, hay que crearlas de
    nuevo. Entre iPhone y Android no se pasan las alarmas ni Plus.»
  - **¿Puedo compartir alarmas o aceptar invitaciones en Android?** «Todavía no: compartir alarmas y
    cuentas regresivas, las invitaciones y las personas de confianza por ahora son solo de iPhone.»
  - **¿Por qué el asistente de Android entiende menos que en iPhone?** «En los teléfonos con Gemini
    Nano (la IA de Google que funciona en el dispositivo) entiende frases libres; en los demás usa un
    intérprete más sencillo. En los dos casos, nada de lo que escribes sale del teléfono.»
- [ ] `metaDescription`: añadir «para iPhone y Android».

## 4. Páginas de invitación (`/i/…`, `/c/…`, `src/components/InvitePage.astro`)

En Android v1 no se pueden abrir invitaciones: no hay alarmas compartidas ni App Links. Hoy la
página le ofrece a todo el mundo el botón de la App Store, «Ya tengo Calarmi» (que abre la app de
iPhone) y «¿La acabas de instalar? Vuelve a tocar el enlace y se añade sola», que en Android sería
falso.

- [ ] Si `navigator.userAgent` es Android:
  - Esconder «Ya tengo Calarmi» y la pista de volver a tocar el enlace.
  - Mostrar un aviso: «Las invitaciones por ahora solo se abren en Calarmi para iPhone.» (en, pt,
    como en el resto de la página).
  - Debajo, el badge de Google Play con un texto que no prometa la invitación: «Calarmi también está
    en Android, para tus propias alarmas.»
- [ ] La vista previa (OG) y la tarjeta (`/card/…`) no cambian.
- [ ] `description` en las tres lenguas dice «…para que también suene en tu iPhone»: está bien, es
      verdad para quien la abra en iPhone.

## 5. `/.well-known/assetlinks.json`: no hace falta ahora

Solo sirve para que Android abra enlaces `https://calarmi.mathyusolutions.com/...` directamente en
la app (App Links con `android:autoVerify="true"`). La app de Android v1 no declara ningún
`intent-filter` con `https`, así que **no** hay que publicarlo. Cuando Android tenga invitaciones:

- añadir en el manifiesto el `intent-filter` con `autoVerify` para `/i/` y `/c/`;
- publicar `public/.well-known/assetlinks.json` con el paquete `com.mathyusolutions.calarmi` y el
  **SHA-256 del certificado de firma de la app** que muestra Play Console en *Integridad de la app*
  (el de Google, **no** el de la clave de subida; añadir también el de debug si se quiere probar);
- servirlo como `application/json`, sin redirecciones, igual que `apple-app-site-association`.

## 6. Nada más

- `robots.txt` y el sitemap no cambian.
- Las rutas que abre la app de Android (`ayuda/`, `privacidad/`, `en/help/`, `en/privacy/`,
  `pt/ajuda/`, `pt/privacidade/`, `de/hilfe/`, `de/datenschutz/`, en `SettingsScreen.kt`) ya
  existen.
