package nl.lloydstellar.override.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.lloydstellar.override.audio.Sfx
import nl.lloydstellar.override.data.FILES

/**
 * The intro on first launch (and from the Archive): the terminal boots and
 * types out FILE 01, the briefing of the browser game. A tap finishes the
 * typing at once.
 */
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val boot = listOf(
        "LLOYDOS v2.0  RESISTANCE BUILD 20.48",
        "MEMORY CHECK ......... 640K OK",
        "ANALOGUE BUS ......... ONLINE",
        "NEXUS-9 CARRIER ...... DETECTED",
        "DECRYPTING FILE 01 ...",
    )
    val file = FILES.first()
    val lines = boot + listOf("", file.title) + file.lines
    val total = lines.sumOf { it.length + 8 }
    var typed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (typed < total) {
            delay(14)
            typed += 2
            if (typed % 40 == 0) Sfx.spawn()
        }
    }
    val done = typed >= total
    val cursor = rememberBlink(500)

    Box(
        Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { typed = total }
    ) {
        Tube()
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier.widthIn(max = 560.dp).weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)
            ) {
                StatusLine("LLOYDOS v2.0 // BOOT")
                Spacer(Modifier.height(18.dp))
                var left = typed
                lines.forEachIndexed { i, line ->
                    if (left <= 0) return@forEachIndexed
                    val n = minOf(line.length, left)
                    left -= line.length + 8
                    val shown = line.substring(0, n) + if (left <= 0 && !done && cursor) "▎" else ""
                    val style = when {
                        i < boot.size -> mono(13.sp, C.Mid, 1.sp)
                        line == file.title -> mono(16.sp, C.Amber, 1.5.sp)
                        else -> mono(15.sp, C.Bright, 0.sp)
                    }
                    Text(shown, style = style, modifier = Modifier.padding(bottom = if (i < boot.size) 4.dp else 12.dp))
                }
            }
            AnimatedVisibility(done, enter = fadeIn(tween(300))) {
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(22.dp)) {
                    CommandButton("JACK IN", Modifier.fillMaxWidth(), primary = true, height = 60.dp, onClick = onDone)
                }
            }
        }
    }
}
