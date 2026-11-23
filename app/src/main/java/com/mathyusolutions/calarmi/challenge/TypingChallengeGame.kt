package com.mathyusolutions.calarmi.challenge

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.model.WakeChallenge
import com.mathyusolutions.calarmi.ui.theme.DS
import kotlinx.coroutines.delay

/** Frases cortas y con ánimo, sin apóstrofes ni signos que el teclado cambie por su cuenta. */
private val phrasePool = listOf(
    R.string.challenges_phrase_1, R.string.challenges_phrase_2, R.string.challenges_phrase_3,
    R.string.challenges_phrase_4, R.string.challenges_phrase_5, R.string.challenges_phrase_6,
    R.string.challenges_phrase_7, R.string.challenges_phrase_8, R.string.challenges_phrase_9,
    R.string.challenges_phrase_10, R.string.challenges_phrase_11, R.string.challenges_phrase_12,
)

/**
 * «Escribir»: copiar una frase corta tal cual, letra por letra, con tildes y
 * mayúsculas. Sin autocorrección ni mayúsculas automáticas, así que no hay
 * atajo: hay que leer y teclear. Una, dos o tres frases según la dificultad.
 * La frase se pinta carácter a carácter: verde lo acertado, rojo desde el
 * primer error, gris lo que falta. Se acepta sola en cuanto coincide.
 */
@Composable
fun TypingChallengeGame(difficulty: WakeChallenge.Difficulty, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberChallengeHaptics()
    val total = TypingRules.total(difficulty)
    val phraseIds = remember(difficulty) { phrasePool.shuffled().take(total) }
    var index by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    // Sube con cada «Listo» en falso para sacudir el campo.
    var shakeCount by remember { mutableIntStateOf(0) }
    var solved by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val phrase = TypingRules.normalize(stringResource(phraseIds[index]))
    val mismatch = TypingRules.mismatchIndex(phrase, input)

    LaunchedEffect(Unit) {
        // En una pantalla recién abierta, pedir el foco al instante a veces no
        // saca el teclado: se insiste un momento después.
        delay(400)
        if (!solved) {
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }

    fun accept() {
        input = ""
        if (index + 1 >= total) {
            solved = true
            keyboard?.hide()
            onSolved()
        } else {
            haptics.light()
            index += 1
        }
    }

    fun onInput(value: String) {
        if (solved) return
        input = value
        if (TypingRules.matches(phrase, value)) accept()
    }

    fun submit() {
        if (solved) return
        if (TypingRules.matches(phrase, input)) {
            accept()
        } else {
            haptics.error()
            shakeCount += 1
        }
    }

    // Con el teclado fuera el sitio escasea en los teléfonos pequeños: si aun
    // así no cabe, se desplaza en vez de cortarse.
    Column(
        modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DS.md),
    ) {
        GameCaption(stringResource(R.string.challenges_typing_phrase_of, index + 1, total))

        val green = successGreen
        val red = errorRed
        val pending = MaterialTheme.colorScheme.onSurfaceVariant
        val typedCount = TypingRules.typedLength(input)
        AnimatedContent(
            targetState = index,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.95f)).togetherWith(fadeOut()) },
            modifier = Modifier
                .widthIn(max = GameMaxWidth)
                .fillMaxWidth()
                .clip(RoundedCornerShape(DS.radiusMd))
                .background(tileColor)
                .semantics { contentDescription = phrase },
            label = "phrase",
        ) { shownIndex ->
            val text = if (shownIndex == index) {
                // La frase con cada carácter coloreado según lo escrito hasta ahora.
                val firstError = mismatch ?: Int.MAX_VALUE
                buildAnnotatedString {
                    phrase.forEachIndexed { i, character ->
                        val color = when {
                            i >= typedCount -> pending
                            i < firstError -> green
                            else -> red
                        }
                        withStyle(SpanStyle(color = color)) { append(character) }
                    }
                }
            } else {
                // La frase que se va, ya completada.
                val done = TypingRules.normalize(stringResource(phraseIds[shownIndex]))
                buildAnnotatedString { withStyle(SpanStyle(color = green)) { append(done) } }
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = DS.md, vertical = DS.sm), contentAlignment = Alignment.Center) {
                Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }

        val accent = MaterialTheme.colorScheme.primary
        val borderColor by animateColorAsState(
            when {
                mismatch != null -> red
                focused -> accent.copy(alpha = 0.6f)
                else -> Color.Transparent
            },
            label = "border",
        )
        val shape = RoundedCornerShape(DS.radiusMd)
        BasicTextField(
            value = input,
            onValueChange = ::onInput,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(accent),
            // Como `autocorrectionDisabled` y `.never` de iOS: sin correcciones
            // ni mayúsculas automáticas que escriban por la persona.
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .widthIn(max = GameMaxWidth)
                .fillMaxWidth()
                .shakeOnChange(shakeCount)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { field ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(shape)
                        .background(tileColor)
                        .border(2.dp, borderColor, shape)
                        .padding(horizontal = DS.md),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (input.isEmpty()) {
                        Text(
                            stringResource(R.string.challenges_typing_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    field()
                }
            },
        )

        // Con el teclado fuera la nota solo se queda si hay un error que señalar.
        if (mismatch != null || !focused) {
            Text(
                stringResource(if (mismatch == null) R.string.challenges_typing_hint else R.string.challenges_typing_error),
                style = MaterialTheme.typography.bodySmall,
                color = if (mismatch == null) MaterialTheme.colorScheme.onSurfaceVariant else red,
                textAlign = TextAlign.Center,
                modifier = Modifier.heightIn(min = 18.dp),
            )
        }
    }
}
