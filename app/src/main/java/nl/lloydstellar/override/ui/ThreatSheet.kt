package nl.lloydstellar.override.ui

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lloydstellar.override.data.Store
import nl.lloydstellar.override.engine.THREATS
import nl.lloydstellar.override.engine.Threat

/**
 * NEW SESSION: pick how hard NEXUS-9 hits. CALM and STANDARD are always
 * there; every THREAT rung opens after an Override on the one before it.
 */
@Composable
fun ThreatSheet(store: Store, onStart: (Int) -> Unit, onClose: () -> Unit) {
    var chosen by remember { mutableIntStateOf(store.lastLevel.takeIf { store.threatOpen(it) } ?: 0) }
    Box(
        Modifier.fillMaxSize().background(C.Void.copy(alpha = 0.92f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Scanlines()
        ConsolePanel(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 560.dp).fillMaxWidth()
                .clickable(remember { MutableInteractionSource() }, null) { },
            edge = C.Amber
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("NEW SESSION // THREAT LEVEL", style = mono(12.sp, C.Amber, 1.5.sp))
                Spacer(Modifier.height(4.dp))
                Text("How hard does NEXUS-9 hit?", style = mono(15.sp, C.Bright, 0.5.sp))
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    THREATS.forEach { t -> ThreatRow(store, t, t.level == chosen) { chosen = t.level } }
                }
                Spacer(Modifier.height(12.dp))
                CommandButton("JACK IN", Modifier.fillMaxWidth(), primary = true, height = 56.dp, trailing = THREATS.first { it.level == chosen }.short) {
                    onStart(chosen)
                }
                Spacer(Modifier.height(8.dp))
                CommandButton("BACK", Modifier.fillMaxWidth(), color = C.Main, height = 46.dp, onClick = onClose)
            }
        }
    }
}

@Composable
private fun ThreatRow(store: Store, t: Threat, selected: Boolean, onSelect: () -> Unit) {
    val open = store.threatOpen(t.level)
    val won = store.overrideAt(t.level)
    val best = store.bestFor(t.level)
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) C.Amber.copy(alpha = 0.12f) else C.Void.copy(alpha = 0f))
            .clickable(enabled = open, role = Role.RadioButton, onClick = click(onSelect))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(if (selected) "(*)" else if (open) "( )" else "[#]", style = mono(14.sp, if (selected) C.Amber else if (open) C.Mid else C.Dim, 0.sp),
            modifier = Modifier.width(36.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.name, style = mono(14.sp, if (open) C.Bright else C.Dim, 1.sp), modifier = Modifier.weight(1f), maxLines = 1)
                if (won) Text("OVERRIDE", style = mono(11.sp, C.Green, 1.sp))
            }
            Text(
                if (open) t.adds else "Locked: make the Override at ${if (t.level == 1) "Standard" else "Threat ${t.level - 1}"}.",
                style = mono(12.sp, if (open) C.Mid else C.Dim, 0.sp)
            )
            if (open && best > 0) Text("BEST ${pad(best, 6)} Hz", style = mono(11.sp, C.Amber, 1.sp))
        }
    }
}
