package com.mathyusolutions.calarmi.model

import androidx.annotation.StringRes
import com.mathyusolutions.calarmi.R
import java.text.Normalizer
import java.time.DayOfWeek
import kotlinx.serialization.Serializable

/**
 * Día de la semana. Los valores crudos (1 = domingo … 7 = sábado) son los de
 * `Calendar.weekday` en iOS: así un JSON de recurrencia vale en las dos apps.
 */
@Serializable(with = WeekdaySerializer::class)
enum class Weekday(val raw: Int, val dayOfWeek: DayOfWeek, @StringRes val shortRes: Int) {
    SUNDAY(1, DayOfWeek.SUNDAY, R.string.weekday_short_sun),
    MONDAY(2, DayOfWeek.MONDAY, R.string.weekday_short_mon),
    TUESDAY(3, DayOfWeek.TUESDAY, R.string.weekday_short_tue),
    WEDNESDAY(4, DayOfWeek.WEDNESDAY, R.string.weekday_short_wed),
    THURSDAY(5, DayOfWeek.THURSDAY, R.string.weekday_short_thu),
    FRIDAY(6, DayOfWeek.FRIDAY, R.string.weekday_short_fri),
    SATURDAY(7, DayOfWeek.SATURDAY, R.string.weekday_short_sat);

    companion object {
        val workdays: Set<Weekday> = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)

        fun fromRaw(raw: Int): Weekday? = entries.firstOrNull { it.raw == raw }

        fun of(day: DayOfWeek): Weekday = entries.first { it.dayOfWeek == day }

        /**
         * Nombre en inglés, español o portugués (con formas cortas y plurales):
         * el asistente recibe lo que la persona escribió tal cual.
         */
        fun fromSlug(slug: String): Weekday? {
            val key = Normalizer.normalize(slug.trim().lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
            return when (key) {
                "sunday", "sundays", "sun", "domingo", "domingos", "dom" -> SUNDAY
                "monday", "mondays", "mon", "lunes", "lun", "segunda", "segunda-feira", "seg" -> MONDAY
                "tuesday", "tuesdays", "tue", "tues", "martes", "mar", "terca", "terca-feira", "ter" -> TUESDAY
                "wednesday", "wednesdays", "wed", "miercoles", "mie", "quarta", "quarta-feira", "qua" -> WEDNESDAY
                "thursday", "thursdays", "thu", "thur", "thurs", "jueves", "jue", "quinta", "quinta-feira", "qui" -> THURSDAY
                "friday", "fridays", "fri", "viernes", "vie", "sexta", "sexta-feira", "sex" -> FRIDAY
                "saturday", "saturdays", "sat", "sabado", "sabados", "sab" -> SATURDAY
                else -> null
            }
        }
    }
}

object WeekdaySerializer : kotlinx.serialization.KSerializer<Weekday> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
        "Weekday", kotlinx.serialization.descriptors.PrimitiveKind.INT,
    )

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Weekday) =
        encoder.encodeInt(value.raw)

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Weekday =
        Weekday.fromRaw(decoder.decodeInt()) ?: Weekday.MONDAY
}
