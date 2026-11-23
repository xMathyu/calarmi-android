package com.mathyusolutions.calarmi.model

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.mathyusolutions.calarmi.R

/**
 * El tono con el que suena una alarma. `raw` es lo que se guarda (en la alarma
 * y en Ajustes) y es el mismo texto que en iOS: "system", "chime"… o
 * "imported:<id>" para un audio importado (Plus).
 */
sealed class AlarmTone(val raw: String) {
    /** El tono de alarma del sistema (Ajustes › Sonido del teléfono). */
    data object System : AlarmTone("system")
    data object Chime : AlarmTone("chime")
    data object Marimba : AlarmTone("marimba")
    data object Radar : AlarmTone("radar")
    data object Arpeggio : AlarmTone("arpeggio")
    data object Pulse : AlarmTone("pulse")
    data object Harp : AlarmTone("harp")
    data class Imported(val id: String) : AlarmTone(IMPORTED_PREFIX + id)

    /** Recurso de audio de los tonos que trae la app. */
    @get:RawRes
    val rawRes: Int?
        get() = when (this) {
            Chime -> R.raw.tone_chime
            Marimba -> R.raw.tone_marimba
            Radar -> R.raw.tone_radar
            Arpeggio -> R.raw.tone_arpeggio
            Pulse -> R.raw.tone_pulse
            Harp -> R.raw.tone_harp
            else -> null
        }

    @get:StringRes
    val titleRes: Int
        get() = when (this) {
            System -> R.string.tone_system
            Chime -> R.string.tone_chime
            Marimba -> R.string.tone_marimba
            Radar -> R.string.tone_radar
            Arpeggio -> R.string.tone_arpeggio
            Pulse -> R.string.tone_pulse
            Harp -> R.string.tone_harp
            is Imported -> R.string.tone_imported
        }

    /** Nombre de SF Symbol para el ícono de la fila. */
    val symbol: String
        get() = when (this) {
            System -> "speaker.wave.2.fill"
            Chime -> "bell.and.waves.left.and.right.fill"
            Marimba -> "music.note"
            Radar -> "dot.radiowaves.left.and.right"
            Arpeggio -> "music.quarternote.3"
            Pulse -> "waveform"
            Harp -> "sparkles"
            is Imported -> "waveform.circle.fill"
        }

    companion object {
        const val IMPORTED_PREFIX = "imported:"
        val DEFAULT: AlarmTone = System
        val builtins: List<AlarmTone> = listOf(System, Chime, Marimba, Radar, Arpeggio, Pulse, Harp)

        fun fromRaw(raw: String?): AlarmTone? {
            if (raw.isNullOrEmpty()) return null
            if (raw.startsWith(IMPORTED_PREFIX)) {
                val id = raw.removePrefix(IMPORTED_PREFIX)
                return if (id.isEmpty()) null else Imported(id)
            }
            return builtins.firstOrNull { it.raw == raw }
        }
    }
}
