package com.mathyusolutions.calarmi.ui.plus

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.billing.PlusFeature
import com.mathyusolutions.calarmi.billing.PlusStore
import com.mathyusolutions.calarmi.ui.components.SymbolIcons
import com.mathyusolutions.calarmi.ui.theme.DS
import com.mathyusolutions.calarmi.ui.theme.LocalAccent

/** Calarmi Plus: qué suma, los dos planes y «Restaurar compras». */
@Composable
fun PaywallScreen(highlight: PlusFeature?, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = CalarmiApp.container(context)
    val isPlus by container.plus.isPlus.collectAsStateWithLifecycle()
    val offers by container.plus.offers.collectAsStateWithLifecycle()
    val loadState by container.plus.loadState.collectAsStateWithLifecycle()
    val error by container.plus.purchaseError.collectAsStateWithLifecycle()
    var plan by remember { mutableStateOf(PlusStore.Product.YEARLY) }
    val accent = LocalAccent.current
    val purple = Color(0xFFAF52DE)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(DS.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.action_close)) }
            }
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(accent, purple))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(48.dp)) }
            Spacer(Modifier.height(DS.lg))
            Text(stringResource(R.string.plus_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(DS.sm))
            Text(stringResource(R.string.plus_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(DS.xl))

            PlusFeature.entries.forEach { feature ->
                val lit = feature == highlight
                Surface(
                    shape = RoundedCornerShape(DS.radiusMd),
                    color = if (lit) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer,
                    border = if (lit) BorderStroke(1.5.dp, accent) else null,
                    modifier = Modifier.fillMaxWidth().padding(vertical = DS.xs),
                ) {
                    Row(Modifier.padding(DS.md), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Icon(SymbolIcons.of(feature.symbol), null, tint = accent)
                        }
                        Column(Modifier.padding(start = DS.md)) {
                            Text(stringResource(feature.titleRes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (feature == PlusFeature.UNLIMITED_COUNTDOWNS) stringResource(feature.descriptionRes, PlusStore.FREE_COUNTDOWN_LIMIT) else stringResource(feature.descriptionRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(DS.sm))
            Text(stringResource(R.string.plus_always_free), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(DS.xl))

            if (isPlus) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Verified, null, tint = Color(0xFF34C759))
                    Spacer(Modifier.width(DS.sm))
                    Text(stringResource(R.string.plus_owned), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(stringResource(R.string.plus_thanks), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?package=${context.packageName}")))
                }) { Text(stringResource(R.string.plus_manage_subscription)) }
            } else {
                val o = offers
                if (o == null || loadState == PlusStore.LoadState.FAILED) {
                    Text(
                        stringResource(if (loadState == PlusStore.LoadState.FAILED) R.string.plus_store_unavailable else R.string.plus_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (loadState == PlusStore.LoadState.FAILED) TextButton(onClick = { container.plus.connect() }) { Text(stringResource(R.string.action_retry)) }
                } else {
                    PlanCard(
                        title = stringResource(R.string.plus_yearly),
                        badge = o.yearlyTrialPeriod?.let { stringResource(R.string.plus_trial_badge) },
                        price = o.yearlyPrice ?: "—",
                        caption = stringResource(R.string.plus_per_year),
                        selected = plan == PlusStore.Product.YEARLY,
                        onClick = { plan = PlusStore.Product.YEARLY },
                    )
                    PlanCard(
                        title = stringResource(R.string.plus_lifetime),
                        badge = null,
                        price = o.lifetimePrice ?: "—",
                        caption = stringResource(R.string.plus_one_time),
                        selected = plan == PlusStore.Product.LIFETIME,
                        onClick = { plan = PlusStore.Product.LIFETIME },
                    )
                    Spacer(Modifier.height(DS.md))
                    Button(
                        onClick = { context.findActivity()?.let { container.plus.purchase(it, plan) } },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(
                            stringResource(if (plan == PlusStore.Product.YEARLY && o.yearlyTrialPeriod != null) R.string.plus_cta_trial else R.string.plus_cta_buy),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = DS.sm))
                    }
                    Text(
                        stringResource(if (plan == PlusStore.Product.YEARLY) R.string.plus_legal_yearly else R.string.plus_legal_lifetime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = DS.md),
                    )
                }
                OutlinedButton(onClick = { container.plus.refresh() }, modifier = Modifier.padding(top = DS.md)) {
                    Text(stringResource(R.string.plus_restore))
                }
            }
            Spacer(Modifier.height(DS.xxl))
        }
    }
}

@Composable
private fun PlanCard(title: String, badge: String?, price: String, caption: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Surface(
        shape = RoundedCornerShape(DS.radiusLg),
        color = if (selected) accent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = DS.xs).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(DS.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = if (selected) accent else MaterialTheme.colorScheme.outline)
            Column(Modifier.weight(1f).padding(start = DS.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (badge != null) {
                        Text(
                            badge,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = DS.sm).clip(RoundedCornerShape(50)).background(accent).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(price, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

fun android.content.Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
