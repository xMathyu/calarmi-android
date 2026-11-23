package com.mathyusolutions.calarmi.sharing

import com.google.firebase.Timestamp
import com.mathyusolutions.calarmi.data.keepingSyncFields
import com.mathyusolutions.calarmi.data.leadTimesCsv
import com.mathyusolutions.calarmi.data.parseLeadTimes
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
import java.util.Date
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El contrato de Firestore (calarmi-firebase/DESIGN.md) visto desde Android. */
class ShareMappingTest {
    private val base = 1_795_000_000_000L // 2026-11-17 aprox.

    // ------------------------------------------------------------ Recurrence

    @Test fun recurrenceMapsEveryRule() {
        assertEquals(mapOf("type" to "once", "interval" to 1, "weekdays" to emptyList<Int>()), ShareMapping.recurrenceMap(RecurrenceRule.Once))
        assertEquals(mapOf("type" to "daily", "interval" to 3, "weekdays" to emptyList<Int>()), ShareMapping.recurrenceMap(RecurrenceRule.Daily(3)))
        assertEquals(mapOf("type" to "monthly", "interval" to 1, "weekdays" to emptyList<Int>()), ShareMapping.recurrenceMap(RecurrenceRule.Monthly(1)))
        assertEquals(mapOf("type" to "yearly", "interval" to 2, "weekdays" to emptyList<Int>()), ShareMapping.recurrenceMap(RecurrenceRule.Yearly(2)))
    }

    @Test fun weekdaysUseIosNumbersSorted() {
        // 1 = domingo … 7 = sábado, como `Calendar.weekday` de iOS.
        val rule = RecurrenceRule.Weekly(2, setOf(Weekday.SATURDAY, Weekday.MONDAY, Weekday.SUNDAY, Weekday.FRIDAY))
        val map = ShareMapping.recurrenceMap(rule)
        assertEquals("weekly", map["type"])
        assertEquals(2, map["interval"])
        assertEquals(listOf(1, 2, 6, 7), map["weekdays"])
        assertEquals(rule, ShareMapping.recurrence(map))
    }

    @Test fun recurrenceRoundTrips() {
        listOf(
            RecurrenceRule.Once,
            RecurrenceRule.Daily(1),
            RecurrenceRule.Daily(21),
            RecurrenceRule.Weekly(1, Weekday.workdays),
            RecurrenceRule.Weekly(3, emptySet()),
            RecurrenceRule.Monthly(6),
            RecurrenceRule.Yearly(1),
        ).forEach { assertEquals(it, ShareMapping.recurrence(ShareMapping.recurrenceMap(it))) }
    }

    @Test fun recurrenceIsTolerant() {
        // Firestore devuelve Long; la web, números de JavaScript (Double).
        assertEquals(RecurrenceRule.Daily(2), ShareMapping.recurrence(mapOf("type" to "daily", "interval" to 2L, "weekdays" to emptyList<Long>())))
        assertEquals(RecurrenceRule.Weekly(1, setOf(Weekday.TUESDAY)), ShareMapping.recurrence(mapOf("type" to "weekly", "interval" to 1.0, "weekdays" to listOf(3.0))))
        // Intervalos fuera de rango se acotan a lo que aceptan las reglas.
        assertEquals(RecurrenceRule.Daily(1), ShareMapping.recurrence(mapOf("type" to "daily", "interval" to 0L)))
        assertEquals(999, ShareMapping.recurrenceMap(RecurrenceRule.Daily(5000))["interval"])
        // Un tipo que esta versión no conoce, o algo que no es un mapa: suena una vez.
        assertEquals(RecurrenceRule.Once, ShareMapping.recurrence(mapOf("type" to "hourly", "interval" to 1L)))
        assertEquals(RecurrenceRule.Once, ShareMapping.recurrence(null))
        assertEquals(RecurrenceRule.Once, ShareMapping.recurrence("weekly"))
        // Días inválidos se ignoran.
        assertEquals(RecurrenceRule.Weekly(1, setOf(Weekday.SUNDAY)), ShareMapping.recurrence(mapOf("type" to "weekly", "interval" to 1L, "weekdays" to listOf(1L, 0L, 9L, "x"))))
    }

    // ----------------------------------------------------------------- Alarm

    private fun sample() = Reminder(
        id = "a1b2",
        title = "  Despertar  ",
        notes = "Con café",
        date = base,
        categoryRaw = ReminderCategory.EVENT.raw,
        customCategoryId = "cat-1",
        iconKindRaw = ReminderIconKind.EMOJI.raw,
        symbolName = "☕️",
        recurrence = RecurrenceRule.Weekly(1, setOf(Weekday.MONDAY, Weekday.WEDNESDAY)),
        leadTimes = listOf(AlarmLeadTime.MIN10, AlarmLeadTime.AT_START, AlarmLeadTime.MIN10),
        additionalSchedules = listOf(
            AlarmSchedule(id = "x", date = base + 3_600_000, recurrence = RecurrenceRule.Weekly(1, setOf(Weekday.SATURDAY))),
            AlarmSchedule(id = "y", date = base + 7_200_000, recurrence = RecurrenceRule.Once),
        ),
        tone = AlarmTone.Marimba,
        challenge = WakeChallenge(WakeChallenge.Kind.QR, WakeChallenge.Difficulty.HARD, allowsSnooze = false, code = "ABC-123"),
        isEnabled = false,
    )

    private val category = CustomCategory(id = "cat-1", name = "Trabajo", colorHex = "#007AFF", iconKindRaw = ReminderIconKind.SYMBOL.raw, iconValue = "briefcase.fill")

    @Test fun alarmMapHasTheContractFields() {
        val map = ShareMapping.alarmMap(sample(), category)
        assertEquals("Despertar", map["title"])
        assertEquals("Con café", map["notes"])
        assertEquals(Date(base), map["date"])
        assertEquals(listOf(0, 600), map["leadTimes"])
        assertEquals(ReminderCategory.EVENT.raw, map["categoryRaw"])
        assertEquals(ReminderIconKind.EMOJI.raw, map["iconKindRaw"])
        assertEquals("☕️", map["symbolName"])
        assertEquals(false, map["isEnabled"])
        assertEquals("marimba", map["tone"])
        assertEquals(mapOf("kind" to "qr", "difficulty" to 3, "allowsSnooze" to false, "code" to "ABC-123"), map["challenge"])
        assertEquals(
            mapOf("id" to "cat-1", "name" to "Trabajo", "colorHex" to "#007AFF", "iconKindRaw" to 0, "iconValue" to "briefcase.fill"),
            map["customCategory"],
        )
        val extras = map["additionalSchedules"] as List<*>
        assertEquals(2, extras.size)
        assertEquals(mapOf("date" to Date(base + 3_600_000), "recurrence" to mapOf("type" to "weekly", "interval" to 1, "weekdays" to listOf(7))), extras[0])
        // Ningún número sale como Double: las reglas exigen `int`.
        assertTrue(listOf(map["categoryRaw"], map["iconKindRaw"]).all { it is Int })
    }

    @Test fun alarmRoundTripsThroughTheContract() {
        val original = sample()
        val back = ShareMapping.alarm(original.id, ShareMapping.alarmMap(original, category))!!
        val r = back.reminder
        assertEquals("Despertar", r.title)
        assertEquals(original.notes, r.notes)
        assertEquals(original.date, r.date)
        assertEquals(original.recurrence, r.recurrence)
        assertEquals(original.categoryRaw, r.categoryRaw)
        assertEquals("cat-1", r.customCategoryId)
        assertEquals(original.iconKindRaw, r.iconKindRaw)
        assertEquals(original.symbolName, r.symbolName)
        assertEquals(listOf(AlarmLeadTime.AT_START, AlarmLeadTime.MIN10), r.leadTimes)
        assertEquals(listOf(AlarmLeadTime.AT_START, AlarmLeadTime.MIN10), back.leadTimes)
        assertEquals(AlarmTone.Marimba, r.tone)
        assertEquals(original.challenge, r.challenge)
        assertFalse(r.isEnabled)
        assertEquals(category, back.customCategory!!.copy(createdAt = category.createdAt))
        // Horarios extra: mismas fechas y reglas, con ids estables (leer dos veces no es un cambio).
        assertEquals(original.additionalSchedules.map { it.date to it.recurrence }, r.additionalSchedules.map { it.date to it.recurrence })
        assertEquals(listOf("a1b2-1", "a1b2-2"), r.additionalSchedules.map { it.id })
        assertEquals(r, ShareMapping.alarm(original.id, ShareMapping.alarmMap(original, category))!!.reminder.copy(createdAt = r.createdAt, updatedAt = r.updatedAt))
    }

    @Test fun firestoreTimestampsAreRead() {
        val stamp = Timestamp(1_795_000_123L, 456_000_000)
        assertEquals(1_795_000_123_456L, ShareMapping.millis(stamp))
        val map = ShareMapping.alarmMap(sample(), null).toMutableMap()
        map["date"] = stamp
        assertEquals(1_795_000_123_456L, ShareMapping.alarm("x", map)!!.reminder.date)
    }

    @Test fun onlyBuiltinTonesTravel() {
        val imported = sample().copy(tone = AlarmTone.Imported("abc"))
        assertFalse(ShareMapping.alarmMap(imported, null).containsKey("tone"))
        assertFalse(ShareMapping.alarmMap(sample().copy(tone = null), null).containsKey("tone"))
        val back = ShareMapping.alarm("x", ShareMapping.alarmMap(sample(), null) + ("tone" to "imported:abc"))!!
        assertNull(back.reminder.tone)
    }

    @Test fun customCategoryOnlyWhenItIsTheAlarms() {
        assertFalse(ShareMapping.alarmMap(sample(), category.copy(id = "otra")).containsKey("customCategory"))
        assertFalse(ShareMapping.alarmMap(sample(), null).containsKey("customCategory"))
    }

    @Test fun titlesAndNotesFitTheRules() {
        assertEquals("Alarma", ShareMapping.alarmMap(sample().copy(title = "   "), null)["title"])
        assertEquals(200, (ShareMapping.alarmMap(sample().copy(title = "x".repeat(300)), null)["title"] as String).length)
        assertEquals(2000, (ShareMapping.alarmMap(sample().copy(notes = "n".repeat(2500)), null)["notes"] as String).length)
        assertFalse(ShareMapping.alarmMap(sample().copy(notes = "  "), null).containsKey("notes"))
        val many = sample().copy(additionalSchedules = List(30) { AlarmSchedule(date = base + it, recurrence = RecurrenceRule.Once) })
        assertEquals(20, (ShareMapping.alarmMap(many, null)["additionalSchedules"] as List<*>).size)
    }

    @Test fun leadTimesAlwaysHaveOne() {
        assertEquals(listOf(0), ShareMapping.leadTimeSeconds(emptyList()))
        assertEquals(listOf(0, 300), ShareMapping.leadTimeSeconds(listOf(300, 0, 300, -5)))
        assertEquals(10, ShareMapping.leadTimeSeconds((1..15).map { it * 60 }).size)
        // Desconocidos (p. ej. 7 minutos) se ignoran; si no queda ninguno, «al momento».
        assertEquals(listOf(AlarmLeadTime.AT_START), ShareMapping.leadTimes(listOf(420L)))
        assertEquals(listOf(AlarmLeadTime.AT_START), ShareMapping.leadTimes(null))
        assertEquals(listOf(AlarmLeadTime.MIN5, AlarmLeadTime.DAY1), ShareMapping.leadTimes(listOf(86400L, 300L, 420L)))
    }

    @Test fun unknownChallengeIsDropped() {
        assertNull(ShareMapping.challenge(mapOf("kind" to "juggle", "difficulty" to 2L, "allowsSnooze" to true)))
        assertEquals(
            WakeChallenge(WakeChallenge.Kind.MATH, WakeChallenge.Difficulty.MEDIUM, allowsSnooze = true),
            ShareMapping.challenge(mapOf("kind" to "math", "difficulty" to 9L)),
        )
        // Todos los nombres son los de iOS.
        WakeChallenge.Kind.entries.forEach { kind ->
            assertEquals(kind, ShareMapping.challenge(ShareMapping.challengeMap(WakeChallenge(kind)))!!.kind)
        }
        assertEquals("sling", ShareMapping.challengeMap(WakeChallenge(WakeChallenge.Kind.SLING))["kind"])
    }

    @Test fun incompleteAlarmIsIgnored() {
        assertNull(ShareMapping.alarm("x", mapOf("date" to Date(base))))
        assertNull(ShareMapping.alarm("x", mapOf("title" to "Sin fecha")))
        assertNull(ShareMapping.alarm("x", null))
        // Lo mínimo alcanza: lo demás toma valores por defecto.
        val minimal = ShareMapping.alarm("x", mapOf("title" to "T", "date" to Date(base)))!!.reminder
        assertEquals(RecurrenceRule.Once, minimal.recurrence)
        assertEquals(listOf(AlarmLeadTime.AT_START), minimal.leadTimes)
        assertTrue(minimal.isEnabled)
    }

    // ------------------------------------------------------------- Countdown

    @Test fun countdownRoundTrips() {
        val event = CountdownEvent(
            id = "c1", title = "Cumpleaños de Ana", notes = "Pastel", date = base, includesTime = true,
            repeatsYearly = true, colorHex = "#FF2D55", iconKindRaw = ReminderIconKind.EMOJI.raw, iconValue = "🎂",
        )
        val map = ShareMapping.countdownMap(event)
        assertEquals(setOf("title", "notes", "date", "includesTime", "repeatsYearly", "colorHex", "iconKindRaw", "iconValue"), map.keys)
        val back = ShareMapping.countdown("c1", map)!!
        assertEquals(event.copy(createdAt = back.createdAt, updatedAt = back.updatedAt), back)
        assertNull(ShareMapping.countdown("c1", mapOf("title" to "x")))
    }

    // --------------------------------------------------------------- Preview

    @Test fun previewHasTheRequiredFields() {
        val p = ShareMapping.previewMap("Viaje", base, hasTime = false, timeZone = "America/Santiago", language = "de", emoji = "✈️", repeatsYearly = true)
        assertEquals("Viaje", p["title"])
        assertEquals(Date(base), p["date"])
        assertEquals(false, p["hasTime"])
        assertEquals("America/Santiago", p["timeZone"])
        assertEquals("en", p["language"]) // solo es/en/pt
        assertEquals(true, p["repeatsYearly"])
        assertEquals("✈️", p["emoji"])
        assertFalse(p.containsKey("symbol"))
        assertFalse(p.containsKey("tintHex"))
        assertEquals(false, ShareMapping.previewMap("x", base, true, "UTC", "es")["repeatsYearly"])
        assertEquals("es", ShareMapping.language("es"))
        assertEquals("pt", ShareMapping.language("pt"))
        assertEquals("en", ShareMapping.language("de"))
        assertEquals("en", ShareMapping.language(null))
    }

    @Test fun previewIconsFollowIos() {
        val alarm = sample()
        assertEquals("☕️" to null, ShareMapping.alarmPreviewIcon(alarm, ReminderIconKind.SYMBOL, "calendar"))
        val symbol = alarm.copy(iconKindRaw = ReminderIconKind.SYMBOL.raw, symbolName = "pills.fill")
        assertEquals(null to "pills.fill", ShareMapping.alarmPreviewIcon(symbol, ReminderIconKind.SYMBOL, "calendar"))
        assertEquals(null to "calendar", ShareMapping.alarmPreviewIcon(symbol.copy(symbolName = null), ReminderIconKind.SYMBOL, "calendar"))
        // Con foto: el símbolo de la categoría, por si la foto no se puede dibujar.
        val photo = alarm.copy(iconKindRaw = ReminderIconKind.PHOTO.raw)
        assertEquals(null to "bell.fill", ShareMapping.alarmPreviewIcon(photo, ReminderIconKind.EMOJI, "🎉"))
        val countdown = CountdownEvent(iconKindRaw = ReminderIconKind.SYMBOL.raw, iconValue = "airplane")
        assertEquals(null to "airplane", ShareMapping.countdownPreviewIcon(countdown))
        assertEquals("🎂" to null, ShareMapping.countdownPreviewIcon(countdown.copy(iconKindRaw = ReminderIconKind.EMOJI.raw, iconValue = "🎂")))
    }

    // --------------------------------------------------------------- Personas

    @Test fun peopleAreSortedByArrivalWithoutMe() {
        val members = mapOf(
            "me" to mapOf("name" to "Yo", "role" to "owner", "joinedAt" to Timestamp(100, 0)),
            "b" to mapOf("name" to " Beto ", "role" to "member", "joinedAt" to Timestamp(300, 0)),
            "a" to mapOf("name" to "Ana", "role" to "member", "joinedAt" to Timestamp(200, 0)),
            "c" to mapOf("name" to "Caro", "role" to "member"),
            "bad" to "no es un mapa",
        )
        val people = ShareMapping.people(members, excluding = "me")
        assertEquals(listOf("a", "b", "c"), people.map { it.uid })
        assertEquals("Beto", people[1].name)
        assertEquals(200_000L, people[0].joinedAt)
        assertEquals(emptyList<ShareMapping.Person>(), ShareMapping.people(null))
    }

    @Test fun namesFitTheRules() {
        assertNull(ShareMapping.validName("   "))
        assertNull(ShareMapping.validName(null))
        assertEquals("Ana", ShareMapping.validName("  Ana "))
        assertEquals(60, ShareMapping.validName("x".repeat(80))!!.length)
    }

    // ------------------------------------------------------- Token y enlaces

    @Test fun tokensMatchTheRules() {
        val tokens = List(2000) { InviteTokens.generate() }
        tokens.forEach {
            assertEquals(22, it.length)
            assertTrue(it, Regex("^[A-Za-z0-9]{22}$").matches(it))
            assertTrue(InviteTokens.isValid(it))
        }
        assertEquals(tokens.size, tokens.toSet().size)
    }

    @Test fun tokensUseTheWholeAlphabet() {
        // Con un generador fijo: cada uno de los 62 caracteres sale, y ninguno más.
        val chars = (1..400).flatMap { InviteTokens.generate(Random(it.toLong())).toList() }
        assertEquals(62, chars.toSet().size)
    }

    @Test fun tokenValidation() {
        assertFalse(InviteTokens.isValid(null))
        assertFalse(InviteTokens.isValid("short"))
        assertFalse(InviteTokens.isValid("abcdefghijklmnopqrstu-"))
        assertFalse(InviteTokens.isValid("abcdefghijklmnopqrstuvw"))
        assertTrue(InviteTokens.isValid("AbCdEfGhIjKlMnOpQrStU9"))
    }

    @Test fun inviteLinks() {
        val token = "AbCdEfGhIjKlMnOpQrStU9"
        assertEquals("https://calarmi.mathyusolutions.com/s/$token/", InviteLinks.url(token))
        assertEquals(token, InviteLinks.token(InviteLinks.url(token)))
        assertEquals(token, InviteLinks.token("https://calarmi.mathyusolutions.com/s/$token"))
        assertEquals(token, InviteLinks.token("https://CALARMI.mathyusolutions.com/s/$token/?utm=x"))
        assertEquals(token, InviteLinks.token("calarmi://s/$token"))
        assertEquals(token, InviteLinks.token("calarmi://s/$token/"))
        assertNull(InviteLinks.token("https://evil.example.com/s/$token/"))
        assertNull(InviteLinks.token("https://calarmi.mathyusolutions.com/i/$token/"))
        assertNull(InviteLinks.token("https://calarmi.mathyusolutions.com/s/short/"))
        assertNull(InviteLinks.token("https://calarmi.mathyusolutions.com/s/$token/extra"))
        assertNull(InviteLinks.token("calarm://s/$token"))
        assertNull(InviteLinks.token(null))
        assertNull(InviteLinks.token("no es una url"))
    }

    // ------------------------------------------------ Lo que guarda el teléfono

    @Test fun personalLeadTimesCsv() {
        assertEquals("0,600", leadTimesCsv(listOf(AlarmLeadTime.MIN10, AlarmLeadTime.AT_START, AlarmLeadTime.MIN10)))
        assertEquals(listOf(AlarmLeadTime.AT_START, AlarmLeadTime.MIN10), parseLeadTimes("600, 0"))
        assertNull(parseLeadTimes(null))
        assertNull(parseLeadTimes("7,abc"))
    }

    @Test fun savingKeepsTheShareLink() {
        val stored = sample().copy(shareId = "a1b2", isReceivedShare = true, sharedByName = "Ana", shareRevision = 4, sharePhotoRevision = 9, ownerLeadTimes = "0", personalLeadTimes = "600")
        // El editor arma una alarma nueva sin saber nada de lo compartido.
        val edited = Reminder(id = stored.id, title = "Otro").keepingSyncFields(stored)
        assertEquals("a1b2", edited.shareId)
        assertTrue(edited.isReceivedShare)
        assertEquals("Ana", edited.sharedByName)
        assertEquals(4L, edited.shareRevision)
        assertEquals(9L, edited.sharePhotoRevision)
        assertEquals("600", edited.personalLeadTimes)
        assertEquals("Otro", edited.title)
        assertEquals(edited, edited.keepingSyncFields(null))
    }

    @Test fun mirroredAlarmClearsWhatItNoLongerHas() {
        // `set(merge)` mezcla mapas: lo opcional que la alarma ya no tiene se borra explícito.
        val plain = sample().copy(notes = null, symbolName = null, customCategoryId = null, challenge = WakeChallenge(WakeChallenge.Kind.MATH), tone = null)
        val merged = DelegationService.replacingAlarm(ShareMapping.alarmMap(plain, null))
        val delete = com.google.firebase.firestore.FieldValue.delete()
        listOf("notes", "symbolName", "customCategory", "tone").forEach { assertEquals(it, delete, merged[it]) }
        assertEquals(delete, (merged["challenge"] as Map<*, *>)["code"])
        assertEquals("math", (merged["challenge"] as Map<*, *>)["kind"])
        // Lo que sí tiene no se toca.
        val full = ShareMapping.alarmMap(sample(), category)
        assertEquals(full, DelegationService.replacingAlarm(full))
    }

    @Test fun scheduleChangesAreDetected() {
        val a = sample()
        assertTrue(ShareNotifications.sameSchedules(a, a.copy(title = "Otro", leadTimes = listOf(AlarmLeadTime.HOUR1))))
        assertTrue(ShareNotifications.sameSchedules(a, a.copy(date = a.date + 400))) // redondeo, no cambio
        assertTrue(ShareNotifications.sameSchedules(a, a.copy(additionalSchedules = a.additionalSchedules.reversed().map { it.copy(id = "z") })))
        assertFalse(ShareNotifications.sameSchedules(a, a.copy(date = a.date + 60_000)))
        assertFalse(ShareNotifications.sameSchedules(a, a.copy(recurrence = RecurrenceRule.Daily(1))))
        assertFalse(ShareNotifications.sameSchedules(a, a.copy(additionalSchedules = a.additionalSchedules.dropLast(1))))
    }
}
