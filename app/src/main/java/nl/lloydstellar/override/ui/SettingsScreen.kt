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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lloydstellar.override.BuildConfig
import nl.lloydstellar.override.data.Store

@Composable
fun SettingsScreen(store: Store, onBack: () -> Unit, onChanged: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Tube(dim = 0.2f)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 560.dp)) { ScreenTitle("SETTINGS", null, onBack) }
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp).padding(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ConsolePanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        TermToggle("SOUND", "Sparks, sweeps, alarms and the voice", store.sound) { store.changeSound(it); onChanged() }
                        DashedRule()
                        TermToggle("MUSIC", "The electro loop that speeds up as your signal grows", store.music) { store.changeMusic(it); onChanged() }
                        DashedRule()
                        TermToggle("HAPTICS", "Feel every slide, fusion and lock", store.haptics) { store.changeHaptics(it); onChanged() }
                        DashedRule()
                        TermToggle("SCREEN EFFECTS", "Shake, static and flashes. Also off when Android's animations are off.", store.effects) { store.changeEffects(it) }
                    }
                }
                DashedRule()
                CommandButton("RESET RECORDS", Modifier.fillMaxWidth(), color = C.Red) { confirm = true }
                Text("2048 Override ${BuildConfig.VERSION_NAME}", style = mono(12.sp, C.Dim, 1.sp))
            }
        }
        if (confirm) ConfirmReset(onYes = { store.resetRecords(); confirm = false }, onNo = { confirm = false })
    }
}

/** A console prompt instead of a Material dialog. */
@Composable
private fun ConfirmReset(onYes: () -> Unit, onNo: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(C.Void.copy(alpha = 0.85f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onNo),
        contentAlignment = Alignment.Center
    ) {
        ConsolePanel(Modifier.padding(horizontal = 22.dp).widthIn(max = 520.dp).fillMaxWidth().clickable(remember { MutableInteractionSource() }, null) { }, edge = C.Red) {
            Column(Modifier.padding(18.dp)) {
                Text("RESET RECORDS?", style = mono(18.sp, C.Red, 1.5.sp))
                Spacer(Modifier.height(10.dp))
                Text("This deletes your sessions, stats, sector bars, achievements, phosphors and decrypted files on this phone. There is no undo, not even a rollback.",
                    style = mono(14.sp, C.Mid, 0.sp))
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CommandButton("CANCEL", Modifier.weight(1f), color = C.Main, onClick = onNo)
                    CommandButton("RESET", Modifier.weight(1f), color = C.Red, primary = true, onClick = onYes)
                }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    var license by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        Tube(dim = 0.2f)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 560.dp)) { ScreenTitle("ABOUT", null, onBack) }
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp).padding(bottom = 24.dp)
            ) {
                Spacer(Modifier.height(10.dp))
                Text("2048 Override ${BuildConfig.VERSION_NAME}", style = mono(16.sp, C.Bright, 1.sp))
                Spacer(Modifier.height(8.dp))
                Body(
                    "2048 Override started as a browser game on my website. This is the same game with the same rules, " +
                        "rebuilt for your phone, with twelve sectors, rollbacks and achievements on top."
                )
                Body(
                    "I'm Lloyd Stellar, an electro producer from Hilversum. On lloydstellar.nl you'll find more free " +
                        "games, music tools and my music."
                )
                Spacer(Modifier.height(10.dp))
                CommandButton("LLOYDSTELLAR.NL", Modifier.fillMaxWidth(), primary = true) { onOpen("https://lloydstellar.nl/") }
                Spacer(Modifier.height(10.dp))
                CommandButton("LISTEN ON BANDCAMP", Modifier.fillMaxWidth(), color = C.Main) { onOpen("https://lloydstellar.bandcamp.com/") }
                Spacer(Modifier.height(10.dp))
                CommandButton("PLAY IN YOUR BROWSER", Modifier.fillMaxWidth(), color = C.Main) { onOpen("https://lloydstellar.nl/override.html") }

                Spacer(Modifier.height(18.dp))
                DashedRule()
                Heading("HOW TO PLAY")
                Body("Swipe to slide every signal on the matrix. Two equal signals that touch fuse into one of double strength. Build a 2048 to overload the AI core.")
                Body("A move without a fusion raises the AI Trace; at 100 % NEXUS-9 locks a cell for a few moves. Red Corrupted Nodes refuse to fuse upward: slide an equal signal into one, or park it next to a 64 EMP Cell.")
                Body("ROLLBACK undoes your last move. You start a session with one charge and earn one for every new 256, 512, 1024 and up, three at most.")
                Heading("PRIVACY")
                Body("No ads, no account, no tracking. The app has no internet access at all. Your scores stay on this phone.")
                Heading("CREDITS")
                Body(
                    "Made by Lloyd Stellar. The sliding and fusing rules come from 2048 by Gabriele Cirulli, under the MIT licence. " +
                        "The voice is S.A.M., the Software Automatic Mouth from 1982, rendered with sam-js by Christian Schiffler. " +
                        "Fonts: Orbitron by Matt McInerney and Share Tech Mono by Carrois Type Design, both under the SIL Open Font License."
                )
                Spacer(Modifier.height(6.dp))
                CommandButton("OPEN SOURCE LICENCES", Modifier.fillMaxWidth(), color = C.Main, height = 46.dp) {
                    license = listOf("MIT-2048.txt", "OFL-orbitron.txt", "OFL-sharetechmono.txt").joinToString("\n\n----------\n\n") { f ->
                        val head = when (f) {
                            "MIT-2048.txt" -> "2048 by Gabriele Cirulli\nhttps://github.com/gabrielecirulli/2048\n\n"
                            "OFL-orbitron.txt" -> "Orbitron\n\n"
                            else -> "Share Tech Mono\n\n"
                        }
                        head + runCatching { ctx.assets.open("licenses/$f").bufferedReader().readText() }.getOrDefault("")
                    }
                }
            }
        }
        license?.let { text ->
            Box(
                Modifier.fillMaxSize().background(C.Void.copy(alpha = 0.95f))
                    .clickable(remember { MutableInteractionSource() }, null) { license = null }
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
                    CommandButton("CLOSE", Modifier.fillMaxWidth(), color = C.Main, height = 46.dp) { license = null }
                    Spacer(Modifier.height(14.dp))
                    Text(text, style = mono(11.sp, C.Mid, 0.sp))
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = mono(13.sp, C.Main, 2.sp), modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
}

@Composable
private fun Body(text: String) {
    Text(text, style = mono(14.sp, C.Mid, 0.sp), modifier = Modifier.padding(bottom = 10.dp))
}
