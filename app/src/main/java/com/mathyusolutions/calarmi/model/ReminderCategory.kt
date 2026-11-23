package com.mathyusolutions.calarmi.model

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.mathyusolutions.calarmi.R

/** Categorías de fábrica. Los valores crudos son los de iOS. */
enum class ReminderCategory(
    val raw: Int,
    val slug: String,
    @StringRes val titleRes: Int,
    /** Nombre de SF Symbol: la app lo traduce a un ícono Material (`SymbolIcons`). */
    val defaultSymbol: String,
    val tint: Color,
    val suggestedSymbols: List<String>,
) {
    BIRTHDAY(
        0, "birthday", R.string.category_birthday, "birthday.cake.fill", Color(0xFFFF2D55),
        listOf("birthday.cake.fill", "gift.fill", "party.popper.fill", "balloon.fill", "fork.knife", "music.note", "camera.fill", "heart.fill"),
    ),
    ANNIVERSARY(
        1, "anniversary", R.string.category_anniversary, "heart.fill", Color(0xFFFF3B30),
        listOf("heart.fill", "heart.circle.fill", "rings.fill", "rosette", "champagne.bottle", "wineglass.fill", "gift.fill", "calendar"),
    ),
    EVENT(
        2, "event", R.string.category_event, "calendar", Color(0xFF007AFF),
        listOf("calendar", "calendar.badge.clock", "calendar.circle.fill", "person.2.fill", "airplane", "ticket.fill", "graduationcap.fill", "trophy.fill"),
    ),
    REMINDER(
        3, "reminder", R.string.category_reminder, "bell.fill", Color(0xFFFF9500),
        listOf("bell.fill", "alarm.fill", "checkmark.circle.fill", "list.bullet.clipboard.fill", "pill.fill", "stethoscope", "creditcard.fill", "house.fill"),
    ),
    OTHER(
        4, "other", R.string.category_other, "star.fill", Color(0xFFAF52DE),
        listOf("star.fill", "flag.fill", "bookmark.fill", "tag.fill", "pencil", "lightbulb.fill", "leaf.fill", "globe"),
    );

    companion object {
        /** Orden en pantalla: la más usada primero. */
        val displayOrder = listOf(REMINDER, EVENT, BIRTHDAY, ANNIVERSARY, OTHER)

        fun fromRaw(raw: Int): ReminderCategory = entries.firstOrNull { it.raw == raw } ?: OTHER

        fun fromSlug(slug: String): ReminderCategory? = entries.firstOrNull { it.slug == slug.trim().lowercase() }
    }
}
