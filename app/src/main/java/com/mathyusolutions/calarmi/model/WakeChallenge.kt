package com.mathyusolutions.calarmi.model

import androidx.annotation.StringRes
import com.mathyusolutions.calarmi.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * El reto que hay que resolver para apagar una alarma. En Android la app
 * controla la pantalla que suena, así que la alarma no deja de sonar hasta que
 * el reto se resuelve (o se pospone, si la alarma lo permite).
 *
 * Se guarda como JSON en la alarma con los mismos nombres de campo que en iOS.
 */
@Serializable
data class WakeChallenge(
    val kind: Kind,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    /** Si la pantalla del reto ofrece «Posponer». */
    val allowsSnooze: Boolean = true,
    /** Para `QR`: el contenido del código que hay que escanear. */
    val code: String? = null,
) {
    enum class Group(@StringRes val titleRes: Int) {
        MIND(R.string.challenge_group_mind),
        ARCADE(R.string.challenge_group_arcade),
        BODY(R.string.challenge_group_body),
        GET_UP(R.string.challenge_group_get_up),
    }

    /** El orden es el de la lista. El nombre serializado es el de iOS: no renombrar. */
    @Serializable
    enum class Kind(
        val group: Group,
        @StringRes val titleRes: Int,
        @StringRes val descriptionRes: Int,
        val symbol: String,
    ) {
        @SerialName("math") MATH(Group.MIND, R.string.challenge_math, R.string.challenge_math_desc, "plus.forwardslash.minus"),
        @SerialName("memory") MEMORY(Group.MIND, R.string.challenge_memory, R.string.challenge_memory_desc, "square.grid.3x3.fill"),
        @SerialName("order") ORDER(Group.MIND, R.string.challenge_order, R.string.challenge_order_desc, "number.square"),
        @SerialName("pairs") PAIRS(Group.MIND, R.string.challenge_pairs, R.string.challenge_pairs_desc, "rectangle.on.rectangle.angled"),
        @SerialName("colors") COLORS(Group.MIND, R.string.challenge_colors, R.string.challenge_colors_desc, "paintpalette.fill"),
        @SerialName("odd") ODD(Group.MIND, R.string.challenge_odd, R.string.challenge_odd_desc, "eye.fill"),
        @SerialName("typing") TYPING(Group.MIND, R.string.challenge_typing, R.string.challenge_typing_desc, "keyboard"),
        @SerialName("flap") FLAP(Group.ARCADE, R.string.challenge_flap, R.string.challenge_flap_desc, "wind"),
        @SerialName("hurdles") HURDLES(Group.ARCADE, R.string.challenge_hurdles, R.string.challenge_hurdles_desc, "figure.jumprope"),
        @SerialName("cannon") CANNON(Group.ARCADE, R.string.challenge_cannon, R.string.challenge_cannon_desc, "target"),
        @SerialName("sling") SLING(Group.ARCADE, R.string.challenge_sling, R.string.challenge_sling_desc, "square.stack.3d.up.fill"),
        @SerialName("shake") SHAKE(Group.BODY, R.string.challenge_shake, R.string.challenge_shake_desc, "iphone.radiowaves.left.and.right"),
        @SerialName("steps") STEPS(Group.BODY, R.string.challenge_steps, R.string.challenge_steps_desc, "figure.walk"),
        @SerialName("tilt") TILT(Group.BODY, R.string.challenge_tilt, R.string.challenge_tilt_desc, "circle.dotted.and.circle"),
        @SerialName("reflex") REFLEX(Group.BODY, R.string.challenge_reflex, R.string.challenge_reflex_desc, "scope"),
        @SerialName("qr") QR(Group.GET_UP, R.string.challenge_qr, R.string.challenge_qr_desc, "qrcode.viewfinder");

        /** Hace falta Plus para ELEGIRLO; uno ya elegido sigue sonando sin Plus. */
        val requiresPlus: Boolean get() = group == Group.ARCADE

        companion object {
            fun inGroup(group: Group): List<Kind> = entries.filter { it.group == group }
        }
    }

    /** En JSON es un número (1, 2, 3), como en iOS. */
    @Serializable(with = DifficultySerializer::class)
    enum class Difficulty(val raw: Int, @StringRes val titleRes: Int) {
        EASY(1, R.string.difficulty_easy),
        MEDIUM(2, R.string.difficulty_medium),
        HARD(3, R.string.difficulty_hard),
    }

    /** Un reto QR sin código registrado cuenta como «sin reto». */
    val isReady: Boolean get() = kind != Kind.QR || !code.isNullOrEmpty()

    companion object {
        /** Tope de insistencia: una alarma con reto deja de sonar sola a los 30 min. */
        const val MAX_RING_MILLIS: Long = 30 * 60 * 1000L
    }
}

object DifficultySerializer : kotlinx.serialization.KSerializer<WakeChallenge.Difficulty> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
        "WakeChallenge.Difficulty", kotlinx.serialization.descriptors.PrimitiveKind.INT,
    )

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: WakeChallenge.Difficulty) =
        encoder.encodeInt(value.raw)

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): WakeChallenge.Difficulty {
        val raw = decoder.decodeInt()
        return WakeChallenge.Difficulty.entries.firstOrNull { it.raw == raw } ?: WakeChallenge.Difficulty.MEDIUM
    }
}
