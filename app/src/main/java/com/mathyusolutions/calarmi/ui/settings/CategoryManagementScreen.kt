package com.mathyusolutions.calarmi.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.model.CustomCategory
import com.mathyusolutions.calarmi.model.ReminderCategory
import com.mathyusolutions.calarmi.model.ReminderIconKind
import com.mathyusolutions.calarmi.ui.alarms.EditorTextField
import com.mathyusolutions.calarmi.ui.alarms.IconPickerSheet
import com.mathyusolutions.calarmi.ui.components.CategoryStyle
import com.mathyusolutions.calarmi.ui.components.ReminderIcon
import com.mathyusolutions.calarmi.ui.components.ScreenScaffold
import com.mathyusolutions.calarmi.ui.components.SectionCard
import com.mathyusolutions.calarmi.ui.components.SettingRow
import com.mathyusolutions.calarmi.ui.components.SymbolIcons
import com.mathyusolutions.calarmi.ui.theme.DS
import com.mathyusolutions.calarmi.ui.theme.parseHex
import kotlinx.coroutines.launch

/** Ajustes › Categorías: las de fábrica y las propias, con su color e ícono. */
@Composable
fun CategoryManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = CalarmiApp.container(context)
    val customs by container.categories.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<CustomCategory?>(null) }

    ScreenScaffold(title = stringResource(R.string.settings_categories), onBack = onBack) {
        SectionCard(title = stringResource(R.string.categories_builtin)) {
            ReminderCategory.displayOrder.forEachIndexed { i, category ->
                SettingRow(
                    title = stringResource(category.titleRes),
                    icon = SymbolIcons.of(category.defaultSymbol),
                    iconTint = category.tint,
                    divider = i < ReminderCategory.displayOrder.lastIndex,
                )
            }
        }
        SectionCard(title = stringResource(R.string.categories_custom), footer = stringResource(R.string.settings_categories_footer)) {
            customs.forEach { custom ->
                val style = CategoryStyle.of(custom)
                Row(
                    Modifier.fillMaxWidth().clickable { editing = custom }.padding(horizontal = DS.lg, vertical = DS.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReminderIcon(style.iconKind, style.iconValue, style.color, size = 30.dp, shape = RoundedCornerShape(7.dp))
                    Text(custom.name, modifier = Modifier.weight(1f).padding(start = DS.md))
                    IconButton(onClick = { scope.launch { container.categories.delete(custom) } }) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SettingRow(
                title = stringResource(R.string.categories_new),
                icon = Icons.Filled.Add,
                divider = false,
                onClick = { editing = CustomCategory(sortOrder = customs.size) },
            )
        }
    }

    editing?.let { category ->
        CategoryEditorDialog(
            initial = category,
            onDismiss = { editing = null },
            onSave = { saved ->
                scope.launch { container.categories.save(saved) }
                editing = null
            },
        )
    }
}

private val CATEGORY_COLORS = listOf(
    "#FF3B30", "#FF9500", "#FFCC00", "#34C759", "#00C7BE", "#30B0C7",
    "#007AFF", "#5856D6", "#AF52DE", "#FF2D55", "#A2845E", "#8E8E93",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorDialog(initial: CustomCategory, onDismiss: () -> Unit, onSave: (CustomCategory) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var color by remember { mutableStateOf(initial.colorHex) }
    var kind by remember { mutableStateOf(initial.iconKind) }
    var value by remember { mutableStateOf(initial.iconValue) }
    var pickingIcon by remember { mutableStateOf(false) }
    val tint = parseHex(color) ?: Color(0xFFAF52DE)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.name.isEmpty()) R.string.categories_new else R.string.categories_edit)) },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(DS.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clickable { pickingIcon = true }) {
                        ReminderIcon(kind, value, tint, size = 52.dp, shape = RoundedCornerShape(DS.radiusMd))
                    }
                    EditorTextField(name, { name = it }, stringResource(R.string.categories_name_placeholder), modifier = Modifier.padding(start = DS.md))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(DS.sm), verticalArrangement = Arrangement.spacedBy(DS.sm)) {
                    CATEGORY_COLORS.forEach { hex ->
                        val c = parseHex(hex)!!
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(c)
                                .border(if (hex.equals(color, true)) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), CircleShape)
                                .clickable { color = hex },
                            contentAlignment = Alignment.Center,
                        ) { if (hex.equals(color, true)) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(initial.copy(name = name.trim(), colorHex = color, iconKindRaw = kind.raw, iconValue = value))
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (pickingIcon) {
        IconPickerSheet(
            tint = tint,
            suggested = emptyList(),
            defaultSymbol = "star.fill",
            iconKind = kind,
            iconValue = value,
            photoPath = null,
            allowsPhoto = false,
            onDismiss = { pickingIcon = false },
            onChange = { k, v, _ -> kind = k; value = v ?: "star.fill" },
        )
    }
}

@Suppress("unused")
private val unusedKind = ReminderIconKind.SYMBOL
