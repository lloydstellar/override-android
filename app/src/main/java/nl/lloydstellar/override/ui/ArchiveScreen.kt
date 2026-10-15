package nl.lloydstellar.override.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lloydstellar.override.audio.Sfx
import nl.lloydstellar.override.data.DISPATCHES
import nl.lloydstellar.override.data.FILES
import nl.lloydstellar.override.data.PHOSPHORS
import nl.lloydstellar.override.data.SIGNALS
import nl.lloydstellar.override.data.Store
import nl.lloydstellar.override.data.TRANSMISSION_URL
import nl.lloydstellar.override.data.signal

/**
 * The Archive of the browser game: phosphor colours and decrypted files,
 * both unlocked by the highest signal you ever built, plus the Signal
 * Catalogue from override.html and the transmission behind the Override.
 */
@Composable
fun ArchiveScreen(store: Store, onBack: () -> Unit, onOpen: (String) -> Unit, onBriefing: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val best = store.bestTile
    Box(Modifier.fillMaxSize()) {
        Tube(dim = 0.2f)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 560.dp)) {
                ScreenTitle(
                    "ARCHIVE",
                    if (best > 0) "Highest signal ever: $best ${signal(best).name}." else "Build signals to decrypt the archive.",
                    onBack
                )
                Spacer(Modifier.height(6.dp))
                TermTabs(listOf("FILES", "PHOSPHOR", "SIGNALS"), tab) { tab = it }
            }
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (tab) {
                    0 -> { Files(store, onOpen, onBriefing); Dispatches(store) }
                    1 -> Phosphors(store)
                    else -> Catalogue(best)
                }
            }
        }
    }
}

@Composable
private fun Files(store: Store, onOpen: (String) -> Unit, onBriefing: () -> Unit) {
    val best = store.bestTile
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    if (best >= 2048) {
        ConsolePanel(Modifier.fillMaxWidth(), edge = C.Amber) {
            Column(Modifier.padding(16.dp)) {
                Text("TRANSMISSION UNLOCKED", style = mono(14.sp, C.Amber, 1.5.sp))
                Spacer(Modifier.height(8.dp))
                Text(
                    "You assembled the Override Code. The resistance thanks you with the one thing it still broadcasts: my Corporate Electro EP, six tracks, free.",
                    style = mono(14.sp, C.Bright, 0.sp)
                )
                Spacer(Modifier.height(12.dp))
                CommandButton("LISTEN ON BANDCAMP", Modifier.fillMaxWidth(), primary = true) { onOpen(TRANSMISSION_URL) }
            }
        }
    }
    FILES.forEach { f ->
        val unlocked = best >= f.req
        val expanded = open == f.id
        ConsolePanel(
            Modifier.fillMaxWidth().animateContentSize()
                .clickable(enabled = unlocked, role = Role.Button, onClick = click { open = if (expanded) null else f.id }),
            edge = if (unlocked) C.Main else C.Dim
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (unlocked) f.title else f.title.substringBefore(" // ") + " // ENCRYPTED",
                        style = mono(14.sp, if (unlocked) C.Bright else C.Dim, 1.sp), modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (!unlocked) "TILE ${f.req}" else if (expanded) "[-]" else "[+]",
                        style = mono(12.sp, if (unlocked) C.Main else C.Dim, 1.sp)
                    )
                }
                if (expanded) {
                    Spacer(Modifier.height(10.dp))
                    f.lines.forEach {
                        Text(it, style = mono(14.sp, C.Mid, 0.sp))
                        Spacer(Modifier.height(8.dp))
                    }
                    if (f.id == "f01") CommandButton("REPLAY THE BRIEFING", Modifier.fillMaxWidth(), color = C.Main, height = 46.dp, onClick = onBriefing)
                }
            }
        }
    }
}

/** One dispatch per month: clear every daily of that month to read it. */
@Composable
private fun Dispatches(store: Store) {
    val months = remember { store.dispatchMonths() }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Spacer(Modifier.height(6.dp))
    Text("DISPATCHES", style = mono(13.sp, C.Main, 2.sp))
    Text("One for every month. Clear all the Daily Directives of a month to decrypt its dispatch.", style = mono(12.sp, C.Mid, 0.sp))
    DISPATCHES.forEachIndexed { i, f ->
        val unlocked = (i + 1) in months
        val expanded = open == f.id
        ConsolePanel(
            Modifier.fillMaxWidth().animateContentSize()
                .clickable(enabled = unlocked, role = Role.Button, onClick = click { open = if (expanded) null else f.id }),
            edge = if (unlocked) C.Main else C.Dim
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (unlocked) f.title else f.title + " // ENCRYPTED", style = mono(13.sp, if (unlocked) C.Bright else C.Dim, 1.sp),
                        modifier = Modifier.weight(1f))
                    Text(if (!unlocked) "" else if (expanded) "[-]" else "[+]", style = mono(12.sp, C.Main, 1.sp))
                }
                if (expanded) {
                    Spacer(Modifier.height(8.dp))
                    f.lines.forEach {
                        Text(it, style = mono(14.sp, C.Mid, 0.sp))
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Phosphors(store: Store) {
    val best = store.bestTile
    Text("The colour of the tube. Each one is unlocked by a bigger signal.", style = mono(13.sp, C.Mid, 0.sp))
    PHOSPHORS.forEach { ph ->
        val unlocked = best >= ph.req
        val active = store.phosphorId == ph.id
        ConsolePanel(
            Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = {
                if (unlocked) { Sfx.unlock(); store.changePhosphor(ph.id) } else Sfx.blocked()
            }),
            edge = if (active) C.Amber else if (unlocked) C.Main else C.Dim
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).background(Color(ph.bg)).border(2.dp, Color(ph.main).copy(alpha = if (unlocked) 1f else 0.3f)),
                    contentAlignment = Alignment.Center
                ) { Box(Modifier.size(12.dp).background(Color(ph.main).copy(alpha = if (unlocked) 1f else 0.3f))) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(ph.name.uppercase(), style = mono(15.sp, if (unlocked) C.Bright else C.Dim, 1.sp))
                    Text(if (unlocked) ph.desc else "LOCKED: reach tile ${ph.req}", style = mono(12.sp, if (unlocked) C.Mid else C.Dim, 0.sp))
                }
                if (active) Text("[ON ]", style = mono(14.sp, C.Amber, 1.sp))
            }
        }
    }
}

/** Every value on the matrix is a piece of resistance hardware. */
@Composable
private fun Catalogue(best: Int) {
    Text("Every value on the matrix is a piece of resistance hardware. Here is what you are building.", style = mono(13.sp, C.Mid, 0.sp))
    ConsolePanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            SIGNALS.forEachIndexed { i, s ->
                val known = s.value <= maxOf(best, 4)
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TileIcon(s.value, 46.dp, locked = !known)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (known) s.title.uppercase() else "${s.value} // UNKNOWN SIGNAL", style = mono(14.sp, if (known) C.Bright else C.Dim, 1.sp))
                        Text(if (known) s.about else "Build one to read the catalogue entry.", style = mono(12.sp, if (known) C.Mid else C.Dim, 0.sp))
                    }
                }
                if (i < SIGNALS.size - 1) DashedRule()
            }
            DashedRule()
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TileIcon(4, 46.dp, corrupt = true)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("CORRUPTED NODE", style = mono(14.sp, C.Red, 1.sp))
                    Text("Rogue AI code. It slides but never fuses upward. An equal signal purges it, an EMP Cell next to it fries it.",
                        style = mono(12.sp, C.Mid, 0.sp))
                }
            }
        }
    }
}
