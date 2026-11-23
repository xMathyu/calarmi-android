package com.mathyusolutions.calarmi.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import com.mathyusolutions.calarmi.billing.PlusFeature
import com.mathyusolutions.calarmi.model.CountdownPrefill
import com.mathyusolutions.calarmi.model.WakeChallenge
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable data class ReminderEditorRoute(val id: String? = null, val template: String? = null, val initialDate: Long? = null, val initialTitle: String? = null)
@Serializable data class CountdownDetailRoute(val id: String)
@Serializable data class CountdownEditorRoute(val id: String? = null, val prefillJson: String? = null)
@Serializable data class PaywallRoute(val highlight: String? = null)
@Serializable object CategoriesRoute
@Serializable object CalendarSelectionRoute
@Serializable object AppIconsRoute
@Serializable data class ChallengePracticeRoute(val challengeJson: String, val title: String)

enum class HomeTab { ALARMS, CALENDAR, COUNTDOWNS, ASSISTANT, SETTINGS }

/**
 * Lo que cualquier pantalla puede pedir sin conocer el grafo de navegación.
 * Lo provee `MainActivity`.
 */
interface AppNavigator {
    fun back()
    fun selectTab(tab: HomeTab)
    /** `initialDate`: el día elegido en el calendario (la alarma empieza ese día). */
    fun newReminder(template: String? = null, initialDate: Long? = null, initialTitle: String? = null)
    fun openReminder(id: String)
    fun newCountdown(prefill: CountdownPrefill? = null)
    fun openCountdown(id: String)
    fun editCountdown(id: String)
    fun openPaywall(highlight: PlusFeature? = null)
    fun openCategories()
    fun openCalendarSelection()
    fun openAppIcons()
    fun practiceChallenge(challenge: WakeChallenge, title: String)
}

val LocalAppNavigator = staticCompositionLocalOf<AppNavigator> { error("Sin AppNavigator") }
