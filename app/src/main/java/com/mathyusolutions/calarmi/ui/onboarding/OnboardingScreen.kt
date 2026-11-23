package com.mathyusolutions.calarmi.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.alarm.AlarmPermissions
import com.mathyusolutions.calarmi.ui.components.HeroIcon
import com.mathyusolutions.calarmi.ui.theme.DS
import com.mathyusolutions.calarmi.ui.theme.LocalAccent
import kotlinx.coroutines.launch

private data class Slide(val icon: ImageVector, val title: Int, val detail: Int)

private val slides = listOf(
    Slide(Icons.Filled.AlarmOn, R.string.onboarding_1_title, R.string.onboarding_1_detail),
    Slide(Icons.Filled.Cake, R.string.onboarding_2_title, R.string.onboarding_2_detail),
    Slide(Icons.Filled.EventNote, R.string.onboarding_3_title, R.string.onboarding_3_detail),
    Slide(Icons.Filled.AutoAwesome, R.string.onboarding_4_title, R.string.onboarding_4_detail),
    Slide(Icons.Filled.NotificationsActive, R.string.onboarding_5_title, R.string.onboarding_5_detail),
)

/** La bienvenida: qué hace Calarmi y, al final, el permiso de notificaciones. */
@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    val container = CalarmiApp.container(context)
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    val accent = LocalAccent.current
    fun finish() {
        container.settings.update { it.copy(onboardingCompleted = true) }
        // Si falta algo más (alarmas exactas, pantalla completa), la lista lo dice arriba.
        val missing = AlarmPermissions.missing(context)
        if (AlarmPermissions.Missing.FULL_SCREEN in missing) {
            runCatching { context.startActivity(AlarmPermissions.settingsIntent(context, AlarmPermissions.Missing.FULL_SCREEN)) }
        }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)),
        ),
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(DS.sm), horizontalArrangement = Arrangement.End) {
                if (pager.currentPage < slides.lastIndex) {
                    TextButton(onClick = { scope.launch { pager.animateScrollToPage(slides.lastIndex) } }) { Text(stringResource(R.string.onboarding_skip)) }
                } else Spacer(Modifier.height(48.dp))
            }
            HorizontalPager(pager, modifier = Modifier.weight(1f)) { page ->
                val slide = slides[page]
                Column(
                    Modifier.fillMaxSize().padding(horizontal = DS.xxxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    HeroIcon(slide.icon)
                    Spacer(Modifier.height(DS.xxl))
                    Text(stringResource(slide.title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(DS.md))
                    Text(stringResource(slide.detail), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            Row(Modifier.fillMaxWidth().padding(DS.lg), horizontalArrangement = Arrangement.Center) {
                repeat(slides.size) { i ->
                    val color by animateColorAsState(if (i == pager.currentPage) accent else MaterialTheme.colorScheme.outline, label = "dot")
                    Box(Modifier.padding(4.dp).size(8.dp).clip(CircleShape).background(color))
                }
            }
            Button(
                onClick = {
                    if (pager.currentPage < slides.lastIndex) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    else if (AlarmPermissions.needsNotificationRuntimePermission(context)) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else finish()
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = DS.xl, vertical = DS.md).height(52.dp),
            ) {
                Text(stringResource(if (pager.currentPage < slides.lastIndex) R.string.onboarding_next else R.string.onboarding_allow), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(DS.md))
        }
    }
}
