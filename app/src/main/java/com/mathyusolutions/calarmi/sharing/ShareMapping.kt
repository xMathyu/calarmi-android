package com.mathyusolutions.calarmi.sharing

import com.google.firebase.Timestamp
import com.mathyusolutions.calarmi.model.AlarmLeadTime
import com.mathyusolutions.calarmi.model.AlarmSchedule
import com.mathyusolutions.calarmi.model.AlarmTone
import com.mathyusolutions.calarmi.model.CountdownEvent
import com.mathyusolutions.calarmi.model.CustomCategory
import com.mathyusolutions.calarmi.model.RecurrenceRule
import com.mathyusolutions.calarmi.model.Reminder
import com.mathyusolutions.calarmi.model.ReminderCategory
import com.mathyusolutions.calarmi.model.ReminderIconKind
import com.mathyusolutions.calarmi.model.WakeChallenge
import com.mathyusolutions.calarmi.model.Weekday
import com.mathyusolutions.calarmi.model.isEmojiIcon
import java.net.URI
import java.security.SecureRandom
import java.util.Date
import java.util.Random

/**
 * Traducción entre los modelos de la app y los mapas de Firestore del contrato
 * compartido con iOS y la web (`calarmi-firebase/DESIGN.md`). Cambiar un nombre
 * de campo aquí exige cambiarlo en los tres lados.
 *
 * Sin llamadas a Firebase a propósito: las fechas salen como `Date` (el SDK las
 * guarda como timestamp) y al leer se acepta `Timestamp` o `Date`. Así las
 * pruebas cubren la traducción sin red ni emulador.
 */
object ShareMapping {
    // Lo que aceptan las reglas (firestore.rules). Recortar aquí evita que un
    // título larguísimo haga fallar todo el share.
    const val MAX_TITLE = 200
    const val MAX_NOTES = 2000
    const val MAX_SCHEDULES = 20
    const val MAX_LEAD_TIMES = 10
    const val MAX_INTERVAL = 999
    const val MAX_NAME = 60

    const val KIND_ALARM = "alarm"
    const val KIND_COUNTDOWN = "countdown"
    const val KIND_DELEGATION = "delegation"

    // ------------------------------------------------------------ Recurrence

    /** `{ type, interval, weekdays }`; `weekdays` (1 = domingo … 7 = sábado) solo en `weekly`. */
    fun recurrenceMap(rule: RecurrenceRule): Map<String, Any> {
        fun clamp(n: Int) = n.coerceIn(1, MAX_INTERVAL)
        return when (rule) {
            RecurrenceRule.Once -> mapOf("type" to "once", "interval" to 1, "weekdays" to emptyList<Int>())
            is RecurrenceRule.Daily -> mapOf("type" to "daily", "interval" to clamp(rule.interval), "weekdays" to emptyList<Int>())
            is RecurrenceRule.Weekly -> mapOf(
                "type" to "weekly",
                "interval" to clamp(rule.interval),
                "weekdays" to rule.weekdays.map { it.raw }.sorted(),
            )
            is RecurrenceRule.Monthly -> mapOf("type" to "monthly", "interval" to clamp(rule.interval), "weekdays" to emptyList<Int>())
            is RecurrenceRule.Yearly -> mapOf("type" to "yearly", "interval" to clamp(rule.interval), "weekdays" to emptyList<Int>())
        }
    }

    /** Tolerante: lo que no se entiende (un tipo nuevo de otra plataforma) suena una vez. */
    fun recurrence(value: Any?): RecurrenceRule {
        val map = value as? Map<*, *> ?: return RecurrenceRule.Once
        val interval = (int(map["interval"]) ?: 1).coerceIn(1, MAX_INTERVAL)
        return when (map["type"]) {
            "daily" -> RecurrenceRule.Daily(interval)
            "weekly" -> RecurrenceRule.Weekly(
                interval,
                (map["weekdays"] as? List<*>).orEmpty().mapNotNull { int(it)?.let(Weekday::fromRaw) }.toSet(),
            )
            "monthly" -> RecurrenceRule.Monthly(interval)
            "yearly" -> RecurrenceRule.Yearly(interval)
            else -> RecurrenceRule.Once
        }
    }

    // ----------------------------------------------------------------- Alarm

    /**
     * El mapa `Alarm` de una alarma. `custom` es su categoría propia (si tiene),
     * para que quien la recibe la pinte con su color. Solo viajan los tonos que
     * trae la app: uno importado no existe en otro teléfono.
     */
    fun alarmMap(reminder: Reminder, custom: CustomCategory?): Map<String, Any> {
        val map = linkedMapOf<String, Any>(
            "title" to clipped(reminder.title, MAX_TITLE, fallback = "Alarma"),
            "date" to Date(reminder.date),
            "recurrence" to recurrenceMap(reminder.recurrence),
            "additionalSchedules" to reminder.additionalSchedules.take(MAX_SCHEDULES).map {
                mapOf("date" to Date(it.date), "recurrence" to recurrenceMap(it.recurrence))
            },
            "leadTimes" to leadTimeSeconds(reminder.leadTimes.map { it.seconds }),
            "categoryRaw" to reminder.categoryRaw,
            "iconKindRaw" to reminder.iconKindRaw,
            "isEnabled" to reminder.isEnabled,
        )
        reminder.notes?.trim()?.takeIf { it.isNotEmpty() }?.let { map["notes"] = it.take(MAX_NOTES) }
        reminder.symbolName?.takeIf { it.isNotEmpty() }?.let { map["symbolName"] = it }
        if (custom != null && custom.id == reminder.customCategoryId) {
            map["customCategory"] = mapOf(
                "id" to custom.id,
                "name" to custom.name,
                "colorHex" to custom.colorHex,
                "iconKindRaw" to custom.iconKindRaw,
                "iconValue" to custom.iconValue,
            )
        }
        reminder.challenge?.let { map["challenge"] = challengeMap(it) }
        reminder.tone?.takeIf { it in AlarmTone.builtins }?.let { map["tone"] = it.raw }
        return map
    }

    /** Lo que trae un `Alarm` de Firestore: la alarma, su categoría propia y los avisos del dueño. */
    data class RemoteAlarm(
        val reminder: Reminder,
        val customCategory: CustomCategory?,
        val leadTimes: List<AlarmLeadTime>,
    )

    /**
     * Una alarma a partir del mapa `Alarm`, con el `id` del documento. `null` si
     * falta lo mínimo (título y fecha). Los horarios extra reciben ids estables
     * (`<id>-1`, `<id>-2`…) para que leer dos veces lo mismo no cuente como cambio.
     */
    fun alarm(id: String, value: Any?): RemoteAlarm? {
        val map = value as? Map<*, *> ?: return null
        val title = map["title"] as? String ?: return null
        val date = millis(map["date"]) ?: return null
        val schedules = (map["additionalSchedules"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }
            .mapIndexedNotNull { index, schedule ->
                val at = millis(schedule["date"]) ?: return@mapIndexedNotNull null
                AlarmSchedule(id = "$id-${index + 1}", date = at, recurrence = recurrence(schedule["recurrence"]))
            }
        val custom = (map["customCategory"] as? Map<*, *>)?.let { c ->
            val cid = c["id"] as? String ?: return@let null
            CustomCategory(
                id = cid,
                name = (c["name"] as? String)?.takeIf { it.isNotBlank() } ?: "Categoría",
                colorHex = (c["colorHex"] as? String)?.takeIf { it.isNotBlank() } ?: "#AF52DE",
                iconKindRaw = int(c["iconKindRaw"]) ?: ReminderIconKind.SYMBOL.raw,
                iconValue = (c["iconValue"] as? String)?.takeIf { it.isNotBlank() } ?: "star.fill",
            )
        }
        val leads = leadTimes(map["leadTimes"])
        val reminder = Reminder(
            id = id,
            title = title,
            notes = (map["notes"] as? String)?.takeIf { it.isNotBlank() },
            date = date,
            categoryRaw = int(map["categoryRaw"]) ?: ReminderCategory.EVENT.raw,
            customCategoryId = custom?.id,
            iconKindRaw = int(map["iconKindRaw"]) ?: ReminderIconKind.SYMBOL.raw,
            symbolName = (map["symbolName"] as? String)?.takeIf { it.isNotEmpty() },
            recurrence = recurrence(map["recurrence"]),
            leadTimes = leads,
            additionalSchedules = schedules,
            tone = AlarmTone.fromRaw(map["tone"] as? String)?.takeIf { it in AlarmTone.builtins },
            challenge = challenge(map["challenge"]),
            isEnabled = map["isEnabled"] as? Boolean ?: true,
        )
        return RemoteAlarm(reminder, custom, leads)
    }

    fun challengeMap(challenge: WakeChallenge): Map<String, Any> {
        val map = linkedMapOf<String, Any>(
            "kind" to challengeKindName(challenge.kind),
            "difficulty" to challenge.difficulty.raw,
            "allowsSnooze" to challenge.allowsSnooze,
        )
        challenge.code?.takeIf { it.isNotEmpty() }?.let { map["code"] = it }
        return map
    }

    /**
     * Un reto de un tipo que esta versión no conoce se descarta: mejor una alarma
     * que se apaga con un toque que una que no se puede apagar.
     */
    fun challenge(value: Any?): WakeChallenge? {
        val map = value as? Map<*, *> ?: return null
        val name = map["kind"] as? String ?: return null
        val kind = WakeChallenge.Kind.entries.firstOrNull { challengeKindName(it) == name } ?: return null
        return WakeChallenge(
            kind = kind,
            difficulty = WakeChallenge.Difficulty.entries.firstOrNull { it.raw == int(map["difficulty"]) } ?: WakeChallenge.Difficulty.MEDIUM,
            allowsSnooze = map["allowsSnooze"] as? Boolean ?: true,
            code = (map["code"] as? String)?.takeIf { it.isNotEmpty() },
        )
    }

    /** El nombre de iOS (`@SerialName` del enum), el mismo en el JSON local. */
    private fun challengeKindName(kind: WakeChallenge.Kind): String =
        WakeChallenge.Kind.serializer().descriptor.getElementName(kind.ordinal)

    // ------------------------------------------------------------- Countdown

    fun countdownMap(event: CountdownEvent): Map<String, Any> {
        val map = linkedMapOf<String, Any>(
            "title" to clipped(event.title, MAX_TITLE, fallback = "Cuenta regresiva"),
            "date" to Date(event.date),
            "includesTime" to event.includesTime,
            "repeatsYearly" to event.repeatsYearly,
            "colorHex" to event.colorHex.take(9),
            "iconKindRaw" to event.iconKindRaw,
            "iconValue" to event.iconValue.take(100),
        )
        event.notes?.trim()?.takeIf { it.isNotEmpty() }?.let { map["notes"] = it.take(MAX_NOTES) }
        return map
    }

    fun countdown(id: String, value: Any?): CountdownEvent? {
        val map = value as? Map<*, *> ?: return null
        val title = map["title"] as? String ?: return null
        val date = millis(map["date"]) ?: return null
        return CountdownEvent(
            id = id,
            title = title,
            notes = (map["notes"] as? String)?.takeIf { it.isNotBlank() },
            date = date,
            includesTime = map["includesTime"] as? Boolean ?: false,
            repeatsYearly = map["repeatsYearly"] as? Boolean ?: false,
            colorHex = (map["colorHex"] as? String)?.takeIf { it.isNotBlank() } ?: CountdownEvent.DEFAULT_COLOR_HEX,
            iconKindRaw = int(map["iconKindRaw"]) ?: ReminderIconKind.EMOJI.raw,
            iconValue = (map["iconValue"] as? String)?.takeIf { it.isNotEmpty() } ?: CountdownEvent.DEFAULT_EMOJI,
        )
    }

    // --------------------------------------------------------------- Preview

    /**
     * Lo que dibuja la página de invitación (y la tarjeta del enlace).
     * `repeatsYearly`: una cuenta anual (un cumpleaños) o una alarma cuyo horario
     * principal es anual; la web muestra entonces la próxima vez.
     */
    fun previewMap(
        title: String,
        date: Long,
        hasTime: Boolean,
        timeZone: String,
        language: String,
        tintHex: String? = null,
        emoji: String? = null,
        symbol: String? = null,
        repeatsYearly: Boolean = false,
    ): Map<String, Any> {
        val map = linkedMapOf<String, Any>(
            "title" to clipped(title, MAX_TITLE, fallback = "Calarmi"),
            "date" to Date(date),
            "hasTime" to hasTime,
            "timeZone" to timeZone.take(64),
            "language" to if (language in setOf("es", "en", "pt")) language else "en",
            "repeatsYearly" to repeatsYearly,
        )
        tintHex?.let { map["tintHex"] = it }
        emoji?.let { map["emoji"] = it }
        symbol?.let { map["symbol"] = it }
        return map
    }

    /**
     * El ícono de una alarma para la página (como `InvitePreview(reminder:)` de
     * iOS): su emoji, o su símbolo; con foto, el símbolo de la categoría por si
     * la foto no se puede dibujar. Devuelve (emoji, símbolo).
     */
    fun alarmPreviewIcon(reminder: Reminder, categoryIconKind: ReminderIconKind, categoryIconValue: String): Pair<String?, String?> {
        val own = reminder.symbolName?.takeIf { it.isNotEmpty() }
        val categorySymbol = if (categoryIconKind == ReminderIconKind.SYMBOL) categoryIconValue else "bell.fill"
        val isEmoji = reminder.iconKind == ReminderIconKind.EMOJI || (reminder.iconKind == ReminderIconKind.SYMBOL && isEmojiIcon(own))
        return when {
            isEmoji -> (own ?: "⭐️") to null
            reminder.iconKind == ReminderIconKind.PHOTO -> null to categorySymbol
            else -> null to (own ?: categorySymbol)
        }
    }

    fun countdownPreviewIcon(event: CountdownEvent): Pair<String?, String?> = when {
        event.iconKind == ReminderIconKind.EMOJI && isEmojiIcon(event.iconValue) -> event.iconValue to null
        event.iconKind == ReminderIconKind.SYMBOL -> null to event.iconValue
        else -> null to null
    }

    /** El idioma de la página: "es", "pt" o "en" (el de Android por defecto). */
    fun language(languageCode: String?): String = when (languageCode?.lowercase()) {
        "es" -> "es"
        "pt" -> "pt"
        else -> "en"
    }

    // --------------------------------------------------------------- Personas

    /** El nombre tal como lo aceptan las reglas (1…60 caracteres), o `null`. */
    fun validName(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NAME)

    data class Person(val uid: String, val name: String, val role: String, val joinedAt: Long?)

    /**
     * Los miembros de un share (o las personas de confianza de una lista),
     * ordenados por cuándo entraron, sin `excluding` (normalmente, quien mira).
     */
    fun people(value: Any?, excluding: String? = null, dateKey: String = "joinedAt"): List<Person> {
        val map = value as? Map<*, *> ?: return emptyList()
        return map.mapNotNull { (key, entry) ->
            val uid = key as? String ?: return@mapNotNull null
            val e = entry as? Map<*, *> ?: return@mapNotNull null
            if (uid == excluding) return@mapNotNull null
            Person(uid, validName(e["name"] as? String) ?: "", e["role"] as? String ?: "member", millis(e[dateKey]))
        }.sortedWith(compareBy<Person>({ it.joinedAt ?: Long.MAX_VALUE }, { it.uid }))
    }

    // ------------------------------------------------------------- Ayudantes

    /** Un entero de Firestore (Long), de Kotlin o un `Double` exacto (la web escribe números de JavaScript). */
    fun int(value: Any?): Int? = when (value) {
        is Int -> value
        is Long -> value.toInt()
        is Double -> if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) value.toInt() else null
        is Number -> value.toInt()
        else -> null
    }

    fun long(value: Any?): Long? = when (value) {
        is Long -> value
        is Int -> value.toLong()
        is Double -> if (value % 1.0 == 0.0) value.toLong() else null
        is Number -> value.toLong()
        else -> null
    }

    /** Milisegundos de un `Timestamp` (lo que devuelve Firestore) o de un `Date`. */
    fun millis(value: Any?): Long? = when (value) {
        is Timestamp -> value.seconds * 1000 + value.nanoseconds / 1_000_000
        is Date -> value.time
        else -> null
    }

    /** Avisos en segundos: al menos uno (las reglas lo exigen), sin repetidos y en orden. */
    fun leadTimeSeconds(seconds: List<Int>): List<Int> =
        seconds.filter { it >= 0 }.distinct().sorted().take(MAX_LEAD_TIMES).ifEmpty { listOf(AlarmLeadTime.AT_START.seconds) }

    /** Los avisos que conoce esta app; los desconocidos se ignoran y, si no queda ninguno, «al momento». */
    fun leadTimes(value: Any?): List<AlarmLeadTime> =
        (value as? List<*>).orEmpty().mapNotNull { int(it)?.let(AlarmLeadTime::fromSeconds) }
            .distinct().sortedBy { it.seconds }.ifEmpty { listOf(AlarmLeadTime.AT_START) }

    private fun clipped(text: String, max: Int, fallback: String): String =
        text.trim().ifEmpty { fallback }.take(max)
}

/**
 * El token de `invites/{token}`: 22 caracteres `[A-Za-z0-9]` al azar (~131
 * bits). Es la llave de la invitación (cualquiera que lo tenga puede leerla),
 * así que sale de un generador criptográfico y sin sesgo de módulo.
 */
object InviteTokens {
    const val LENGTH = 22
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val pattern = Regex("^[A-Za-z0-9]{$LENGTH}$")

    fun generate(random: Random = SecureRandom()): String {
        val out = StringBuilder(LENGTH)
        val bytes = ByteArray(32)
        while (out.length < LENGTH) {
            random.nextBytes(bytes)
            for (b in bytes) {
                // 6 bits (0…63): se descartan 62 y 63 para que cada letra salga igual de seguido.
                val v = b.toInt() and 0x3F
                if (v < ALPHABET.length) out.append(ALPHABET[v])
                if (out.length == LENGTH) break
            }
        }
        return out.toString()
    }

    /** Lo mismo que exigen las reglas (`^[A-Za-z0-9]{22}$`). */
    fun isValid(token: String?): Boolean = token != null && pattern.matches(token)
}

/**
 * Los enlaces de invitación: `https://calarmi.mathyusolutions.com/s/<token>/`
 * (App Link, `assetlinks.json` en la web) y, de respaldo, `calarmi://s/<token>`.
 */
object InviteLinks {
    const val HOST = "calarmi.mathyusolutions.com"
    const val SCHEME = "calarmi"

    fun url(token: String): String = "https://$HOST/s/$token/"

    /** El token de un enlace nuestro, o `null` si no lo es (lo que llega de fuera no se cree). */
    fun token(link: String?): String? {
        val uri = runCatching { URI(link?.trim() ?: return null) }.getOrNull() ?: return null
        val segments: List<String> = when (uri.scheme?.lowercase()) {
            "https", "http" -> {
                if (!uri.host.equals(HOST, ignoreCase = true)) return null
                uri.path.orEmpty().split("/").filter { it.isNotEmpty() }
            }
            // En `calarmi://s/<token>` la «s» es el host.
            SCHEME -> listOfNotNull(uri.host) + uri.path.orEmpty().split("/").filter { it.isNotEmpty() }
            else -> return null
        }
        if (segments.size != 2 || segments[0] != "s") return null
        return segments[1].takeIf(InviteTokens::isValid)
    }
}
