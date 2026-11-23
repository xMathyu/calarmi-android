package com.mathyusolutions.calarmi.model

import android.content.Context
import com.mathyusolutions.calarmi.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Cada cuánto se repite una alarma. Se guarda como JSON. */
@Serializable
sealed interface RecurrenceRule {
    @Serializable @SerialName("once")
    data object Once : RecurrenceRule

    @Serializable @SerialName("daily")
    data class Daily(val interval: Int = 1) : RecurrenceRule

    @Serializable @SerialName("weekly")
    data class Weekly(val interval: Int = 1, val weekdays: Set<Weekday> = emptySet()) : RecurrenceRule

    @Serializable @SerialName("monthly")
    data class Monthly(val interval: Int = 1) : RecurrenceRule

    @Serializable @SerialName("yearly")
    data class Yearly(val interval: Int = 1) : RecurrenceRule

    val isRecurring: Boolean get() = this !is Once

    fun summary(context: Context): String {
        val res = context.resources
        return when (this) {
            Once -> res.getString(R.string.recurrence_once)
            is Daily -> if (interval == 1) res.getString(R.string.recurrence_daily)
            else res.getQuantityString(R.plurals.recurrence_every_n_days, interval, interval)
            is Weekly -> {
                val base = if (interval == 1) res.getString(R.string.recurrence_weekly)
                else res.getQuantityString(R.plurals.recurrence_every_n_weeks, interval, interval)
                if (weekdays.isEmpty()) base
                else "$base (" + weekdays.sortedBy { it.raw }.joinToString(", ") { res.getString(it.shortRes) } + ")"
            }
            is Monthly -> if (interval == 1) res.getString(R.string.recurrence_monthly)
            else res.getQuantityString(R.plurals.recurrence_every_n_months, interval, interval)
            is Yearly -> if (interval == 1) res.getString(R.string.recurrence_yearly)
            else res.getQuantityString(R.plurals.recurrence_every_n_years, interval, interval)
        }
    }
}
