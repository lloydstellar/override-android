package nl.lloydstellar.override.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lloydstellar.override.data.Store
import nl.lloydstellar.override.engine.DeepGrid
import nl.lloydstellar.override.engine.Sector

/**
 * The Deep Grid: the next sector to clear on top, the cleared ones below it,
 * deepest first, so a replay for a missing bar is one tap away.
 */
@Composable
fun DeepGridScreen(store: Store, onBack: () -> Unit, onStart: (Sector, Boolean) -> Unit, onContinue: () -> Unit) {
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    val saved = store.savedDeep
    val savedNumber = saved?.optInt("sector")
    val next = store.deepest + 1
    val cleared = (next - 1 downTo DeepGrid.FIRST).toList()
    Box(Modifier.fillMaxSize()) {
        Tube(dim = 0.2f)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 560.dp)) {
                ScreenTitle(
                    "DEEP GRID",
                    if (cleared.isEmpty()) "Sectors without end, below the core. Every one stacks the twists of the directives."
                    else "Sectors without end, below the core. Deepest cleared: ${DeepGrid.FIRST + cleared.size - 1}.",
                    onBack
                )
            }
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp).padding(top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (saved != null && savedNumber != null && savedNumber >= DeepGrid.FIRST) {
                    CommandButton("CONTINUE S$savedNumber ${DeepGrid.name(savedNumber)}", Modifier.fillMaxWidth(), primary = true, height = 58.dp, onClick = onContinue)
                    Spacer(Modifier.height(4.dp))
                }
                Text("NEXT", style = mono(12.sp, C.Amber, 1.5.sp))
                DeepRow(DeepGrid.sector(next), 0, next = true) { open = next }
                if (cleared.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("CLEARED", style = mono(12.sp, C.Main, 1.5.sp))
                    cleared.forEach { n -> DeepRow(DeepGrid.sector(n), store.deepBars(n), next = false) { open = n } }
                }
                Spacer(Modifier.height(4.dp))
                Text("THE DEEPER, THE HARDER: THREE TWISTS FROM SECTOR 18, THE 2048 FROM SECTOR 28. BARS WORK AS IN THE CAMPAIGN.",
                    style = mono(12.sp, C.Dim, 0.5.sp))
            }
        }
        AnimatedVisibility(open != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
            val n = open
            if (n != null) {
                val s = DeepGrid.sector(n)
                Briefing(
                    s, bars = store.deepBars(n), best = store.deepBest(n),
                    savedHere = savedNumber == n,
                    assistOffered = (store.deepFails[n] ?: 0) >= 3,
                    onClose = { open = null }, onStart = { assist -> open = null; onStart(s, assist) },
                    kicker = "${s.code} // DEEP GRID // DEPTH ${DeepGrid.depth(n)}",
                )
            }
        }
    }
}

@Composable
private fun DeepRow(s: Sector, bars: Int, next: Boolean, onClick: () -> Unit) {
    ConsolePanel(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = click(onClick)),
        edge = when {
            next -> C.Amber
            bars == 3 -> C.Amber
            else -> C.Main
        }
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.number.toString(), style = display(26.sp, color = C.Bright), modifier = Modifier.width(60.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = mono(16.sp, C.Bright, 1.5.sp))
                Text(s.short, style = mono(12.sp, C.Mid, 0.sp))
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TileIcon(s.rules.target, 34.dp)
                Spacer(Modifier.height(4.dp))
                Bars(bars, size = 14.dp)
            }
        }
    }
}
