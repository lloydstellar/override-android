package nl.lloydstellar.override.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.lloydstellar.override.App
import nl.lloydstellar.override.audio.Haptics
import nl.lloydstellar.override.audio.Sfx

/*
 * The building blocks of every screen, taken from the LLOYDOS look of
 * lloydstellar.nl and its other two apps: angular command buttons, console
 * panels with corner brackets, mono text, dotted leaders, one phosphor
 * colour for everything and amber only for the main action and records.
 */

/* ---------------- tiles in Compose ---------------- */

/** A tile from the matrix, drawn with the terminal's own sprites. */
@Composable
fun TileIcon(value: Int, size: Dp, modifier: Modifier = Modifier, corrupt: Boolean = false, locked: Boolean = false) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val img = remember(value, px, corrupt) {
        (if (corrupt) App.sprites.corrupt(px) else App.sprites.tile(value, px)).asImageBitmap()
    }
    Image(
        img, null, modifier.size(size),
        colorFilter = if (locked) Locked else null,
        alpha = if (locked) 0.32f else 1f
    )
}

val Locked = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/* ---------------- text ---------------- */

/** The site's h1 in the phosphor colour, with a soft glow. */
fun h1(size: TextUnit): TextStyle = display(size, FontWeight.ExtraBold, C.Bright, 1.sp).copy(
    brush = Brush.verticalGradient(listOf(Color.White, C.Bright, C.Main)),
    shadow = Shadow(C.Main.copy(alpha = 0.35f), blurRadius = 12f),
)

/** The amber stroke under every h1 on lloydstellar.nl. */
@Composable
fun AmberStroke(width: Dp = 110.dp) {
    Canvas(Modifier.width(width).height(2.dp)) {
        drawRect(Brush.horizontalGradient(listOf(C.Amber, Color.Transparent)))
    }
}

fun pad(n: Int, len: Int) = n.toString().padStart(len, '0')

/** A value that blinks on and off in hard steps, like the site's cursor. */
@Composable
fun rememberBlink(periodMs: Long = 550): Boolean {
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(periodMs) { while (true) { delay(periodMs); on = !on } }
    return on
}

/* ---------------- shapes and panels ---------------- */

/** Corners cut top-left and bottom-right, like clip-path on the site's buttons and panels. */
class CutTLBR(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }
        val p = Path().apply {
            moveTo(c, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height - c)
            lineTo(size.width - c, size.height); lineTo(0f, size.height); lineTo(0f, c); close()
        }
        return Outline.Generic(p)
    }
}

/**
 * A console panel: dark fill, thin phosphor edge, corners cut top-left and
 * bottom-right, brackets on the other two corners and faint scanlines.
 */
@Composable
fun ConsolePanel(modifier: Modifier = Modifier, edge: Color = C.Main, content: @Composable () -> Unit) {
    val shape = CutTLBR(14.dp)
    Box(
        modifier
            .clip(shape)
            .background(C.Void.copy(alpha = 0.88f))
            .border(1.dp, edge.copy(alpha = 0.35f), shape)
            .drawBehind {
                val sl = Color.Black.copy(alpha = 0.18f)
                var y = 2f
                while (y < size.height) { drawRect(sl, Offset(0f, y), Size(size.width, 1f)); y += 3f }
                val l = 26.dp.toPx(); val w = 2.dp.toPx()
                drawRect(edge, Offset(size.width - l, 0f), Size(l, w))
                drawRect(edge, Offset(size.width - w, 0f), Size(w, l))
                drawRect(edge, Offset(0f, size.height - w), Size(l, w))
                drawRect(edge, Offset(0f, size.height - l), Size(w, l))
            }
    ) { content() }
}

@Composable
fun DashedRule(modifier: Modifier = Modifier, color: Color = C.Deep) {
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(color, Offset(0f, 0.5f), Offset(size.width, 0.5f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
    }
}

/** "LABEL ........ VALUE", the dotted leader of the hall of fame. */
@Composable
fun LeaderRow(label: String, value: String, valueColor: Color = C.Bright, textSize: TextUnit = 14.sp, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Bottom) {
        Text(label, style = mono(textSize, C.Mid, 1.sp), maxLines = 1)
        val dots = C.Main.copy(alpha = 0.25f)
        Canvas(Modifier.weight(1f).height(10.dp).padding(horizontal = 8.dp)) {
            drawLine(dots, Offset(0f, size.height - 2f), Offset(size.width, size.height - 2f),
                strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 5f)))
        }
        Text(value, style = mono(textSize, valueColor, 1.sp), maxLines = 1)
    }
}

/* ---------------- buttons ---------------- */

fun click(action: () -> Unit): () -> Unit = {
    Sfx.ui()
    Haptics.ui()
    action()
}

/**
 * The site's command button: "▸ LABEL", mono, uppercase, angular. Outlined in
 * its colour; filled (dark text) when [primary] or while pressed.
 */
@Composable
fun CommandButton(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = C.Action,
    primary: Boolean = false,
    height: Dp = 54.dp,
    trailing: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val compact = height < 50.dp
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val ink0 = if (enabled) color else C.Dim
    val filled = enabled && (primary || pressed)
    val shape = CutTLBR(9.dp)
    Row(
        modifier
            .height(height)
            .clip(shape)
            .background(if (filled) ink0 else ink0.copy(alpha = 0.08f))
            .border(1.dp, ink0, shape)
            .clickable(src, null, enabled = enabled, role = Role.Button, onClick = click(onClick))
            .padding(horizontal = if (compact) 6.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        val ink = if (filled) C.Void else ink0
        val style = when {
            height >= 56.dp -> mono(18.sp, ink, 2.sp)
            compact -> mono(13.sp, ink, 0.5.sp)
            else -> mono(15.sp, ink, 2.sp)
        }
        Text(if (compact) label else "▸ $label", style = style, maxLines = 1)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(trailing, style = style.copy(color = ink.copy(alpha = 0.75f)))
        }
    }
}

/** A small square command button with an icon (pause, back). */
@Composable
fun IconCommand(icon: ImageVector, description: String, modifier: Modifier = Modifier, size: Dp = 44.dp, color: Color = C.Main, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val shape = CutTLBR(7.dp)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(if (pressed) color else C.Void.copy(alpha = 0.7f))
            .border(1.dp, color.copy(alpha = 0.7f), shape)
            .clickable(src, null, role = Role.Button, onClickLabel = description, onClick = click(onClick)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = if (pressed) C.Void else color, modifier = Modifier.size(size * 0.5f))
    }
}

/** Screen title: back button, h1 with its amber stroke, and a mono line under it. */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCommand(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Text(title, style = h1(28.sp))
        }
        Spacer(Modifier.height(10.dp))
        AmberStroke()
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = mono(13.sp, C.Mid, 0.5.sp))
        }
    }
}

/** A setting as a terminal line: "SOUND ...... [ON ]", the whole row toggles. */
@Composable
fun TermToggle(label: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch, onClick = click { onChange(!checked) })
            .padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = mono(15.sp, C.Bright, 1.sp), modifier = Modifier.weight(1f))
            Text(if (checked) "[ON ]" else "[OFF]", style = mono(15.sp, if (checked) C.Amber else C.Dim, 1.sp))
        }
        Text(sub, style = mono(12.sp, C.Mid, 0.sp))
    }
}

/** Tabs as terminal labels; the active one in amber with the amber stroke. */
@Composable
fun TermTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            Column(
                Modifier.clickable(role = Role.Tab, onClick = click { onSelect(i) }).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(if (on) "▸ $l" else "  $l", style = mono(15.sp, if (on) C.Amber else C.Mid, 1.5.sp))
                Spacer(Modifier.height(5.dp))
                if (on) AmberStroke(56.dp) else Spacer(Modifier.height(2.dp))
            }
        }
    }
}

/** Empty list message, mono, with a blinking cursor. */
@Composable
fun Empty(text: String) {
    val on = rememberBlink()
    Box(Modifier.fillMaxWidth().padding(top = 60.dp, start = 22.dp, end = 22.dp)) {
        Text(text + if (on) " ▎" else "  ", style = mono(15.sp, C.Mid, 0.5.sp), textAlign = TextAlign.Start)
    }
}

/** Signal bars of a sector: three small ascending bars, lit up to [n]. */
@Composable
fun Bars(n: Int, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val on = C.Amber
    val off = C.Dim.copy(alpha = 0.5f)
    Canvas(modifier.size(size * 1.2f, size)) {
        val w = this.size.width / 3.6f
        for (i in 0 until 3) {
            val h = this.size.height * (0.45f + 0.275f * i)
            drawRect(if (i < n) on else off, Offset(i * w * 1.3f, this.size.height - h), Size(w, h))
        }
    }
}

/** The full-screen layer behind every menu: the terminal's tube, dot grid and scanlines. */
@Composable
fun Tube(modifier: Modifier = Modifier, dim: Float = 0f) {
    val bg = C.Void
    val bg2 = C.Bg2
    val dot = C.Main.copy(alpha = 0.035f)
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.radialGradient(listOf(bg2, bg), center = Offset(size.width / 2, size.height * 0.42f), radius = size.maxDimension * 0.75f))
        val step = 6.dp.toPx() * 2.4f
        var y = 4f
        while (y < size.height) { var x = 4f; while (x < size.width) { drawRect(dot, Offset(x, y), Size(2f, 2f)); x += step }; y += step }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)), center = center, radius = size.maxDimension * 0.72f))
        if (dim > 0f) drawRect(bg.copy(alpha = dim))
    }
    Scanlines()
}

/** The scanlines of lloydstellar.nl: one dark line every 3 px. */
@Composable
fun Scanlines(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val path = Path()
        var y = 2f
        while (y < size.height) {
            path.addRect(androidx.compose.ui.geometry.Rect(0f, y, size.width, y + 1f))
            y += 3f
        }
        drawPath(path, Color.Black.copy(alpha = 0.14f))
    }
}
