package com.example.gemini.ui.components

import androidx.compose.animation.core.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit

/**
 * Native Jetpack Compose implementation of React Bits "ShinyText"
 * (https://reactbits.dev/text-animations/shiny-text).
 *
 * Casts a glistening metallic light beam across text on an infinite loop.
 */
@Composable
fun ShinyText(
    text: String,
    modifier: Modifier = Modifier,
    baseColor: Color = Color(0xFFA1A1AA),
    shinyColor: Color = Color.White,
    durationMillis: Int = 2600,
    style: TextStyle = LocalTextStyle.current,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null
) {
    val transition = rememberInfiniteTransition(label = "shiny_transition")
    val progress by transition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shiny_progress"
    )

    Text(
        text = text,
        style = style.copy(color = baseColor),
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        modifier = modifier
            .graphicsLayer(alpha = 0.99f)
            .drawWithContent {
                drawContent()
                val width = size.width
                val height = size.height
                val startX = (progress - 0.35f) * width
                val endX = (progress + 0.35f) * width
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            shinyColor.copy(alpha = 0.90f),
                            Color.Transparent
                        ),
                        start = Offset(startX, 0f),
                        end = Offset(endX, height)
                    ),
                    blendMode = BlendMode.SrcIn
                )
            }
    )
}
