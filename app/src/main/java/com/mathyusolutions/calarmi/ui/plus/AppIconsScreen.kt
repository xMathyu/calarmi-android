package com.mathyusolutions.calarmi.ui.plus

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.billing.PlusFeature
import com.mathyusolutions.calarmi.ui.components.PlusBadge
import com.mathyusolutions.calarmi.ui.components.ScreenScaffold
import com.mathyusolutions.calarmi.ui.components.SectionCard
import com.mathyusolutions.calarmi.ui.navigation.LocalAppNavigator
import com.mathyusolutions.calarmi.ui.theme.DS

/**
 * Los íconos de colores (Plus). En Android cada uno es un `activity-alias` del
 * lanzador: se habilita el elegido y se deshabilitan los demás.
 */
enum class AppIcon(val alias: String, val titleRes: Int, val previewRes: Int) {
    DEFAULT(".LauncherDefault", R.string.app_icon_default, R.mipmap.ic_launcher),
    BLUE(".LauncherBlue", R.string.app_icon_blue, R.mipmap.ic_launcher_blue),
    PURPLE(".LauncherPurple", R.string.app_icon_purple, R.mipmap.ic_launcher_purple),
    GREEN(".LauncherGreen", R.string.app_icon_green, R.mipmap.ic_launcher_green),
    PINK(".LauncherPink", R.string.app_icon_pink, R.mipmap.ic_launcher_pink);

    fun component(context: Context) = ComponentName(context.packageName, "com.mathyusolutions.calarmi$alias")

    companion object {
        /** El de color que esté habilitado; si ninguno, el predeterminado. */
        fun current(context: Context): AppIcon = entries.drop(1).firstOrNull {
            context.packageManager.getComponentEnabledSetting(it.component(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } ?: DEFAULT

        fun apply(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            entries.forEach { other ->
                val state = when {
                    other == icon -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                pm.setComponentEnabledSetting(other.component(context), state, PackageManager.DONT_KILL_APP)
            }
        }
    }
}

@Composable
fun AppIconsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = CalarmiApp.container(context)
    val navigator = LocalAppNavigator.current
    val isPlus by container.plus.isPlus.collectAsStateWithLifecycle()
    var current by remember { mutableStateOf(AppIcon.current(context)) }
    ScreenScaffold(title = stringResource(R.string.settings_app_icon), onBack = onBack) {
        SectionCard(footer = stringResource(R.string.app_icon_footer)) {
            AppIcon.entries.forEach { icon ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (icon != AppIcon.DEFAULT && !isPlus) navigator.openPaywall(PlusFeature.APP_ICONS)
                        else {
                            AppIcon.apply(context, icon)
                            current = icon
                        }
                    }.padding(horizontal = DS.lg, vertical = DS.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(painterResource(icon.previewRes), null, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
                    Text(stringResource(icon.titleRes), modifier = Modifier.weight(1f).padding(start = DS.md), style = MaterialTheme.typography.bodyLarge)
                    if (icon != AppIcon.DEFAULT && !isPlus) PlusBadge()
                    if (current == icon) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
