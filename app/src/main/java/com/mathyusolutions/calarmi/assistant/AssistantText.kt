package com.mathyusolutions.calarmi.assistant

import com.mathyusolutions.calarmi.model.Weekday
import java.text.Normalizer
import java.util.Locale

/** Los idiomas que entiende (y en los que responde) el asistente: los mismos de la app. */
enum class AssistantLang(val tag: String) {
    ES("es"), EN("en"), PT("pt");

    val locale: Locale
        get() = when (this) {
            ES -> Locale.forLanguageTag("es")
            EN -> Locale.ENGLISH
            PT -> Locale.forLanguageTag("pt-BR")
        }

    companion object {
        /** El idioma de la app (o del sistema) cuando el mensaje no deja claro el suyo. */
        fun of(locale: Locale): AssistantLang = when (locale.language) {
            "es" -> ES
            "pt" -> PT
            else -> EN
        }
    }
}

/**
 * Minúsculas y sin tildes, CARÁCTER A CARÁCTER: el texto plegado mide lo mismo
 * que el original, así un hallazgo en el plegado recorta exactamente el mismo
 * tramo del original (el título conserva «Cumple de mi mamá» con su tilde).
 */
internal fun fold(text: String): String {
    val out = CharArray(text.length)
    for (i in text.indices) {
        val c = text[i].lowercaseChar()
        out[i] = when {
            c.code < 128 -> c
            c == '’' || c == '‘' -> '\''
            else -> Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull() ?: c
        }
    }
    return String(out)
}

/**
 * Detecta español, inglés o portugués por palabras típicas. `null` cuando no
 * hay pistas («ok», «9am»): entonces sigue el idioma del turno anterior, como
 * hace iOS, y si no el de la app.
 */
object LanguageDetector {
    private val es = setOf(
        "el", "los", "las", "del", "y", "hoy", "manana", "pon", "ponme", "recuerdame", "recordarme", "avisame",
        "despiertame", "alarma", "alarmas", "recordatorio", "tengo", "mi", "mis", "una", "un", "con", "hay",
        "lunes", "martes", "miercoles", "jueves", "viernes", "noche", "cumple", "cumpleanos", "reunion", "cita",
        "borra", "elimina", "quita", "desactiva", "activa", "cambia", "mueve", "cuantos", "cuantas", "muestrame",
        "esta", "este", "proximo", "semana", "mes", "ano", "anos", "todos", "cada", "que", "cual", "cuales",
        "dias", "minutos", "horas", "antes", "pastilla", "porfa", "gracias", "hola", "llamar", "quiero",
        "necesito", "puedes", "mama", "papa", "luz", "pague", "pagar", "sacar", "basura", "misa", "futbol",
        "colocalo", "entrar", "buscar", "dentista", "medianoche", "mediodia", "madrugada", "pasado", "cuando",
        "ahora", "tambien", "dime", "nada", "algo", "de", "la", "para", "por", "en", "es", "lo", "le", "me",
    )
    private val en = setOf(
        "the", "at", "remind", "me", "alarm", "alarms", "reminder", "tomorrow", "today", "every", "day", "week",
        "month", "year", "on", "my", "to", "for", "set", "what", "do", "i", "have", "delete", "remove", "cancel",
        "turn", "off", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "birthday",
        "meeting", "minutes", "hours", "before", "in", "and", "this", "next", "show", "list", "how", "many",
        "wake", "up", "please", "call", "mom", "dad", "is", "are", "of", "with", "an",
        "tonight", "morning", "evening", "afternoon", "night", "noon", "midnight", "change", "move", "it",
        "hi", "hello", "thanks", "you", "can", "could", "take", "pill", "pay", "doctor", "appointment",
        "every", "days", "weeks", "months", "years", "after", "about", "when", "which", "there", "any",
    )
    private val pt = setOf(
        "o", "os", "as", "do", "da", "dos", "das", "no", "na", "nos", "nas", "e", "hoje", "amanha", "lembre",
        "lembra", "lembrete", "alarme", "alarmes", "tenho", "meu", "minha", "uma", "um", "com", "segunda",
        "terca", "quarta", "quinta", "sexta", "noite", "aniversario", "reuniao", "consulta", "apaga", "apague",
        "exclui", "exclua", "desativa", "desative", "ativa", "ative", "muda", "mude", "quantos", "quantas",
        "mostra", "mostre", "proxima", "semana", "mes", "ano", "todos", "todo", "toda", "todas", "cada", "que",
        "quais", "dias", "minutos", "horas", "antes", "remedio", "obrigado", "obrigada", "oi", "ola", "coloca",
        "crie", "voce", "pode", "nao", "sim", "ate", "depois", "ao", "pra", "manha", "acorde", "avise",
        "mae", "pai", "academia", "pagar", "conta", "agora", "tambem", "isso", "esse", "essa", "nesta",
        "neste", "eu", "tem", "de", "para", "por", "em", "me",
    )
    private val word = Regex("[a-z]+")

    fun detect(text: String): AssistantLang? {
        // Letras que delatan el idioma antes de plegar: ñ/¿/¡ → español, ã/õ/ç → portugués.
        var sEs = 0.0
        var sPt = 0.0
        var sEn = 0.0
        for (c in text.lowercase()) {
            when (c) {
                'ñ', '¿', '¡' -> sEs += 2
                'ã', 'õ', 'ç', 'ê', 'ô' -> sPt += 2
            }
        }
        val words = word.findAll(fold(text)).map { it.value }.toList()
        for (w in words) {
            val inEs = w in es
            val inEn = w in en
            val inPt = w in pt
            val shared = listOf(inEs, inEn, inPt).count { it }
            if (shared == 0) continue
            // Una palabra común a dos idiomas pesa menos que una exclusiva.
            val weight = if (shared == 1) 1.0 else 0.4
            if (inEs) sEs += weight
            if (inEn) sEn += weight
            if (inPt) sPt += weight
        }
        val scores = listOf(AssistantLang.ES to sEs, AssistantLang.EN to sEn, AssistantLang.PT to sPt)
            .sortedByDescending { it.second }
        val (best, top) = scores[0]
        if (top < 1.0 || top - scores[1].second < 0.6) return null
        return best
    }
}

/** Vocabulario compartido por el parser: días, meses y números en los tres idiomas (ya plegados). */
internal object Vocab {
    const val WD_ES = "lunes|martes|miercoles|jueves|viernes|sabados|sabado|domingos|domingo"
    const val WD_EN = "mondays|monday|tuesdays|tuesday|wednesdays|wednesday|thursdays|thursday|fridays|friday|saturdays|saturday|sundays|sunday"
    const val WD_PT = "segundas-feiras|segunda-feira|segundas|segunda|tercas-feiras|terca-feira|tercas|terca|" +
        "quartas-feiras|quarta-feira|quartas|quarta|quintas-feiras|quinta-feira|quintas|quinta|" +
        "sextas-feiras|sexta-feira|sextas|sexta|sabados|sabado|domingos|domingo"

    /**
     * Los nombres de día que se buscan. «segunda», «quinta» o «sexta» solo son
     * días en portugués: en español son ordinales y romperían títulos.
     */
    fun weekdays(lang: AssistantLang): String =
        if (lang == AssistantLang.PT) "$WD_PT|$WD_EN|$WD_ES" else "$WD_ES|$WD_EN"

    fun weekdayOf(word: String): Weekday? {
        val w = word.trim().lowercase().replace(Regex("-feiras?$"), "")
        Weekday.fromSlug(w)?.let { return it }
        if (w.endsWith("s")) Weekday.fromSlug(w.dropLast(1))?.let { return it }
        return null
    }

    /** «los sábados», «mondays», «segundas»: el plural ya dice que se repite. */
    fun isPluralWeekday(word: String): Boolean {
        val w = word.lowercase()
        return w.endsWith("days") || w.endsWith("feiras") || w in setOf("sabados", "domingos", "segundas", "tercas", "quartas", "quintas", "sextas")
    }

    const val MONTHS = "enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre|" +
        "january|february|march|april|may|june|july|august|september|october|november|december|" +
        "janeiro|fevereiro|marco|maio|junho|julho|setembro|outubro|novembro|dezembro|" +
        "jan|ene|feb|fev|apr|abr|aug|sept|oct|nov|dec|dic|dez"

    fun monthOf(word: String): Int? = when (word.take(3)) {
        "ene", "jan" -> 1
        "feb", "fev" -> 2
        "mar" -> 3
        "abr", "apr" -> 4
        "may", "mai" -> 5
        "jun" -> 6
        "jul" -> 7
        "ago", "aug" -> 8
        "sep", "set" -> 9
        "oct", "out" -> 10
        "nov" -> 11
        "dic", "dec", "dez" -> 12
        else -> null
    }

    private val hourWords = mapOf(
        "una" to 1, "uma" to 1, "one" to 1, "dos" to 2, "duas" to 2, "two" to 2, "tres" to 3, "three" to 3,
        "cuatro" to 4, "quatro" to 4, "four" to 4, "cinco" to 5, "five" to 5, "seis" to 6, "six" to 6,
        "siete" to 7, "sete" to 7, "seven" to 7, "ocho" to 8, "oito" to 8, "eight" to 8, "nueve" to 9,
        "nove" to 9, "nine" to 9, "diez" to 10, "dez" to 10, "ten" to 10, "once" to 11, "onze" to 11,
        "eleven" to 11, "doce" to 12, "doze" to 12, "twelve" to 12,
    )
    private val countWords = hourWords + mapOf(
        "un" to 1, "uno" to 1, "um" to 1, "a" to 1, "an" to 1, "quince" to 15, "quinze" to 15, "fifteen" to 15,
        "veinte" to 20, "vinte" to 20, "twenty" to 20, "treinta" to 30, "trinta" to 30, "thirty" to 30,
        "cuarenta" to 40, "quarenta" to 40, "forty" to 40, "otra" to 2, "otro" to 2, "other" to 2,
    )

    /** Horas en palabras («a las ocho»). Sin «once» en inglés: «at once» es «ya». */
    fun hourWordsRegex(lang: AssistantLang): String =
        hourWords.keys.filter { !(lang == AssistantLang.EN && it == "once") }.sortedByDescending { it.length }.joinToString("|")

    const val COUNT = "una|uno|un|uma|um|dos|duas|two|tres|three|cuatro|quatro|four|cinco|five|seis|six|siete|sete|seven|" +
        "ocho|oito|eight|nueve|nove|nine|diez|dez|ten|doce|doze|twelve|quince|quinze|fifteen|veinte|vinte|twenty|" +
        "treinta|trinta|thirty|cuarenta|quarenta|forty|one|an|a"

    fun number(word: String): Int? = word.toIntOrNull() ?: countWords[word.trim()]
}
