package nl.lloydstellar.override.ui

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lloydstellar.override.data.ACHIEVEMENTS
import nl.lloydstellar.override.data.Store
import nl.lloydstellar.override.data.signal
import nl.lloydstellar.override.engine.SECTORS
import nl.lloydstellar.override.engine.THREATS
import nl.lloydstellar.override.engine.threat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val RANKS = listOf("1ST", "2ND", "3RD") + (4..20).map { "${it}TH" }

@Composable
fun RecordsScreen(store: Store, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        Tube(dim = 0.2f)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 560.dp)) {
                ScreenTitle("RECORDS", "Kept on this phone only.", onBack)
                Spacer(Modifier.height(6.dp))
                TermTabs(listOf("SESSIONS", "STATS", "AWARDS"), tab) { tab = it }
            }
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 24.dp)
            ) {
                when (tab) {
                    0 -> Sessions(store)
                    1 -> Stats(store)
                    else -> Awards(store)
                }
            }
        }
    }
}

/** Your top Classic sessions, laid out like the hall of fame on lloydstellar.nl. */
@Composable
private fun Sessions(store: Store) {
    val runs = store.runs.take(20)
    if (runs.isEmpty()) {
        Empty("No sessions yet. NEXUS-9 is waiting.")
        return
    }
    val fmt = SimpleDateFormat("MMM d", Locale.ENGLISH)
    ConsolePanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("* FREQUENCY INJECTORS *", style = mono(12.sp, C.Main, 1.sp))
            Text("YOUR BEST ${runs.size}  //  CLASSIC, EVERY LEVEL", style = mono(11.sp, C.Dim, 1.sp))
            Spacer(Modifier.height(6.dp))
            runs.forEachIndexed { i, r ->
                val first = i == 0
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(RANKS[i], style = mono(14.sp, if (first) C.Amber else C.Mid, 1.sp), modifier = Modifier.width(46.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${pad(r.score, 6)} Hz", style = mono(19.sp, if (first) C.Amber else C.Bright, 1.sp))
                        Text(
                            "${threat(r.level).short}  ${fmt.format(Date(r.time)).uppercase()}  ${r.moves} MOVES" + if (r.won) "  OVERRIDE" else "",
                            style = mono(11.sp, if (r.won) C.Green else C.Dim, 0.5.sp), maxLines = 1
                        )
                    }
                    TileIcon(r.tile, 36.dp)
                }
                if (i < runs.size - 1) DashedRule()
            }
        }
    }
}

@Composable
private fun Stats(store: Store) {
    val en = Locale.ENGLISH
    ConsolePanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("* LIFETIME *", style = mono(12.sp, C.Main, 1.sp))
            Spacer(Modifier.height(4.dp))
            THREATS.filter { store.bestFor(it.level) > 0 }.forEach {
                LeaderRow("BEST // ${it.short}", "${pad(store.bestFor(it.level), 6)} Hz", C.Amber)
            }
            LeaderRow("THREAT CLEARED", (THREATS.filter { store.overrideAt(it.level) }.maxOfOrNull { it.level } ?: -2).let { if (it < -1) "-" else threat(it).short })
            LeaderRow("HIGHEST SIGNAL", if (store.bestTile > 0) "${store.bestTile} ${signal(store.bestTile).name}" else "-")
            LeaderRow("OVERRIDES", store.overrides.toString())
            LeaderRow("FASTEST OVERRIDE", if (store.fastestOverride > 0) "${store.fastestOverride} MOVES" else "-")
            LeaderRow("SESSIONS", store.games.toString())
            LeaderRow("TOTAL FREQUENCY", "%,d Hz".format(en, store.totalScore))
            LeaderRow("FUSIONS", "%,d".format(en, store.fusions))
            LeaderRow("INJECTIONS", "%,d".format(en, store.moves))
            LeaderRow("NODES REMOVED", "%,d".format(en, store.nodes))
            LeaderRow("NEXUS-9 LOCKS", "%,d".format(en, store.locks))
            LeaderRow("ROLLBACKS", store.rollbacks.toString())
            LeaderRow("SECTORS CLEARED", "${SECTORS.count { store.bars[it.number - 1] > 0 }}/${SECTORS.size}")
            if (store.deepest >= nl.lloydstellar.override.engine.DeepGrid.FIRST) LeaderRow("DEEPEST SECTOR", store.deepest.toString(), C.Amber)
            LeaderRow("DAILIES CLEARED", store.dailiesCleared.toString())
            LeaderRow("WEEKLIES CLEARED", store.weekliesCleared.toString())
            LeaderRow("TIME ON THE GRID", formatTime(store.seconds.toInt()))
        }
    }
}

@Composable
private fun Awards(store: Store) {
    val got = ACHIEVEMENTS.count { store.isUnlocked(it.id) }
    ConsolePanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row {
                Text("* ACHIEVEMENTS *", style = mono(12.sp, C.Main, 1.sp), modifier = Modifier.weight(1f))
                Text("$got/${ACHIEVEMENTS.size}", style = mono(12.sp, C.Amber, 1.sp))
            }
            Spacer(Modifier.height(4.dp))
            ACHIEVEMENTS.forEachIndexed { i, a ->
                val on = store.isUnlocked(a.id)
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
                    Text(if (on) "[*]" else "[ ]", style = mono(14.sp, if (on) C.Amber else C.Dim, 0.sp), modifier = Modifier.width(36.dp))
                    Column {
                        Text(a.title.uppercase(), style = mono(15.sp, if (on) C.Bright else C.Mid, 1.sp))
                        Text(a.text, style = mono(12.sp, if (on) C.Mid else C.Dim, 0.sp))
                    }
                }
                if (i < ACHIEVEMENTS.size - 1) DashedRule()
            }
        }
    }
}
