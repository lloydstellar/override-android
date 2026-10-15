package nl.lloydstellar.override.ui

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lloydstellar.override.App
import nl.lloydstellar.override.data.Store
import nl.lloydstellar.override.engine.PIN_UNLOCK
import nl.lloydstellar.override.engine.SPECIALS
import androidx.compose.runtime.LaunchedEffect
import nl.lloydstellar.override.engine.Special
import nl.lloydstellar.override.engine.specialInfo

/**
 * The manual: every rule and every kind of block on one page, to look
 * things up after the tutorial and the one-time hints. Special blocks you
 * have not met yet stay classified, so they still come as a surprise.
 */
@Composable
fun ManualScreen(store: Store, onBack: () -> Unit) {
    /* what you met since you last opened the manual: marked NEW during this visit */
    val fresh = rememberSaveable { (SPECIALS.map { it.kind.name } + "PIN").filter { store.blockIsNew(it) } }
    LaunchedEffect(Unit) { store.readBlocks() }
    var tab by rememberSaveable { mutableIntStateOf(if (fresh.any { it != "PIN" }) 2 else if (fresh.isNotEmpty()) 1 else 0) }
    Box(Modifier.fillMaxSize()) {
        Tube(dim = 0.2f)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 560.dp)) {
                ScreenTitle("MANUAL", "How the matrix works.", onBack)
                Spacer(Modifier.height(6.dp))
                TermTabs(listOf("BASICS", if ("PIN" in fresh) "NEXUS-9 NEW" else "NEXUS-9", if (fresh.any { it != "PIN" }) "BLOCKS NEW" else "BLOCKS"), tab) { tab = it }
            }
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 24.dp)
            ) {
                when (tab) {
                    0 -> Basics()
                    1 -> Nexus(store, fresh)
                    else -> Blocks(store, fresh)
                }
            }
        }
    }
}

/** One entry: a picture, a title, a line under it and the explanation. */
@Composable
private fun Entry(icon: @Composable () -> Unit, title: String, tag: String?, text: String, titleColor: Color = C.Bright, dim: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        icon()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = mono(15.sp, if (dim) C.Dim else titleColor, 1.sp), modifier = Modifier.semantics { heading() })
            if (tag != null) Text(tag, style = mono(11.sp, if (dim) C.Dim else C.Amber, 1.sp))
            Spacer(Modifier.height(3.dp))
            Text(text, style = mono(13.sp, if (dim) C.Dim else C.Mid, 0.sp))
        }
    }
}

@Composable
private fun Preview(id: String, size: Dp = 52.dp) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val img = remember(id, px) { App.sprites.preview(id, px).asImageBitmap() }
    Image(img, null, Modifier.size(size))
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    ConsolePanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("* $title *", style = mono(12.sp, C.Main, 1.sp))
            content()
        }
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun Basics() {
    Section("THE MATRIX") {
        Entry({ TileIcon(8, 52.dp) }, "SLIDE AND FUSE", null,
            "Swipe to slide every signal at once. Two equal signals that touch fuse into one of double strength. After every move a new 2 or 4 drops in.")
        DashedRule()
        Entry({ TileIcon(2048, 52.dp) }, "THE OVERRIDE", null,
            "Build a 2048 and the core of NEXUS-9 goes down. In Classic you can keep going after that, towards 4096 and 8192.")
        DashedRule()
        Entry({ TileIcon(64, 52.dp) }, "EMP CELL", "THE 64",
            "Any red block that ends up right next to a 64 burns out, for 64 Hz. Slide your 64 towards trouble.")
        DashedRule()
        Entry({ TileIcon(256, 52.dp) }, "ROLLBACK", null,
            "Undoes your last move, the new block and the trace included. You start with one charge (three in CALM) and earn one for every new 256, 512, 1024 and up, as long as the buffer has room.")
    }
}

@Composable
private fun Nexus(store: Store, fresh: List<String>) {
    val best = store.bestTile
    Section("THE AI TRACE") {
        Entry({ Preview("NODE") }, "TRACE LEVEL", null,
            "Every move without a fusion raises the trace by 5 %, plus 3 % for every red block on the board. Every fusion lowers it, a fusion of 128 or more a bit extra. Higher threat levels make it climb faster.")
    }
    Section("NEXUS-9 LOCKS") {
        Entry({ Preview("LOCK") }, "LOCKED CELL", null,
            "At 100 % NEXUS-9 seals a cell. Nothing slides into it or out of it until its counter runs out, and the trace falls back to 25 %.")
        DashedRule()
        Entry({ Preview("LOCK") }, "NEXUS-9 LEARNS", null,
            "Each lock in a session holds one move longer than the one before, up to nine moves. A low trace early on keeps the later locks short.")
        DashedRule()
        Entry({ Preview("LOCK") }, "CRACK THE SEAL", null,
            "A fusion right next to a locked cell takes two moves off its counter. Fuse beside a lock to get the cell back sooner.")
        DashedRule()
        val pin = best >= PIN_UNLOCK || store.metBlock("PIN")
        Entry({ Preview(if (pin) "PIN" else "UNKNOWN") }, if (pin) "PIN LOCK" else "CLASSIFIED",
            if (!pin) null else if ("PIN" in fresh) "NEW  //  FROM YOUR FIRST 512" else "FROM YOUR FIRST 512",
            if (pin) "Sometimes NEXUS-9 seals a cell with a signal in it. That signal stays put until the lock runs out or you crack it."
            else "Decrypts once you have built a $PIN_UNLOCK.", dim = !pin)
    }
}

private class BlockText(val special: Special?, val id: String, val name: String, val text: String)

private val BLOCKS = listOf(
    BlockText(null, "NODE", "CORRUPTED NODE",
        "Rogue code. It slides but never fuses upward, and feeds the trace while it is on the board. Slide an equal signal into it and both disappear; a 64 next to it fries it."),
    BlockText(Special.SUPPLY, "SIGNAL", "SUPPLY DROP",
        "A signal from the resistance: a copy of the signal right next to where it lands, so you can fuse the two straight away."),
    BlockText(Special.CAPACITOR, "CAPACITOR", "SURGE CAPACITOR",
        "Slides but never fuses. The ring shows its charge; at zero it discharges into the four cells around it. Signals up to 64 and red blocks are wiped out, bigger signals ride it out, walls lose a layer. Park it next to clutter."),
    BlockText(Special.CASED, "CASED", "CASED SIGNAL",
        "A signal in a lead case. Slide an equal signal into it: the first hit cracks the case, the second fuses the two for double points."),
    BlockText(Special.BEACON, "BEACON", "TRACE BEACON",
        "A red block with a countdown. Clear it like a node before it reaches zero, or it transmits: the trace jumps to 100 % and the beacon stays behind as a Corrupted Node."),
    BlockText(Special.PATCH, "PATCH", "PATCH CABLE",
        "Fuses with any signal up to 256 and doubles it, so a 256 and the cable make a 512. It does not take bigger signals."),
    BlockText(Special.WALL, "WALL", "STATIC WALL",
        "Does not slide, and nothing fuses with it. Every fusion right next to it wears off a layer; after two it falls. A capacitor's discharge chips it too."),
    BlockText(Special.WORM, "WORM", "WORM",
        "A red block that spreads: every eight moves it turns the newest signal into a Corrupted Node. Clear it with an equal signal or a 64."),
)

@Composable
private fun Blocks(store: Store, fresh: List<String>) {
    val best = store.bestTile
    Text("Special blocks turn up in Classic, the first time when your signal gets big enough, and earlier once you have met them. CALM only gets the friendly ones. Some Daily and Weekly Directives bring one kind from the very first move.",
        style = mono(13.sp, C.Mid, 0.sp))
    Spacer(Modifier.height(12.dp))
    Section("ON THE MATRIX") {
        BLOCKS.forEachIndexed { i, b ->
            val info = b.special?.let { specialInfo(it) }
            val known = info == null || best >= info.unlock || store.metBlock(info.kind.name)
            val hostile = b.id == "NODE" || b.id == "BEACON" || b.id == "WORM"
            val tag = when {
                info == null -> "FROM A 32"
                !known -> null
                else -> (if (info.kind.name in fresh) "NEW  //  " else "") + "FROM A ${info.early}" + if (info.friendly) "" else "  //  NOT IN CALM"
            }
            Entry(
                { Preview(if (known) b.id else "UNKNOWN") },
                if (known) b.name else "CLASSIFIED", tag,
                if (known) b.text else "Decrypts once you have built a ${info!!.unlock} or met it on the matrix.",
                titleColor = if (hostile) C.Red else C.Bright, dim = !known,
            )
            if (i < BLOCKS.size - 1) DashedRule()
        }
    }
}
