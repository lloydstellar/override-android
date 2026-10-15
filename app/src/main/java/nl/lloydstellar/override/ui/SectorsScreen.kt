package nl.lloydstellar.override.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lloydstellar.override.data.Store
import nl.lloydstellar.override.engine.SECTORS
import nl.lloydstellar.override.engine.Sector

/** The campaign: twelve sectors, each opened by clearing the one before. */
@Composable
fun SectorsScreen(store: Store, onBack: () -> Unit, onStart: (Sector) -> Unit, onContinue: () -> Unit) {
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    val saved = store.savedSector
    val savedNumber = saved?.optInt("sector")
    val bars = SECTORS.sumOf { store.bars[it.number - 1] }
    Box(Modifier.fillMaxSize()) {
        Tube(dim = 0.2f)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 560.dp)) {
                ScreenTitle("SECTORS", "Twelve sectors of the grid. $bars of ${SECTORS.size * 3} signal bars.", onBack)
            }
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp).padding(top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (saved != null && savedNumber != null) {
                    val s = SECTORS.first { it.number == savedNumber }
                    CommandButton("CONTINUE ${s.code} ${s.name}", Modifier.fillMaxWidth(), primary = true, height = 58.dp, onClick = onContinue)
                    Spacer(Modifier.height(4.dp))
                }
                SECTORS.forEach { s -> SectorRow(store, s) { open = s.number } }
                Spacer(Modifier.height(4.dp))
                Text("BARS: ONE FOR THE OVERRIDE, ONE FOR MAKING IT WITHIN PAR, ONE FOR NEVER BEING LOCKED BY NEXUS-9.",
                    style = mono(12.sp, C.Dim, 0.5.sp))
            }
        }
        AnimatedVisibility(open != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
            val s = SECTORS.firstOrNull { it.number == open }
            if (s != null) Briefing(store, s, onClose = { open = null }, onStart = { open = null; onStart(s) })
        }
    }
}

@Composable
private fun SectorRow(store: Store, s: Sector, onClick: () -> Unit) {
    val unlocked = store.sectorOpen(s.number)
    val bars = store.bars[s.number - 1]
    val edge = when {
        !unlocked -> C.Dim
        bars == 3 -> C.Amber
        else -> C.Main
    }
    ConsolePanel(
        Modifier.fillMaxWidth().clickable(enabled = unlocked, role = Role.Button, onClick = click(onClick)),
        edge = edge
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(pad(s.number, 2), style = display(26.sp, color = if (unlocked) C.Bright else C.Dim), modifier = Modifier.width(60.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = mono(16.sp, if (unlocked) C.Bright else C.Dim, 1.5.sp))
                Text(if (unlocked) s.twist else "LOCKED: CLEAR SECTOR ${pad(s.number - 1, 2)}", style = mono(12.sp, if (unlocked) C.Mid else C.Dim, 0.sp))
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TileIcon(s.rules.target, 34.dp, locked = !unlocked)
                Spacer(Modifier.height(4.dp))
                Bars(bars, size = 14.dp)
            }
        }
    }
}

/** The briefing before a sector: the story, the twist, the target and the par. */
@Composable
private fun Briefing(store: Store, s: Sector, onClose: () -> Unit, onStart: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(C.Void.copy(alpha = 0.9f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Scanlines()
        ConsolePanel(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 18.dp).widthIn(max = 520.dp).fillMaxWidth()
                .clickable(remember { MutableInteractionSource() }, null) { },
            edge = C.Amber
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp)) {
                Text("${s.code} // BRIEFING", style = mono(12.sp, C.Amber, 1.5.sp))
                Spacer(Modifier.height(4.dp))
                Text(s.name, style = h1(30.sp))
                Spacer(Modifier.height(8.dp))
                AmberStroke()
                Spacer(Modifier.height(12.dp))
                s.briefing.forEach {
                    Text(it, style = mono(14.sp, C.Bright, 0.sp))
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(4.dp))
                DashedRule()
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TileIcon(s.rules.target, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("TARGET", style = mono(11.sp, C.Mid, 1.5.sp))
                        Text("${s.rules.target}-BIT PULSE", style = mono(18.sp, C.Bright, 1.sp))
                    }
                }
                LeaderRow("MATRIX", "${s.rules.size} X ${s.rules.size}" + if (s.rules.deadCells.isNotEmpty()) ", ${s.rules.deadCells.size} DEAD" else "")
                if (s.rules.moveLimit > 0) LeaderRow("MOVE LIMIT", s.rules.moveLimit.toString(), C.Red)
                LeaderRow("PAR", s.par.toString())
                val best = store.sectorBest[s.number - 1]
                LeaderRow("YOUR BEST", if (best > 0) "$best MOVES" else "-", if (best > 0) C.Amber else C.Dim)
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("BARS", style = mono(14.sp, C.Mid, 1.sp), modifier = Modifier.weight(1f))
                    Bars(store.bars[s.number - 1], size = 20.dp)
                }
                Spacer(Modifier.height(10.dp))
                val savedHere = store.savedSector?.optInt("sector") == s.number
                CommandButton(if (savedHere) "START OVER" else "JACK IN", Modifier.fillMaxWidth(), primary = true, height = 58.dp, onClick = onStart)
                Spacer(Modifier.height(10.dp))
                CommandButton("BACK", Modifier.fillMaxWidth(), color = C.Main, onClick = onClose)
            }
        }
    }
}
