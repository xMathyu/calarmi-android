package com.mathyusolutions.calarmi.ui.components

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.mathyusolutions.calarmi.model.CustomCategory
import com.mathyusolutions.calarmi.model.Reminder
import com.mathyusolutions.calarmi.model.ReminderCategory
import com.mathyusolutions.calarmi.model.ReminderIconKind
import com.mathyusolutions.calarmi.ui.theme.parseHex

/** Una categoría (de fábrica o propia) lista para pintar. */
sealed interface CategorySelection {
    data class Builtin(val category: ReminderCategory) : CategorySelection
    data class Custom(val id: String) : CategorySelection
}

data class CategoryStyle(
    val selection: CategorySelection,
    val title: String,
    val color: Color,
    val iconKind: ReminderIconKind,
    val iconValue: String,
) {
    companion object {
        fun of(context: Context, category: ReminderCategory) = CategoryStyle(
            CategorySelection.Builtin(category), context.getString(category.titleRes), category.tint,
            ReminderIconKind.SYMBOL, category.defaultSymbol,
        )

        fun of(custom: CustomCategory) = CategoryStyle(
            CategorySelection.Custom(custom.id), custom.name, parseHex(custom.colorHex) ?: Color(0xFFAF52DE),
            custom.iconKind, custom.iconValue,
        )

        fun of(context: Context, reminder: Reminder, customs: List<CustomCategory>): CategoryStyle =
            reminder.customCategoryId?.let { id -> customs.firstOrNull { it.id == id } }?.let(::of)
                ?: of(context, reminder.category)
    }
}
