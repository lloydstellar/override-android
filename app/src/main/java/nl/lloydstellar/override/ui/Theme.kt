package nl.lloydstellar.override.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import nl.lloydstellar.override.R
import nl.lloydstellar.override.data.PHOSPHORS
import nl.lloydstellar.override.data.Phosphor

/**
 * The palette follows the phosphor the player picked, like the terminal in
 * the browser: green by default, amber, LLOYDOS cyan, white or violet once
 * unlocked. Amber, red and green keep their meaning in every theme: amber is
 * the main action and records, red is the AI, green is a success.
 */
object C {
    var phosphor by mutableStateOf(PHOSPHORS[0])

    val Main get() = Color(phosphor.main)
    val Bright get() = Color(phosphor.bright)
    val Mid get() = Color(phosphor.mid)
    val Dim get() = Color(phosphor.dim)
    val Deep get() = Color(phosphor.deep)
    val Void get() = Color(phosphor.bg)
    val Bg2 get() = Color(phosphor.bg2)
    val Amber = Color(0xFFFFB000)
    val Red = Color(0xFFFF4D6D)
    val Green = Color(0xFF4DFF9A)

    /** The amber of the theme is the main action; on the amber phosphor use a warmer red-orange. */
    val Action get() = if (phosphor.id == "amber") Color(0xFFFF7A3D) else Amber
}

@OptIn(ExperimentalTextApi::class)
private fun orbitron(weight: Int) = Font(
    R.font.orbitron, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val Orbitron = FontFamily(orbitron(400), orbitron(500), orbitron(700), orbitron(800), orbitron(900))
val Mono = FontFamily(Font(R.font.share_tech_mono))

fun display(size: TextUnit, weight: FontWeight = FontWeight.Black, color: Color = C.Bright, spacing: TextUnit = 1.sp) =
    TextStyle(fontFamily = Orbitron, fontWeight = weight, fontSize = size, color = color, letterSpacing = spacing)

fun mono(size: TextUnit, color: Color = C.Mid, spacing: TextUnit = 1.5.sp) =
    TextStyle(fontFamily = Mono, fontSize = size, color = color, letterSpacing = spacing)

@Composable
fun OverrideTheme(phosphor: Phosphor, content: @Composable () -> Unit) {
    C.phosphor = phosphor
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Main, onPrimary = C.Void, secondary = C.Amber, tertiary = C.Green,
            background = C.Void, surface = C.Bg2, onSurface = C.Bright, onBackground = C.Bright,
            error = C.Red,
        ),
        content = content
    )
}
