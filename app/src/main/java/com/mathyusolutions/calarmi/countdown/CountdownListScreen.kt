package com.mathyusolutions.calarmi.countdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.billing.PlusFeature
import com.mathyusolutions.calarmi.billing.PlusStore
import com.mathyusolutions.calarmi.model.CountdownEvent
import com.mathyusolutions.calarmi.ui.components.EmptyState
import com.mathyusolutions.calarmi.ui.navigation.LocalAppNavigator
import com.mathyusolutions.calarmi.ui.theme.DS
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * La pestaña «Cuenta regresiva»: una tarjeta por evento, lo más próximo
 * primero, y los que ya pasaron al final. Los días cambian solos a medianoche.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownListScreen() {
    val context = LocalContext.current
    val container = CalarmiApp.container(context)
    val navigator = LocalAppNavigator.current
    val events by container.countdowns.countdowns.collectAsStateWithLifecycle(initialValue = null)
    val isPlus by container.plus.isPlus.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    // Cada 30 s basta: las tarjetas cuentan días, y así «¡Hoy!» llega a
    // medianoche aunque la app se quede abierta.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { now = System.currentTimeMillis() }

    /**
     * Sin Plus caben `FREE_COUNTDOWN_LIMIT` cuentas. Las que ya había no se
     * tocan aunque sean más: solo se frena crear otra.
     */
    fun startNew() {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val count = events?.size ?: 0
        if (count >= PlusStore.FREE_COUNTDOWN_LIMIT && !isPlus) navigator.openPaywall(PlusFeature.UNLIMITED_COUNTDOWNS)
        else navigator.newCountdown()
    }

    // Borrar no espera a la pantalla: sigue aunque se cambie de pestaña.
    fun delete(event: CountdownEvent) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        container.scope.launch { container.countdowns.delete(event.id) }
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tab_countdowns), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            if (!events.isNullOrEmpty()) {
                FloatingActionButton(onClick = ::startNew, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Filled.Add, stringResource(R.string.countdowns_new))
                }
            }
        },
    ) { padding ->
        val list = events ?: return@Scaffold
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (list.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.HourglassBottom,
                    title = stringResource(R.string.countdowns_empty_title),
                    message = stringResource(R.string.countdowns_empty_message),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Button(onClick = ::startNew) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(Modifier.width(DS.sm))
                        Text(stringResource(R.string.countdowns_new))
                    }
                }
            } else {
                val groups = remember(list, now) { CountdownOrder.groups(list, now) }
                LazyColumn(contentPadding = PaddingValues(top = DS.xs, bottom = 96.dp)) {
                    items(groups.upcoming, key = { it.id }) { event ->
                        CountdownRow(event, now, onOpen = { navigator.openCountdown(event.id) }, onEdit = { navigator.editCountdown(event.id) }, onDelete = { delete(event) })
                    }
                    if (groups.past.isNotEmpty()) {
                        item(key = "past-header") {
                            Text(
                                stringResource(R.string.countdowns_past).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = DS.xxxl, end = DS.lg, top = DS.xl, bottom = DS.xs),
                            )
                        }
                        items(groups.past, key = { it.id }) { event ->
                            CountdownRow(event, now, onOpen = { navigator.openCountdown(event.id) }, onEdit = { navigator.editCountdown(event.id) }, onDelete = { delete(event) })
                        }
                    }
                }
            }
        }
    }
}

/** Una tarjeta: tocar abre el detalle, deslizar borra y mantener pulsado ofrece editar o borrar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountdownRow(event: CountdownEvent, now: Long, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) onDelete()
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        modifier = Modifier.padding(horizontal = DS.lg, vertical = DS.xs),
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(DS.radiusXl)).background(MaterialTheme.colorScheme.error).padding(end = DS.xl),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete), tint = Color.White) }
        },
    ) {
        Box(Modifier.fillMaxWidth()) {
            CountdownCard(
                content = event.cardContent(),
                now = now,
                onClick = onOpen,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menu = true
                },
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.countdowns_edit)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    onClick = { menu = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}
