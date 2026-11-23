package com.mathyusolutions.calarmi.challenge

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.model.WakeChallenge
import com.mathyusolutions.calarmi.ui.theme.DS

/**
 * Tres operaciones seguidas, con teclado numérico propio: nada de teclado del
 * sistema que tape el problema.
 */
@Composable
fun MathChallengeGame(difficulty: WakeChallenge.Difficulty, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberChallengeHaptics()
    val problems = remember(difficulty) { List(MathProblem.TOTAL) { MathProblem.random(difficulty) } }
    var index by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    fun check() {
        val answer = input.toIntOrNull() ?: return
        input = ""
        if (answer == problems[index].answer) {
            wrong = false
            if (index + 1 >= MathProblem.TOTAL) {
                onSolved()
            } else {
                haptics.light()
                index += 1
            }
        } else {
            wrong = true
            haptics.error()
        }
    }

    fun tap(key: MathKey) {
        when (key) {
            is MathKey.Digit -> {
                if (input.length >= MathProblem.MAX_DIGITS) return
                wrong = false
                input += key.value.toString()
                haptics.selection()
            }
            MathKey.Delete -> {
                if (input.isEmpty()) return
                input = input.dropLast(1)
                haptics.selection()
            }
            MathKey.Check -> check()
        }
    }

    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DS.lg),
    ) {
        GameCaption(stringResource(R.string.challenges_math_problem_of, index + 1, MathProblem.TOTAL))

        val numberStyle = TextStyle(
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            fontFeatureSettings = "tnum",
        )
        Column(
            Modifier.widthIn(max = GameMaxWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DS.sm),
        ) {
            BasicText(
                problems[index].text,
                modifier = Modifier.fillMaxWidth(),
                style = numberStyle,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 40.sp),
            )
            val border by animateColorAsState(if (wrong) errorRed else Color.Transparent, label = "border")
            val shape = RoundedCornerShape(DS.radiusMd)
            Surface(
                shape = shape,
                color = tileColor,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).border(2.dp, border, shape),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        input.ifEmpty { "?" },
                        style = numberStyle.copy(fontWeight = FontWeight.SemiBold),
                        color = if (input.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        val wrongAlpha by animateFloatAsState(if (wrong) 1f else 0f, label = "wrong")
        Text(
            stringResource(R.string.challenges_math_wrong),
            style = MaterialTheme.typography.bodySmall,
            color = errorRed,
            modifier = Modifier.alpha(wrongAlpha),
        )

        Keypad(canCheck = input.isNotEmpty(), onKey = ::tap)
    }
}

private sealed interface MathKey {
    data class Digit(val value: Int) : MathKey
    data object Delete : MathKey
    data object Check : MathKey
}

private val keyRows: List<List<MathKey>> = listOf(
    listOf(MathKey.Digit(1), MathKey.Digit(2), MathKey.Digit(3)),
    listOf(MathKey.Digit(4), MathKey.Digit(5), MathKey.Digit(6)),
    listOf(MathKey.Digit(7), MathKey.Digit(8), MathKey.Digit(9)),
    listOf(MathKey.Delete, MathKey.Digit(0), MathKey.Check),
)

/**
 * El teclado ocupa lo que quede de alto, hasta 56 dp por tecla: en un
 * teléfono bajito las teclas se achican antes que esconder el problema.
 */
@Composable
private fun ColumnScope.Keypad(canCheck: Boolean, onKey: (MathKey) -> Unit) {
    Column(
        Modifier
            .weight(1f, fill = false)
            .widthIn(max = GameMaxWidth)
            .fillMaxWidth()
            .heightIn(max = 56.dp * 4 + DS.sm * 3),
        verticalArrangement = Arrangement.spacedBy(DS.sm),
    ) {
        keyRows.forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DS.sm)) {
                row.forEach { key ->
                    KeyButton(key, enabled = key != MathKey.Check || canCheck, onClick = { onKey(key) }, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun KeyButton(key: MathKey, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val green = successGreen
    val isCheck = key == MathKey.Check
    val label = when (key) {
        MathKey.Delete -> stringResource(R.string.action_delete)
        MathKey.Check -> stringResource(R.string.challenges_key_check)
        is MathKey.Digit -> key.value.toString()
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(DS.radiusMd),
        color = if (isCheck) green.copy(alpha = 0.18f) else tileColor,
        contentColor = if (isCheck) green else MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (key) {
                is MathKey.Digit -> Text(key.value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                MathKey.Delete -> Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null)
                MathKey.Check -> Icon(Icons.Filled.Check, contentDescription = null)
            }
        }
    }
}
