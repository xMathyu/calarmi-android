package com.mathyusolutions.calarmi.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mathyusolutions.calarmi.domain.CountdownMath
import com.mathyusolutions.calarmi.domain.CountdownStatus
import java.util.UUID

/**
 * Una fecha que se espera (un concierto, un viaje, un cumpleaños) y los días que
 * faltan. No suena: solo cuenta.
 */
@Entity(tableName = "countdowns")
data class CountdownEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val notes: String? = null,
    /** El día del evento; también la hora cuando `includesTime`. */
    val date: Long = System.currentTimeMillis(),
    val includesTime: Boolean = false,
    /** Vuelve cada año: cuenta siempre hasta el próximo. */
    val repeatsYearly: Boolean = false,
    val colorHex: String = DEFAULT_COLOR_HEX,
    val iconKindRaw: Int = ReminderIconKind.EMOJI.raw,
    val iconValue: String = DEFAULT_EMOJI,
    /** Nombre del archivo en `PhotoStore`: es el fondo de la tarjeta. */
    val photoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val iconKind: ReminderIconKind get() = ReminderIconKind.fromRaw(iconKindRaw)

    fun target(now: Long = System.currentTimeMillis()): Long =
        CountdownMath.target(date, includesTime, repeatsYearly, now)

    fun status(now: Long = System.currentTimeMillis()): CountdownStatus =
        CountdownMath.status(target(now), now)

    companion object {
        const val DEFAULT_COLOR_HEX = "#FF2D55"
        const val DEFAULT_EMOJI = "🎉"
    }
}

/** Lo que trae ya lleno una cuenta regresiva creada desde una alarma. */
@kotlinx.serialization.Serializable
data class CountdownPrefill(
    val title: String,
    val date: Long,
    val includesTime: Boolean = false,
    val repeatsYearly: Boolean = false,
    val colorHex: String = CountdownEvent.DEFAULT_COLOR_HEX,
    val iconKindRaw: Int = ReminderIconKind.EMOJI.raw,
    val iconValue: String = CountdownEvent.DEFAULT_EMOJI,
    /** Foto de la alarma: se copia para la cuenta. */
    val photoPath: String? = null,
)
