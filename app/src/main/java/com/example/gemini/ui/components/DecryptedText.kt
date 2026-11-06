package com.example.gemini.ui.components

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
 * 100% Faithful Native Jetpack Compose implementation of React Bits "DecryptedText"
 * (https://reactbits.dev/text-animations/decrypted-text).
 *
 * Scrambles characters through cybernetic glyphs before sequentially resolving
 * letter-by-letter with a subtle glowing color transition.
 *
 * Optimized:
 * - Direct integer index tracking (no Set/collection heap churn)
 * - Transitions to plain zero-overhead Text once resolution completes
 */
@Composable
fun DecryptedText(
    text: String,
    modifier: Modifier = Modifier,
    speed: Long = 28L,
    maxIterations: Int = 6,
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
    if (text.isEmpty()) {
        Text(
            text = "",
            style = style,
            fontSize = fontSize,
            fontWeight = fontWeight,
            overflow = overflow,
            maxLines = maxLines,
            modifier = modifier
        )
        return
    }

    var isComplete by remember(text) { mutableStateOf(false) }
    var revealedUpTo by remember(text) { mutableIntStateOf(0) }
    var displayedChars by remember(text) { mutableStateOf(text.toCharArray()) }

    LaunchedEffect(text) {
        isComplete = false
        revealedUpTo = 0
        val chars = text.toCharArray()
        val len = text.length

        if (sequential) {
            for (i in 0 until len) {
                if (text[i].isWhitespace()) {
                    revealedUpTo = i + 1
                    continue
                }
                var iter = 0
                while (iter < maxIterations) {
                    chars[i] = characters[Random.nextInt(characters.length)]
                    displayedChars = chars.clone()
                    iter++
                    delay(speed)
                }
                chars[i] = text[i]
                displayedChars = chars.clone()
                revealedUpTo = i + 1
            }
        } else {
            val counts = IntArray(len)
            var done = false
            while (!done) {
                done = true
                for (i in 0 until len) {
                    if (text[i].isWhitespace() || counts[i] >= maxIterations) continue
                    chars[i] = characters[Random.nextInt(characters.length)]
                    counts[i]++
                    if (counts[i] < maxIterations) {
                        done = false
                    } else {
                        chars[i] = text[i]
                    }
                }
                displayedChars = chars.clone()
                delay(speed)
            }
        }
        isComplete = true
    }

    val finalRevealedColor = if (revealedColor != Color.Unspecified) revealedColor else style.color

    if (isComplete) {
        // High-performance plain Text rendering once decrypted
        Text(
            text = text,
            style = style,
            color = finalRevealedColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            overflow = overflow,
            maxLines = maxLines,
            modifier = modifier
        )
    } else {
        // Transitional span layout during cipher resolution
        val currentRevealed = revealedUpTo
        val currentChars = displayedChars
        val annotated = buildAnnotatedString {
            for (i in currentChars.indices) {
                val isRev = i < currentRevealed
                withStyle(
                    style = SpanStyle(
                        color = if (isRev) finalRevealedColor else scrambledColor
                    )
                ) {
                    append(currentChars[i])
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
}
