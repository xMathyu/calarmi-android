package com.mathyusolutions.calarmi.assistant

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageDetectorTest {
    private fun detect(text: String) = LanguageDetector.detect(text)

    @Test fun spanish() {
        listOf(
            "¿Qué tengo hoy?",
            "Recuérdame la pastilla todos los días a las 9",
            "pon una alarma de mi daily todos los lunes a las 9",
            "hay treasure hours en fortnite este sabado 14 en peru de 2 a 4 y de 9 a 11",
            "Reunión con Ana el jueves",
            "fútbol todos los sábados de 3 a 5",
            "borra la alarma del gym",
        ).forEach { assertEquals(it, AssistantLang.ES, detect(it)) }
    }

    @Test fun english() {
        listOf(
            "Set an alarm this Friday at 7am for the gym",
            "remind me to call mom tomorrow at 6pm",
            "what do I have this week",
            "this week",
            "delete the gym alarm",
        ).forEach { assertEquals(it, AssistantLang.EN, detect(it)) }
    }

    @Test fun portuguese() {
        listOf(
            "O que eu tenho hoje?",
            "Me lembre do remédio todos os dias às 9",
            "Crie um alarme para amanhã às 8h para o remédio",
            "apague o alarme da academia",
            "reunião toda segunda às 10",
        ).forEach { assertEquals(it, AssistantLang.PT, detect(it)) }
    }

    /** Sin pistas sigue el idioma anterior (o el de la app), como en iOS. */
    @Test fun ambiguousIsNull() {
        listOf("ok", "9am", "Gym", "sí").forEach { assertNull(it, detect(it)) }
    }

    @Test fun appLocaleFallback() {
        assertEquals(AssistantLang.ES, AssistantLang.of(Locale.forLanguageTag("es-PE")))
        assertEquals(AssistantLang.PT, AssistantLang.of(Locale.forLanguageTag("pt-BR")))
        assertEquals(AssistantLang.EN, AssistantLang.of(Locale.FRENCH))
    }

    @Test fun foldKeepsLength() {
        val text = "¿Cumpleaños de mamá? Ação"
        assertEquals(text.length, fold(text).length)
        assertEquals("¿cumpleanos de mama? acao", fold(text))
    }
}
