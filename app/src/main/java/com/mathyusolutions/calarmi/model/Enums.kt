package com.mathyusolutions.calarmi.model

import androidx.annotation.StringRes
import com.mathyusolutions.calarmi.R

/**
 * Con cuánta anticipación suena un aviso. Los valores crudos son segundos,
 * igual que en iOS.
 */
enum class AlarmLeadTime(val seconds: Int, @StringRes val titleRes: Int, @StringRes val shortRes: Int) {
    AT_START(0, R.string.lead_at_start, R.string.lead_short_at_start),
    MIN5(300, R.string.lead_5m, R.string.lead_short_5m),
    MIN10(600, R.string.lead_10m, R.string.lead_short_10m),
    MIN15(900, R.string.lead_15m, R.string.lead_short_15m),
    MIN30(1800, R.string.lead_30m, R.string.lead_short_30m),
    MIN45(2700, R.string.lead_45m, R.string.lead_short_45m),
    HOUR1(3600, R.string.lead_1h, R.string.lead_short_1h),
    HOUR2(7200, R.string.lead_2h, R.string.lead_short_2h),
    HOUR3(10800, R.string.lead_3h, R.string.lead_short_3h),
    HOUR6(21600, R.string.lead_6h, R.string.lead_short_6h),
    HOUR12(43200, R.string.lead_12h, R.string.lead_short_12h),
    DAY1(86400, R.string.lead_1d, R.string.lead_short_1d),
    DAY2(172800, R.string.lead_2d, R.string.lead_short_2d),
    WEEK1(604800, R.string.lead_1w, R.string.lead_short_1w);

    val millis: Long get() = seconds * 1000L

    companion object {
        fun fromSeconds(seconds: Int): AlarmLeadTime? = entries.firstOrNull { it.seconds == seconds }

        /** El más cercano a `minutes` (lo que pide el asistente). */
        fun closestTo(minutes: Int): AlarmLeadTime =
            entries.minBy { kotlin.math.abs(it.seconds - minutes * 60) }
    }
}

/** Cuánto esperar al posponer. */
enum class SnoozeInterval(val seconds: Int, @StringRes val titleRes: Int) {
    MIN1(60, R.string.snooze_1m),
    MIN5(300, R.string.snooze_5m),
    MIN9(540, R.string.snooze_9m),
    MIN10(600, R.string.snooze_10m);

    val millis: Long get() = seconds * 1000L

    companion object {
        val DEFAULT = MIN10
        fun fromSeconds(seconds: Int): SnoozeInterval? = entries.firstOrNull { it.seconds == seconds }
    }
}

/**
 * La cuenta regresiva que se ve ANTES de que suene una alarma (en Android, una
 * notificación con cronómetro). Apagada por defecto.
 */
enum class AlarmCountdown(val seconds: Int, @StringRes val titleRes: Int) {
    OFF(0, R.string.countdown_off),
    MIN1(60, R.string.countdown_1m),
    MIN2(120, R.string.countdown_2m),
    MIN5(300, R.string.countdown_5m),
    MIN10(600, R.string.countdown_10m),
    MIN15(900, R.string.countdown_15m);

    val millis: Long get() = seconds * 1000L

    companion object {
        fun fromSeconds(seconds: Int): AlarmCountdown? = entries.firstOrNull { it.seconds == seconds }
    }
}

enum class ReminderIconKind(val raw: Int) {
    SYMBOL(0), PHOTO(1), EMOJI(2);

    companion object {
        fun fromRaw(raw: Int): ReminderIconKind = entries.firstOrNull { it.raw == raw } ?: SYMBOL
    }
}

/** ¿El texto empieza con un emoji? (los íconos guardan símbolo o emoji en el mismo campo). */
fun isEmojiIcon(value: String?): Boolean {
    if (value.isNullOrEmpty()) return false
    val cp = value.codePointAt(0)
    return cp > 0x238C && !Character.isLetterOrDigit(cp) && cp != '.'.code
}

enum class AppearanceMode(val raw: Int, @StringRes val titleRes: Int) {
    SYSTEM(0, R.string.appearance_system),
    LIGHT(1, R.string.appearance_light),
    DARK(2, R.string.appearance_dark);

    companion object {
        fun fromRaw(raw: Int): AppearanceMode = entries.firstOrNull { it.raw == raw } ?: SYSTEM
    }
}

/** Idioma de la app. `tag` vacío = el del sistema. */
enum class AppLanguage(val tag: String, @StringRes val titleRes: Int) {
    SYSTEM("", R.string.language_system),
    SPANISH("es", R.string.language_spanish),
    ENGLISH("en", R.string.language_english),
    PORTUGUESE("pt-BR", R.string.language_portuguese),
}
