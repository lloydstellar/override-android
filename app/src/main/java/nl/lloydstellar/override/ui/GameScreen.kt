package nl.lloydstellar.override.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import nl.lloydstellar.override.GameController
import nl.lloydstellar.override.Mode
import nl.lloydstellar.override.Phase
import nl.lloydstellar.override.Result
import nl.lloydstellar.override.data.Achievement
import nl.lloydstellar.override.data.Store
import nl.lloydstellar.override.data.signal
import nl.lloydstellar.override.engine.EndReason
import nl.lloydstellar.override.engine.SECTORS
import nl.lloydstellar.override.render.GameView

@Composable
fun GameScreen(
    game: GameController,
    store: Store,
    onHome: () -> Unit,
    onMap: () -> Unit,
    onShare: (Result) -> Unit,
    onTutorialDone: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val view = remember { GameView(ctx, game) }
    view.effectsEnabled = store.effects && GameView.systemAnimations()
    view.phosphor = store.phosphor
    view.highlightTrace = game.mode == Mode.TUTORIAL && game.tutorialStep in listOf(
        GameController.TutorialStep.TRACE, GameController.TutorialStep.COOL, GameController.TutorialStep.LOCK
    )
    var topPx by remember { mutableIntStateOf(0) }
    var bottomPx by remember { mutableIntStateOf(0) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> { view.startLoop(); view.keepScreenOn = true; view.requestFocus(); game.updateMusic() }
                Lifecycle.Event.ON_PAUSE -> { game.saveIfRunning(); view.stopLoop(); view.keepScreenOn = false }
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { view.startLoop(); view.keepScreenOn = true; view.requestFocus() }
        onDispose { lifecycle.removeObserver(obs); view.stopLoop() }
    }

    val tutorial = game.mode == Mode.TUTORIAL
    BackHandler {
        if (tutorial) { onTutorialDone(); return@BackHandler }
        when (game.phase) {
            Phase.PLAYING -> game.pause()
            Phase.PAUSED -> game.resume()
            Phase.OVER -> {}
            Phase.WON, Phase.RESULTS -> onHome()
        }
    }

    Box(Modifier.fillMaxSize().background(C.Void)) {
        AndroidView({ view }, Modifier.fillMaxSize(), update = {
            it.topReserve = topPx.toFloat()
            it.bottomReserve = bottomPx.toFloat()
        })

        /* the status bar stays free */
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .onGloballyPositioned { topPx = (it.positionInRoot().y + it.size.height).toInt() })

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .onGloballyPositioned { bottomPx = (it.findRootCoordinates().size.height - it.positionInRoot().y).toInt() }
        ) {
            if (tutorial) {
                TutorialCard(game.tutorialStep, game.tutorialLocked, game.canRollback, onTutorialDone)
                if (game.tutorialStep == GameController.TutorialStep.ROLLBACK) BottomBar(game, highlightRollback = true)
            } else BottomBar(game)
        }

        game.hint?.let { h ->
            if (game.phase == Phase.PLAYING) HintPanel(h, Modifier.align(Alignment.BottomCenter).padding(bottom = with(androidx.compose.ui.platform.LocalDensity.current) { bottomPx.toDp() } + 8.dp)) { game.dismissHint() }
        }

        Toasts(game, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)))

        var manual by rememberSaveable { mutableStateOf(false) }
        AnimatedVisibility(game.phase == Phase.PAUSED, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
            PauseOverlay(game, store, onHome, onManual = { manual = true })
        }
        AnimatedVisibility(manual && game.phase == Phase.PAUSED, enter = fadeIn(tween(200)), exit = fadeOut(tween(150))) {
            androidx.activity.compose.BackHandler { manual = false }
            Box(Modifier.fillMaxSize().background(C.Void)) { ManualScreen(store) { manual = false } }
        }
        val res = game.result
        AnimatedVisibility(game.phase == Phase.WON, enter = fadeIn(tween(400, delayMillis = 1300)), exit = fadeOut(tween(150))) {
            if (game.mode == Mode.CLASSIC) OverrideOverlay(game) else SectorClearOverlay(game, onMap, onShare)
        }
        AnimatedVisibility(game.phase == Phase.RESULTS && res != null, enter = fadeIn(tween(250)), exit = fadeOut(tween(150))) {
            if (res != null) ResultsOverlay(game, res, onHome = onHome, onMap = onMap, onShare = { onShare(res) })
        }
    }
}

/* ---------------- the tutorial ---------------- */

private val TUTORIAL = mapOf(
    GameController.TutorialStep.SLIDE to ("SWIPE TO SLIDE" to "Swipe anywhere on the screen. Every signal slides as far as it can. Slide the two 2s together."),
    GameController.TutorialStep.FUSE to ("FUSE" to "Two equal signals fuse into one of double strength. A second 4 just arrived: fuse the two 4s into an 8."),
    GameController.TutorialStep.TRACE to ("THE AI TRACE" to "The lit bar at the top is the AI TRACE. It rises with every move that fuses nothing. Slide a few times without fusing and watch it climb."),
    GameController.TutorialStep.COOL to ("COOL IT DOWN" to "Fusing makes the trace drop again. Slide so two equal signals touch and fuse."),
    GameController.TutorialStep.LOCK to ("NEXUS-9" to "If the trace reaches 100\u00A0%, NEXUS-9 locks a cell. Try it: slide without fusing until the bar is full."),
    GameController.TutorialStep.ROLLBACK to ("ROLLBACK" to "Made a bad move? ROLLBACK takes it back, new tile included. You earn charges by building big signals. Make any move first."),
    GameController.TutorialStep.READY to ("READY" to "That is all you need. Build a 2048 to take the AI core down. Red nodes, EMP Cells and the other blocks are explained when you meet them, and the MANUAL in the pause menu has it all."),
)

private const val ROLLBACK_READY = "Now tap ROLLBACK below and watch your last move disappear."

private const val LOCKED_TEXT = "That red cell is locked: nothing slides into it or out of it. The number counts down with every move. Make a few moves and it opens again."

/** The tutorial: one thing per step, always by doing it, always with a way out. */
@Composable
private fun TutorialCard(step: GameController.TutorialStep, locked: Boolean, rollbackReady: Boolean, onDone: () -> Unit) {
    val (title, body0) = TUTORIAL.getValue(step)
    val body = when {
        step == GameController.TutorialStep.LOCK && locked -> LOCKED_TEXT
        step == GameController.TutorialStep.ROLLBACK && rollbackReady -> ROLLBACK_READY
        else -> body0
    }
    val last = step == GameController.TutorialStep.READY
    val swipe = step.ordinal <= GameController.TutorialStep.LOCK.ordinal || step == GameController.TutorialStep.ROLLBACK && !rollbackReady
    ConsolePanel(
        Modifier.fillMaxWidth().wrapContentWidth().widthIn(max = 560.dp).padding(horizontal = 16.dp, vertical = 10.dp)
            .height(206.dp),
        edge = C.Amber
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TRAINING ${step.ordinal + 1}/${TUTORIAL.size}", style = mono(12.sp, C.Amber, 1.5.sp), modifier = Modifier.weight(1f))
                if (!last) {
                    Text(
                        "SKIP TUTORIAL ▸", style = mono(13.sp, C.Mid, 1.sp),
                        modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = click(onDone))
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("> $title", style = mono(17.sp, C.Bright, 1.sp))
                    Spacer(Modifier.height(4.dp))
                    Text(body, style = mono(13.sp, C.Mid, 0.sp))
                }
                if (swipe) SwipeHand(vertical = false)
            }
            Spacer(Modifier.weight(1f))
            if (last) CommandButton("JACK IN", Modifier.fillMaxWidth(), primary = true, height = 50.dp, onClick = onDone)
        }
    }
}

/** A hand that swipes sideways, so nobody has to read how to play. */
@Composable
private fun SwipeHand(vertical: Boolean) {
    val t = rememberInfiniteTransition(label = "hand")
    val x by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "x")
    Box(Modifier.width(64.dp).height(56.dp), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Rounded.TouchApp, null, tint = C.Bright,
            modifier = Modifier.size(34.dp).offset(x = if (vertical) 0.dp else (x * 16).dp, y = if (vertical) (x * 12).dp else 0.dp)
        )
    }
}

/* ---------------- the bar under the terminal ---------------- */

@Composable
private fun BottomBar(game: GameController, highlightRollback: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().wrapContentWidth().widthIn(max = 560.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconCommand(Icons.Rounded.Pause, "Pause", size = 52.dp, onClick = { game.pause() })
        val charges = game.charges
        val label = if (game.maxCharges == 0) "NO ROLLBACK" else "ROLLBACK " + "■".repeat(charges) + "□".repeat((game.maxCharges - charges).coerceAtLeast(0))
        CommandButton(
            label, Modifier.weight(1f).semantics { contentDescription = "Rollback, $charges " + (if (charges == 1) "charge" else "charges") + " left" },
            color = if (highlightRollback && rememberBlink(450)) C.Amber else C.Main, height = 52.dp,
            enabled = game.canRollback && game.phase == Phase.PLAYING
        ) { game.rollback() }
    }
}

private val HINTS = mapOf(
    "swipe" to ("SWIPE TO SLIDE EVERY SIGNAL" to "Two equal signals that touch fuse into one of double strength. Keep fusing until you hit the target."),
    "corrupt" to ("A RED NODE APPEARED" to "Red nodes block a cell and help NEXUS-9 find you: every move without a fusion raises the trace faster. Slide an equal signal into one to wipe out both, or let a 64 touch it."),
    "trace" to ("THE AI TRACE IS CLIMBING" to "Every move without a fusion raises it, every fusion lowers it. At 100\u00A0% NEXUS-9 locks a cell."),
    "lock" to ("NEXUS-9 LOCKED A CELL" to "Nothing slides into or out of a locked cell. The lock counts down with every move, and a fusion right next to it cracks two moves off."),
    "learns" to ("NEXUS-9 LEARNS" to "Every lock in this session holds one move longer than the one before, up to nine. Keep the trace down."),
    "pin" to ("PIN LOCK" to "This time NEXUS-9 sealed a cell with a signal in it. The signal stays put until the lock runs out or you crack it."),
    "supply" to ("SUPPLY DROP" to "The resistance dropped a copy of the signal right next to it. Fuse the two before something gets in between."),
    "capacitor" to ("SURGE CAPACITOR" to "It slides but never fuses. When its counter hits zero it discharges into the four cells around it: signals up to 64 and red nodes are wiped out, bigger signals ride it out. Park it next to clutter."),
    "cased" to ("CASED SIGNAL" to "A signal in a lead case. Slide an equal signal into it: the first hit cracks the case, the second fuses them for double points."),
    "beacon" to ("TRACE BEACON" to "A red node with a timer. If it reaches zero it transmits: the trace jumps to 100\u00A0% and NEXUS-9 locks a cell. Wipe it out like a node before that."),
    "patch" to ("PATCH CABLE" to "It fuses with any signal up to 256 and doubles it. Save it for the biggest one it can take."),
    "wall" to ("STATIC WALL" to "A wall does not slide and nothing fuses with it. Every fusion right next to it wears off a layer; after two it falls."),
    "worm" to ("WORM" to "A red node that spreads: every eight moves it turns the newest signal into a red node. Wipe it out with an equal signal or a 64."),
    "emp" to ("EMP DISCHARGE" to "Your 64 just fried a red node. A 64 is an EMP Cell: any node that ends up right next to it burns out, for 64 Hz."),
    "rollback" to ("ROLLBACK BUFFER" to "ROLLBACK undoes your last move, spawn and trace included. You get a charge for every new 256, 512, 1024 and up, as far as the buffer holds."),
)

@Composable
private fun HintPanel(id: String, modifier: Modifier, onDismiss: () -> Unit) {
    val (title, body) = HINTS[id] ?: return
    ConsolePanel(
        modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 16.dp)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
        edge = C.Amber
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("> $title", style = mono(14.sp, C.Amber, 1.sp), modifier = Modifier.weight(1f))
                Text("[OK]", style = mono(13.sp, C.Mid, 1.sp))
            }
            Spacer(Modifier.height(6.dp))
            Text(body, style = mono(13.sp, C.Bright, 0.sp))
        }
    }
}

@Composable
private fun Toasts(game: GameController, modifier: Modifier) {
    val toast = game.toasts.firstOrNull()
    LaunchedEffect(toast) {
        if (toast != null) { delay(2600); game.dismissToast(toast) }
    }
    AnimatedVisibility(
        toast != null, modifier,
        enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()
    ) {
        val a: Achievement? = toast
        if (a != null) {
            ConsolePanel(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).widthIn(max = 520.dp).fillMaxWidth(), edge = C.Amber) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("ACHIEVEMENT  //  ${a.title.uppercase()}", style = mono(14.sp, C.Amber, 1.sp), maxLines = 1)
                    Text(a.text, style = mono(12.sp, C.Bright, 0.sp), maxLines = 1)
                }
            }
        }
    }
}

/* ---------------- overlays ---------------- */

/** Dark screen with the site's scanlines, for pause and results. */
@Composable
private fun Scrim(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(C.Void.copy(alpha = 0.95f))
            .clickable(remember { MutableInteractionSource() }, null) { },
    ) {
        Scanlines()
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun PauseOverlay(game: GameController, store: Store, onHome: () -> Unit, onManual: () -> Unit) {
    Scrim {
        Text("PAUSED", style = h1(40.sp))
        Spacer(Modifier.height(10.dp))
        AmberStroke()
        Spacer(Modifier.height(10.dp))
        val s = game.sector
        Text(
            (if (s != null) "${s.code} ${s.name}  //  " else "") + "${pad(game.score, 6)} Hz  //  MAX ${signal(game.maxTile).name}",
            style = mono(13.sp, C.Mid, 1.sp)
        )
        Spacer(Modifier.height(26.dp))
        CommandButton("RESUME", Modifier.fillMaxWidth(), primary = true, height = 60.dp) { game.resume() }
        Spacer(Modifier.height(10.dp))
        CommandButton("MANUAL", Modifier.fillMaxWidth(), color = C.Main, trailing = if (store.manualHasNews) "NEW" else null, onClick = onManual)
        Spacer(Modifier.height(10.dp))
        CommandButton(if (s != null) "START OVER" else "NEW SESSION", Modifier.fillMaxWidth(), color = C.Main) { game.restart() }
        Spacer(Modifier.height(10.dp))
        CommandButton("END SESSION", Modifier.fillMaxWidth(), color = C.Main) { game.endSession() }
        Spacer(Modifier.height(10.dp))
        CommandButton("HOME", Modifier.fillMaxWidth(), color = C.Main, onClick = onHome)
        Spacer(Modifier.height(22.dp))
        TermToggle("SOUND", "Sparks, sweeps and the alarms", store.sound) { store.changeSound(it); game.updateMusic() }
        DashedRule()
        TermToggle("MUSIC", "The soundtrack that speeds up as your signal grows", store.music) { store.changeMusic(it); game.updateMusic() }
        DashedRule()
        TermToggle("HAPTICS", "Feel every slide, fusion and lock", store.haptics) { store.changeHaptics(it) }
    }
}

/** Classic: the core is down. Keep going, or close the session. */
@Composable
private fun OverrideOverlay(game: GameController) {
    val e = game.engine
    Scrim {
        Text("SYSTEM OVERRIDE", style = h1(30.sp))
        Text("SUCCESSFUL", style = h1(30.sp))
        Spacer(Modifier.height(10.dp))
        AmberStroke()
        Spacer(Modifier.height(10.dp))
        Text("AI CORE OFFLINE  //  SECTOR 7 FREED", style = mono(13.sp, C.Green, 1.sp))
        Spacer(Modifier.height(16.dp))
        ConsolePanel(Modifier.fillMaxWidth(), edge = C.Amber) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                LeaderRow("INJECTIONS", pad(e.moves, 4))
                LeaderRow("FREQUENCY", "${pad(e.score, 6)} Hz")
                LeaderRow("UPTIME", formatTime(game.seconds))
                LeaderRow("TRACE AT OVERRIDE", "${pad(e.trace, 3)}%")
                LeaderRow("ROLLBACKS USED", game.rollbacksUsed.toString())
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("FILE 04, THE OVERRIDE WHITE PHOSPHOR AND A TRANSMISSION ARE WAITING IN THE ARCHIVE.",
            style = mono(12.sp, C.Mid, 0.5.sp))
        Spacer(Modifier.height(18.dp))
        CommandButton("CONTINUE: GLOBAL PURGE", Modifier.fillMaxWidth(), primary = true, height = 60.dp) { game.continueEndless() }
        Spacer(Modifier.height(10.dp))
        CommandButton("END SESSION", Modifier.fillMaxWidth(), color = C.Main) { game.endSession() }
    }
}

@Composable
private fun SectorClearOverlay(game: GameController, onMap: () -> Unit, onShare: (Result) -> Unit) {
    val s = game.sector ?: return
    val directive = s.kind != nl.lloydstellar.override.engine.Sector.Kind.SECTOR
    val r = game.result
    val e = game.engine
    val bars = r?.bars ?: 1
    val shown = remember { Animatable(0f) }
    LaunchedEffect(s.id) {
        delay(1500)
        for (i in 1..bars) { shown.animateTo(i.toFloat(), tween(320)); nl.lloydstellar.override.audio.Sfx.bar(i - 1) }
    }
    Scrim {
        Text(if (directive) "DIRECTIVE CLEAR" else "SECTOR CLEAR", style = h1(if (directive) 30.sp else 34.sp))
        Spacer(Modifier.height(10.dp))
        AmberStroke()
        Spacer(Modifier.height(10.dp))
        Text(
            (if (directive) s.name else "${s.code} ${s.name}") + "  //  ${s.rules.target}-BIT PULSE INJECTED",
            style = mono(13.sp, C.Green, 1.sp)
        )
        Spacer(Modifier.height(16.dp))
        ConsolePanel(Modifier.fillMaxWidth(), edge = C.Amber) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Bars(shown.value.toInt(), size = 34.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("SIGNAL BARS", style = mono(11.sp, C.Mid, 1.5.sp))
                        Text("$bars OF 3", style = mono(24.sp, C.Bright, 1.sp))
                        if (r?.barsImproved == true && bars > 1) Text(if (directive) "NEW RECORD FOR THIS DIRECTIVE" else "NEW RECORD FOR THIS SECTOR", style = mono(11.sp, C.Amber, 1.sp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                DashedRule()
                LeaderRow("OVERRIDE", "YES", C.Green)
                LeaderRow("WITHIN PAR ${s.par}", if (e.moves <= s.par) "YES (${e.moves})" else "NO (${e.moves})", if (e.moves <= s.par) C.Green else C.Dim)
                val ok = e.locksSuffered <= s.lockBudget
                LeaderRow(lockGoal(s.lockBudget), if (ok) "YES (${e.locksSuffered})" else "NO (${e.locksSuffered})", if (ok) C.Green else C.Dim)
            }
        }
        game.monthDecrypted?.let { d ->
            Spacer(Modifier.height(12.dp))
            Text("EVERY DAILY OF ${d.month.name} CLEARED. ${nl.lloydstellar.override.data.DISPATCHES[d.monthValue - 1].title} IS DECRYPTED IN THE ARCHIVE.",
                style = mono(12.sp, C.Green, 0.5.sp))
        }
        Spacer(Modifier.height(18.dp))
        val next = if (directive) null else SECTORS.getOrNull(s.number)
        if (directive) {
            game.result?.let { res -> CommandButton("SHARE", Modifier.fillMaxWidth(), primary = true, height = 60.dp) { onShare(res) } }
            Spacer(Modifier.height(10.dp))
        } else if (next != null) {
            CommandButton("NEXT: ${next.code} ${next.name}", Modifier.fillMaxWidth(), primary = true, height = 60.dp) { game.newSector(next, false) }
            Spacer(Modifier.height(10.dp))
        } else {
            Text("ALL TWELVE SECTORS ARE FREE. THE GRID IS OURS.", style = mono(14.sp, C.Amber, 1.sp))
            Spacer(Modifier.height(14.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CommandButton("RETRY", Modifier.weight(1f), color = C.Main) { game.restart() }
            CommandButton(if (directive) "DIRECTIVES" else "MAP", Modifier.weight(1f), color = C.Main, onClick = onMap)
        }
    }
}

@Composable
private fun ResultsOverlay(game: GameController, r: Result, onHome: () -> Unit, onMap: () -> Unit, onShare: () -> Unit) {
    val shown = remember { Animatable(0f) }
    val count = remember { Animatable(0f) }
    LaunchedEffect(r) { shown.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
    LaunchedEffect(r) {
        delay(200)
        count.animateTo(r.score.toFloat(), tween((600 + r.score / 20).coerceAtMost(1600), easing = FastOutSlowInEasing))
    }
    val blink = rememberBlink(500)
    val (title, sub, color) = when {
        r.reason == EndReason.OUT_OF_MOVES -> Triple("WINDOW CLOSED", "THE SATELLITE IS GONE", C.Red)
        r.reason == EndReason.SIGNAL_LOST -> Triple("SIGNAL LOST", "AI DETECTED", C.Red)
        r.won -> Triple("SESSION CLOSED", "OVERRIDE LOGGED", C.Green)
        else -> Triple("SESSION TERMINATED", "LINK CLOSED BY OPERATOR", C.Amber)
    }
    Scrim {
        Column(Modifier.offset(y = ((1f - shown.value) * 40).dp).alpha(shown.value)) {
            Text(title, style = mono(24.sp, color, 2.sp).copy(shadow = Shadow(color.copy(alpha = 0.5f), blurRadius = 12f)))
            Spacer(Modifier.height(4.dp))
            Text(
                if (r.newBest) (if (blink) "** NEW BEST FREQUENCY **" else "") else sub,
                style = mono(14.sp, if (r.newBest) C.Amber else C.Mid, 2.sp)
            )
            Spacer(Modifier.height(16.dp))
            ConsolePanel(Modifier.fillMaxWidth(), edge = if (r.newBest) C.Amber else C.Main) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Text("FINAL FREQUENCY", style = mono(12.sp, C.Mid, 1.5.sp))
                    Text("${pad(count.value.toInt(), 6)} Hz", style = mono(42.sp, C.Bright, 2.sp))
                    if (r.mode == Mode.CLASSIC) Text(nl.lloydstellar.override.engine.threat(game.level).name, style = mono(12.sp, C.Mid, 1.sp))
                    if (r.rank > 0) Text("#${r.rank} OF YOUR SESSIONS AT THIS LEVEL", style = mono(13.sp, if (r.rank == 1) C.Amber else C.Mid, 1.5.sp))
                    if (r.sector != null) Text("${r.sector.code} ${r.sector.name}  //  TARGET ${r.sector.rules.target}", style = mono(13.sp, C.Mid, 1.sp))
                    Spacer(Modifier.height(12.dp))
                    DashedRule()
                    Spacer(Modifier.height(12.dp))
                    if (r.tile > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                        TileIcon(r.tile, 52.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("HIGHEST SIGNAL", style = mono(11.sp, C.Mid, 1.5.sp))
                            Text(signal(r.tile).title, style = mono(19.sp, C.Bright, 1.sp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    DashedRule()
                    LeaderRow("INJECTIONS", r.moves.toString())
                    LeaderRow("UPTIME", formatTime(r.seconds))
                    LeaderRow("NODES REMOVED", r.nodes.toString())
                    LeaderRow("NEXUS-9 LOCKS", r.locks.toString())
                }
            }
            Spacer(Modifier.height(18.dp))
            if (game.canRollback) {
                CommandButton("ROLLBACK LAST MOVE", Modifier.fillMaxWidth(), color = C.Amber, trailing = "${game.charges} LEFT") { game.rollback() }
                Spacer(Modifier.height(10.dp))
            }
            CommandButton(
                when (r.mode) { Mode.SECTOR -> "RETRY SECTOR"; Mode.DAILY, Mode.WEEKLY -> "RETRY"; else -> "NEW SESSION" },
                Modifier.fillMaxWidth(), primary = true, height = 60.dp
            ) { game.restart() }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CommandButton("HOME", Modifier.weight(1f), color = C.Main, onClick = onHome)
                when (r.mode) {
                    Mode.SECTOR -> CommandButton("MAP", Modifier.weight(1f), color = C.Main, onClick = onMap)
                    Mode.DAILY, Mode.WEEKLY -> CommandButton("DIRECTIVES", Modifier.weight(1f), color = C.Main, onClick = onMap)
                    else -> CommandButton("SHARE", Modifier.weight(1f), color = C.Main, onClick = onShare)
                }
            }
        }
    }
}

/** The third bar of a sector, in words. */
fun lockGoal(budget: Int) = when (budget) {
    0 -> "NEVER LOCKED"
    1 -> "AT MOST 1 LOCK"
    else -> "AT MOST $budget LOCKS"
}

fun formatTime(sec: Int): String = if (sec >= 3600) "%d:%02d:%02d".format(java.util.Locale.ENGLISH, sec / 3600, sec / 60 % 60, sec % 60)
else "%d:%02d".format(java.util.Locale.ENGLISH, sec / 60, sec % 60)
