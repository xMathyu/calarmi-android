package com.mathyusolutions.calarmi

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mathyusolutions.calarmi.billing.PlusFeature
import com.mathyusolutions.calarmi.calendar.CalendarSelectionScreen
import com.mathyusolutions.calarmi.challenge.ChallengeScreen
import com.mathyusolutions.calarmi.countdown.CountdownDetailScreen
import com.mathyusolutions.calarmi.countdown.CountdownEditorScreen
import com.mathyusolutions.calarmi.data.AppJson
import com.mathyusolutions.calarmi.model.CountdownPrefill
import com.mathyusolutions.calarmi.model.WakeChallenge
import com.mathyusolutions.calarmi.sharing.InviteLinks
import com.mathyusolutions.calarmi.sharing.ShareMapping
import com.mathyusolutions.calarmi.sharing.ui.IncomingShareToast
import com.mathyusolutions.calarmi.sharing.ui.InviteSheet
import com.mathyusolutions.calarmi.sharing.ui.ManagedListsScreen
import com.mathyusolutions.calarmi.sharing.ui.PrincipalAlarmsScreen
import com.mathyusolutions.calarmi.sharing.ui.TrustedPeopleScreen
import com.mathyusolutions.calarmi.ui.HomeScreen
import com.mathyusolutions.calarmi.ui.alarms.ReminderEditorScreen
import com.mathyusolutions.calarmi.ui.navigation.AppIconsRoute
import com.mathyusolutions.calarmi.ui.navigation.AppNavigator
import com.mathyusolutions.calarmi.ui.navigation.CalendarSelectionRoute
import com.mathyusolutions.calarmi.ui.navigation.CategoriesRoute
import com.mathyusolutions.calarmi.ui.navigation.ChallengePracticeRoute
import com.mathyusolutions.calarmi.ui.navigation.CountdownDetailRoute
import com.mathyusolutions.calarmi.ui.navigation.CountdownEditorRoute
import com.mathyusolutions.calarmi.ui.navigation.HomeRoute
import com.mathyusolutions.calarmi.ui.navigation.HomeTab
import com.mathyusolutions.calarmi.ui.navigation.LocalAppNavigator
import com.mathyusolutions.calarmi.ui.navigation.ManagedListsRoute
import com.mathyusolutions.calarmi.ui.navigation.PaywallRoute
import com.mathyusolutions.calarmi.ui.navigation.PrincipalRoute
import com.mathyusolutions.calarmi.ui.navigation.ReminderEditorRoute
import com.mathyusolutions.calarmi.ui.navigation.TrustedPeopleRoute
import com.mathyusolutions.calarmi.ui.plus.AppIconsScreen
import com.mathyusolutions.calarmi.ui.plus.PaywallScreen
import com.mathyusolutions.calarmi.ui.settings.CategoryManagementScreen
import com.mathyusolutions.calarmi.ui.theme.CalarmiTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    /** Pestaña pedida desde fuera (widget, atajo, notificación). */
    private val requestedTab = mutableStateOf<HomeTab?>(null)
    private val requestNewAlarm = mutableStateOf(false)
    private val requestCountdown = mutableStateOf<String?>(null)
    private val requestReminder = mutableStateOf<String?>(null)
    /** Token de un enlace de invitación abierto (https://…/s/<token>/ o calarmi://s/<token>). */
    private val requestInvite = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handle(intent)
        val container = CalarmiApp.container(this)
        setContent {
            val settings by container.settings.state.collectAsStateWithLifecycle()
            CalarmiTheme(settings) {
                val nav = rememberNavController()
                val navigator = remember(nav) { navigator(nav) }
                var tab by remember { mutableStateOf(HomeTab.ALARMS) }
                requestedTab.value?.let { tab = it; requestedTab.value = null }
                if (requestNewAlarm.value) {
                    requestNewAlarm.value = false
                    navigator.newReminder()
                }
                requestCountdown.value?.let { id ->
                    requestCountdown.value = null
                    tab = HomeTab.COUNTDOWNS
                    navigator.openCountdown(id)
                }
                requestReminder.value?.let { id ->
                    requestReminder.value = null
                    tab = HomeTab.ALARMS
                    navigator.openReminder(id)
                }
                val incoming by container.sharing.incoming.collectAsStateWithLifecycle()
                CompositionLocalProvider(LocalAppNavigator provides navigator) {
                    Box(Modifier.fillMaxSize()) {
                        AppNavHost(nav, tab) { tab = it }
                        IncomingShareToast(
                            status = incoming,
                            onOpen = { added ->
                                container.sharing.dismissIncoming()
                                openShared(navigator, added.kind, added.id, added.title) { tab = it }
                            },
                            onDismiss = { container.sharing.dismissIncoming() },
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                    requestInvite.value?.let { token ->
                        // Un enlace nuevo mientras se cierra la hoja anterior: el cierre tardío
                        // de esa no debe borrar el pedido nuevo.
                        fun clear() { if (requestInvite.value == token) requestInvite.value = null }
                        androidx.compose.runtime.key(token) {
                            InviteSheet(
                                token = token,
                                onDismiss = ::clear,
                                onAccepted = { accepted ->
                                    clear()
                                    if (accepted.isMine && accepted.kind == ShareMapping.KIND_DELEGATION) navigator.openTrustedPeople()
                                    else openShared(navigator, accepted.kind, accepted.id, accepted.title) { tab = it }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        // Al volver a la app (p. ej. tras dar un permiso) se recalcula todo.
        val container = CalarmiApp.container(this)
        lifecycleScope.launch { container.onDataChanged() }
        container.plus.refresh()
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            ACTION_NEW_ALARM -> requestNewAlarm.value = true
            ACTION_OPEN_TAB -> intent.getStringExtra(EXTRA_TAB)?.let { name ->
                HomeTab.entries.firstOrNull { it.name == name }?.let { requestedTab.value = it }
            }
            ACTION_OPEN_COUNTDOWN -> requestCountdown.value = intent.getStringExtra(EXTRA_ID)
            ACTION_OPEN_REMINDER -> requestReminder.value = intent.getStringExtra(EXTRA_ID)
            Intent.ACTION_VIEW -> InviteLinks.token(intent.dataString)?.let { requestInvite.value = it }
        }
    }

    /** Lleva a lo recién aceptado (o a lo propio, si la invitación era de esta persona). */
    private fun openShared(navigator: AppNavigator, kind: String, id: String, title: String, selectTab: (HomeTab) -> Unit) {
        when (kind) {
            ShareMapping.KIND_COUNTDOWN -> { selectTab(HomeTab.COUNTDOWNS); navigator.openCountdown(id) }
            ShareMapping.KIND_DELEGATION -> navigator.openPrincipal(id, title)
            else -> { selectTab(HomeTab.ALARMS); navigator.openReminder(id) }
        }
    }

    private fun navigator(nav: NavHostController) = object : AppNavigator {
        override fun back() {
            nav.popBackStack()
        }
        override fun selectTab(tab: HomeTab) {
            requestedTab.value = tab
            nav.popBackStack(HomeRoute, inclusive = false)
        }
        override fun newReminder(template: String?, initialDate: Long?, initialTitle: String?) =
            nav.navigate(ReminderEditorRoute(template = template, initialDate = initialDate, initialTitle = initialTitle)) { launchSingleTop = true }
        override fun openReminder(id: String) = nav.navigate(ReminderEditorRoute(id = id)) { launchSingleTop = true }
        override fun newCountdown(prefill: CountdownPrefill?) = nav.navigate(
            CountdownEditorRoute(prefillJson = prefill?.let { AppJson.encodeToString(CountdownPrefill.serializer(), it) }),
        )
        override fun openCountdown(id: String) = nav.navigate(CountdownDetailRoute(id)) { launchSingleTop = true }
        override fun editCountdown(id: String) = nav.navigate(CountdownEditorRoute(id = id))
        override fun openPaywall(highlight: PlusFeature?) = nav.navigate(PaywallRoute(highlight?.name)) { launchSingleTop = true }
        override fun openCategories() = nav.navigate(CategoriesRoute)
        override fun openCalendarSelection() = nav.navigate(CalendarSelectionRoute)
        override fun openAppIcons() = nav.navigate(AppIconsRoute)
        override fun practiceChallenge(challenge: WakeChallenge, title: String) = nav.navigate(
            ChallengePracticeRoute(AppJson.encodeToString(WakeChallenge.serializer(), challenge), title),
        )
        override fun openTrustedPeople() = nav.navigate(TrustedPeopleRoute) { launchSingleTop = true }
        override fun openManagedLists() = nav.navigate(ManagedListsRoute) { launchSingleTop = true }
        override fun openPrincipal(uid: String, name: String) = nav.navigate(PrincipalRoute(uid, name)) { launchSingleTop = true }
        override fun newDelegatedReminder(principal: String, principalName: String) =
            nav.navigate(ReminderEditorRoute(principal = principal, principalName = principalName))
        override fun openDelegatedReminder(principal: String, principalName: String, id: String) =
            nav.navigate(ReminderEditorRoute(id = id, principal = principal, principalName = principalName)) { launchSingleTop = true }
    }

    companion object {
        const val ACTION_NEW_ALARM = "com.mathyusolutions.calarmi.action.NEW_ALARM"
        const val ACTION_OPEN_TAB = "com.mathyusolutions.calarmi.action.OPEN_TAB"
        const val ACTION_OPEN_COUNTDOWN = "com.mathyusolutions.calarmi.action.OPEN_COUNTDOWN"
        const val ACTION_OPEN_REMINDER = "com.mathyusolutions.calarmi.action.OPEN_REMINDER"
        const val EXTRA_TAB = "tab"
        const val EXTRA_ID = "id"
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, tab: HomeTab, onTab: (HomeTab) -> Unit) {
    NavHost(
        navController = nav,
        startDestination = HomeRoute,
        enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() },
    ) {
        composable<HomeRoute> { HomeScreen(tab, onTab) }
        composable<ReminderEditorRoute> {
            val route = it.toRoute<ReminderEditorRoute>()
            ReminderEditorScreen(
                id = route.id,
                template = route.template,
                initialDate = route.initialDate,
                initialTitle = route.initialTitle,
                principal = route.principal,
                principalName = route.principalName,
                onDone = { nav.popBackStack() },
            )
        }
        composable<CountdownDetailRoute> {
            CountdownDetailScreen(id = it.toRoute<CountdownDetailRoute>().id, onBack = { nav.popBackStack() })
        }
        composable<CountdownEditorRoute> {
            val route = it.toRoute<CountdownEditorRoute>()
            val prefill = route.prefillJson?.let { json -> runCatching { AppJson.decodeFromString(CountdownPrefill.serializer(), json) }.getOrNull() }
            CountdownEditorScreen(id = route.id, prefill = prefill, onDone = { nav.popBackStack() })
        }
        composable<PaywallRoute> {
            val highlight = it.toRoute<PaywallRoute>().highlight?.let { name -> PlusFeature.entries.firstOrNull { f -> f.name == name } }
            PaywallScreen(highlight = highlight, onClose = { nav.popBackStack() })
        }
        composable<CategoriesRoute> { CategoryManagementScreen(onBack = { nav.popBackStack() }) }
        composable<CalendarSelectionRoute> { CalendarSelectionScreen(onBack = { nav.popBackStack() }) }
        composable<AppIconsRoute> { AppIconsScreen(onBack = { nav.popBackStack() }) }
        composable<TrustedPeopleRoute> { TrustedPeopleScreen(onBack = { nav.popBackStack() }) }
        composable<ManagedListsRoute> { ManagedListsScreen(onBack = { nav.popBackStack() }) }
        composable<PrincipalRoute> {
            val route = it.toRoute<PrincipalRoute>()
            PrincipalAlarmsScreen(principal = route.uid, name = route.name, onBack = { nav.popBackStack() })
        }
        composable<ChallengePracticeRoute> {
            val route = it.toRoute<ChallengePracticeRoute>()
            val challenge = runCatching { AppJson.decodeFromString(WakeChallenge.serializer(), route.challengeJson) }.getOrNull()
            if (challenge == null) nav.popBackStack()
            else ChallengeScreen(
                challenge = challenge,
                title = route.title,
                isPractice = true,
                onSolved = { nav.popBackStack() },
                onSnooze = null,
                onClose = { nav.popBackStack() },
            )
        }
    }
}
