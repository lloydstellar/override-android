package nl.lloydstellar.override.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.lloydstellar.override.data.Store
import nl.lloydstellar.override.data.signal
import nl.lloydstellar.override.engine.Directives
import nl.lloydstellar.override.engine.SECTORS
import nl.lloydstellar.override.engine.threat
import java.time.LocalDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The title screen as a LLOYDOS screen: the status line and terminal prompt
 * of lloydstellar.nl, the site's h1 with its amber stroke, and a console
 * panel with the signal ladder from a 2 Hz Sub-Pulse to the Override Code.
 */
@Composable
fun HomeScreen(
    store: Store,
    onContinue: () -> Unit,
    /** A new Classic session at a threat level. */
    onStart: (Int) -> Unit,
    onSectors: () -> Unit,
    onDirectives: () -> Unit,
    onRecords: () -> Unit,
    onArchive: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onWebsite: () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(if (shown) 1f else 0f, tween(450), label = "appear")
    val saved = store.savedClassic
    var choosing by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = choosing) { choosing = false }

    Box(Modifier.fillMaxSize()) {
        Tube(dim = 0f)
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
            val tall = maxHeight > 700.dp
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 22.dp).alpha(appear)
                    .then(if (tall) Modifier else Modifier.verticalScroll(rememberScrollState()))
            ) {
                StatusLine()
                Spacer(Modifier.height(if (tall) 24.dp else 14.dp))

                val title = display(46.sp, FontWeight.ExtraBold, C.Bright, 1.sp).copy(
                    brush = Brush.verticalGradient(listOf(Color.White, C.Bright, C.Main)),
                    shadow = Shadow(C.Main.copy(alpha = 0.4f), blurRadius = 16f),
                    lineHeight = 50.sp
                )
                /* the small kicker of Lloyd's Spin Doctor; not part of the official name */
                Text("LLOYD'S", style = mono(14.sp, C.Mid, 4.sp))
                Text("2048\nOVERRIDE", style = title)
                Spacer(Modifier.height(10.dp))
                AmberStroke(120.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "It is 2048 and the AI NEXUS-9 owns the grid. Fuse signals into a 2048-bit pulse and take its core down.",
                    style = mono(15.sp, C.Mid, 0.sp)
                )

                if (tall) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(18.dp))
                LadderPanel(store)
                if (tall) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(18.dp))

                if (saved != null) {
                    val score = saved.optJSONObject("engine")?.optInt("score") ?: 0
                    CommandButton("CONTINUE", Modifier.fillMaxWidth(), primary = true, height = 60.dp,
                        trailing = "${threat(saved.optInt("level")).short}  ${pad(score, 6)} Hz", onClick = onContinue)
                } else {
                    CommandButton("JACK IN", Modifier.fillMaxWidth(), primary = true, height = 60.dp,
                        trailing = threat(store.lastLevel).short, onClick = { onStart(store.lastLevel) })
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CommandButton("NEW SESSION", Modifier.weight(1f), onClick = { choosing = true })
                    SectorsButton(store, Modifier.weight(1f), onSectors)
                }
                Spacer(Modifier.height(10.dp))
                DirectivesButton(store, onDirectives)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CommandButton("RECORDS", Modifier.weight(1f), color = C.Main, height = 46.dp, onClick = onRecords)
                    CommandButton("ARCHIVE", Modifier.weight(1f), color = C.Main, height = 46.dp, onClick = onArchive)
                    CommandButton("SETTINGS", Modifier.weight(1f), color = C.Main, height = 46.dp, onClick = onSettings)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Prompt(onWebsite, Modifier.weight(1f))
                    CommandButton("ABOUT", color = C.Main, height = 44.dp, onClick = onAbout)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (choosing) ThreatSheet(store, onStart = { choosing = false; onStart(it) }, onClose = { choosing = false })
    }
}

/** Today's daily: NEW until it is played, then its bars. */
@Composable
private fun DirectivesButton(store: Store, onClick: () -> Unit) {
    val id = Directives.dailyId(LocalDate.now())
    val bars = store.directiveBars(id)
    val played = store.directiveBest(id) > 0 || bars > 0
    CommandButton(
        "DAILY DIRECTIVE", Modifier.fillMaxWidth(),
        trailing = if (bars > 0) "■".repeat(bars) + "□".repeat(3 - bars) else if (played) "RETRY" else "NEW",
        onClick = onClick
    )
}

@Composable
private fun SectorsButton(store: Store, modifier: Modifier, onClick: () -> Unit) {
    val cleared = SECTORS.count { store.bars[it.number - 1] > 0 }
    /* past the campaign the button shows the deepest sector of the Deep Grid */
    val deepest = store.deepest
    CommandButton("SECTORS", modifier, trailing = if (deepest > SECTORS.size) "S$deepest" else "$cleared/${SECTORS.size}", onClick = onClick)
}

/** The HUD line of the site: system name on the left, a live clock on the right. */
@Composable
fun StatusLine(label: String = "LLOYDOS v2.0 // RESISTANCE TERMINAL") {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) { now = Date(); delay(1000) }
    }
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.ENGLISH) }
    Column {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = mono(12.sp, C.Dim, 1.sp), modifier = Modifier.weight(1f), maxLines = 1)
            Text(fmt.format(now), style = mono(12.sp, C.Mid, 1.sp))
        }
        Spacer(Modifier.height(8.dp))
        val line = C.Main.copy(alpha = 0.3f)
        Canvas(Modifier.fillMaxWidth().height(1.dp)) { drawRect(line) }
    }
}

/** The signal ladder: every tile from 2 to 2048, lit up as far as you have ever come. */
@Composable
private fun LadderPanel(store: Store) {
    val values = listOf(2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048)
    val best = store.bestTile
    ConsolePanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("* SIGNAL LADDER *", style = mono(12.sp, C.Main, 1.sp), modifier = Modifier.weight(1f))
                Text(if (best > 0) "MAX ${signal(best).name}" else "NO SIGNAL YET", style = mono(11.sp, C.Dim, 1.sp), maxLines = 1)
            }
            Spacer(Modifier.height(12.dp))
            val rows = listOf(values.subList(0, 6), values.subList(6, 11))
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { v ->
                        val known = v <= maxOf(best, 4)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(50.dp)) {
                            TileIcon(v, 44.dp, locked = !known)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(4.dp))
            BestLine(store.best)
        }
    }
}

/** "BEST ........ 012345 Hz", the dotted leader of the site's hall of fame. */
@Composable
private fun BestLine(best: Int) {
    val dots = C.Main.copy(alpha = 0.3f)
    Row(verticalAlignment = Alignment.Bottom) {
        Text("BEST FREQUENCY", style = mono(13.sp, C.Mid, 1.5.sp))
        Canvas(Modifier.weight(1f).height(10.dp).padding(horizontal = 8.dp)) {
            drawLine(dots, Offset(0f, size.height - 2f), Offset(size.width, size.height - 2f),
                strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 5f)))
        }
        Text(if (best > 0) "${pad(best, 6)} Hz" else "...... Hz", style = mono(13.sp, if (best > 0) C.Amber else C.Dim, 1.5.sp))
    }
}

/** The terminal prompt of lloydstellar.nl, with the blinking block cursor. */
@Composable
fun Prompt(onWebsite: () -> Unit, modifier: Modifier = Modifier) {
    val on = rememberBlink(540)
    val dim = C.Dim
    val main = C.Main
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = dim)) { append("guest@lloydstellar:~$ ") }
            withStyle(SpanStyle(color = main, textDecoration = TextDecoration.Underline)) { append("lloydstellar.nl") }
            withStyle(SpanStyle(color = if (on) main else Color.Transparent)) { append(" ▎") }
        },
        style = mono(13.sp, main, 0.5.sp),
        maxLines = 1,
        modifier = modifier.clickable(onClick = click(onWebsite)).padding(vertical = 8.dp)
    )
}
