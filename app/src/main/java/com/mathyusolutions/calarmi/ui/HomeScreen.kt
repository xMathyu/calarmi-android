package com.mathyusolutions.calarmi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.assistant.AssistantScreen
import com.mathyusolutions.calarmi.calendar.CalendarScreen
import com.mathyusolutions.calarmi.countdown.CountdownListScreen
import com.mathyusolutions.calarmi.ui.alarms.AlarmsScreen
import com.mathyusolutions.calarmi.ui.navigation.HomeTab
import com.mathyusolutions.calarmi.ui.onboarding.OnboardingScreen
import com.mathyusolutions.calarmi.ui.settings.SettingsScreen

@Composable
fun HomeScreen(tab: HomeTab, onTab: (HomeTab) -> Unit) {
    val container = CalarmiApp.container(LocalContext.current)
    val settings by container.settings.state.collectAsStateWithLifecycle()
    if (!settings.onboardingCompleted) {
        OnboardingScreen()
        return
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TabItem(HomeTab.ALARMS, tab, onTab, Icons.Filled.Notifications, R.string.tab_alarms)
                TabItem(HomeTab.CALENDAR, tab, onTab, Icons.Filled.CalendarMonth, R.string.tab_calendar)
                TabItem(HomeTab.COUNTDOWNS, tab, onTab, Icons.Filled.HourglassBottom, R.string.tab_countdowns)
                TabItem(HomeTab.ASSISTANT, tab, onTab, Icons.Filled.AutoAwesome, R.string.tab_assistant)
                TabItem(HomeTab.SETTINGS, tab, onTab, Icons.Filled.Settings, R.string.tab_settings)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            when (tab) {
                HomeTab.ALARMS -> AlarmsScreen()
                HomeTab.CALENDAR -> CalendarScreen()
                HomeTab.COUNTDOWNS -> CountdownListScreen()
                HomeTab.ASSISTANT -> AssistantScreen()
                HomeTab.SETTINGS -> SettingsScreen()
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(
    item: HomeTab,
    selected: HomeTab,
    onTab: (HomeTab) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: Int,
) {
    NavigationBarItem(
        selected = item == selected,
        onClick = { onTab(item) },
        icon = { Icon(icon, contentDescription = null) },
        // «Cuenta regresiva» / «Contagem regressiva» no caben en cinco pestañas: la
        // etiqueta se achica hasta caber en vez de cortarse con «…».
        label = {
            androidx.compose.foundation.text.BasicText(
                stringResource(label),
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall.copy(color = androidx.compose.material3.LocalContentColor.current),
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = MaterialTheme.typography.labelSmall.fontSize),
            )
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    )
}
