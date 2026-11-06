package com.example.gemini.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Native Jetpack Compose implementation of React Bits "DecryptedText"
 * (https://reactbits.dev/text-animations/decrypted-text).
 *
 * Scrambles characters through cybernetic glyphs before sequentially resolving
 * letter-by-letter with a subtle glowing color transition.
 */
@Composable
fun DecryptedText(
    text: String,
    modifier: Modifier = Modifier,
    speed: Long = 32L,
    maxIterations: Int = 8,
    sequential: Boolean = true,
    characters: String = "!@#$%^&*()_+{}[]~0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz",
    style: TextStyle = LocalTextStyle.current,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    scrambledColor: Color = Color(0xFF38BDF8).copy(alpha = 0.85f),
    revealedColor: Color = Color.Unspecified
) {
    var revealedIndices by remember(text) { mutableStateOf(setOf<Int>()) }
    var iterationCounts by remember(text) { mutableStateOf(IntArray(text.length)) }
    var displayedChars by remember(text) { mutableStateOf(text.toCharArray()) }

    LaunchedEffect(text) {
        revealedIndices = emptySet()
        val counts = IntArray(text.length)
        val chars = text.toCharArray()

        if (sequential) {
            for (i in text.indices) {
                if (text[i].isWhitespace()) {
                    revealedIndices = revealedIndices + i
                    continue
                }
                while (counts[i] < maxIterations) {
                    chars[i] = characters[Random.nextInt(characters.length)]
                    displayedChars = chars.clone()
                    counts[i]++
                    delay(speed)
                }
                chars[i] = text[i]
                displayedChars = chars.clone()
                revealedIndices = revealedIndices + i
            }
        } else {
            var allDone = false
            while (!allDone) {
                allDone = true
                for (i in text.indices) {
                    if (text[i].isWhitespace() || revealedIndices.contains(i)) continue
                    if (counts[i] < maxIterations) {
                        chars[i] = characters[Random.nextInt(characters.length)]
                        counts[i]++
                        allDone = false
                    } else {
                        chars[i] = text[i]
                        revealedIndices = revealedIndices + i
                    }
                }
                displayedChars = chars.clone()
                delay(speed)
            }
        }
    }

    val finalRevealedColor = if (revealedColor != Color.Unspecified) revealedColor else style.color

    val annotated = buildAnnotatedString {
        for (i in displayedChars.indices) {
            val isRevealed = revealedIndices.contains(i)
            withStyle(
                style = SpanStyle(
                    color = if (isRevealed) finalRevealedColor else scrambledColor
                )
            ) {
                append(displayedChars[i])
            }
        }
    }

    Text(
        text = annotated,
        style = style,
        fontSize = fontSize,
        fontWeight = fontWeight,
        overflow = overflow,
        maxLines = maxLines,
        modifier = modifier
    )
}
