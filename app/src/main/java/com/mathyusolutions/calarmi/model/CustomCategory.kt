package com.mathyusolutions.calarmi.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/** Una categoría creada por la persona, junto a las de fábrica. */
@Entity(tableName = "custom_categories")
data class CustomCategory(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val colorHex: String = "#AF52DE",
    val iconKindRaw: Int = ReminderIconKind.SYMBOL.raw,
    /** SF Symbol o emoji, según `iconKind`. */
    val iconValue: String = "star.fill",
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val iconKind: ReminderIconKind get() = ReminderIconKind.fromRaw(iconKindRaw)
    val slug: String get() = name.trim().lowercase()
}
