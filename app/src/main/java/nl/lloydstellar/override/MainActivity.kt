package nl.lloydstellar.override

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import nl.lloydstellar.override.audio.Haptics
import nl.lloydstellar.override.audio.Music
import nl.lloydstellar.override.audio.Sfx
import nl.lloydstellar.override.data.Store
import nl.lloydstellar.override.data.signal
import nl.lloydstellar.override.render.Sprites
import nl.lloydstellar.override.ui.AboutScreen
import nl.lloydstellar.override.ui.ArchiveScreen
import nl.lloydstellar.override.ui.C
import nl.lloydstellar.override.ui.DirectivesScreen
import nl.lloydstellar.override.ui.GameScreen
import nl.lloydstellar.override.ui.HomeScreen
import nl.lloydstellar.override.ui.IntroScreen
import nl.lloydstellar.override.ui.OverrideTheme
import nl.lloydstellar.override.ui.RecordsScreen
import nl.lloydstellar.override.ui.SectorsScreen
import nl.lloydstellar.override.ui.SettingsScreen

/* process-wide singletons: one store, one game, one set of tile sprites for the menus */
object App {
    lateinit var store: Store
    lateinit var game: GameController
    lateinit var sprites: Sprites
    fun init(ctx: android.content.Context) {
        if (::store.isInitialized) return
        store = Store(ctx.applicationContext)
        game = GameController(store)
        val mono = ResourcesCompat.getFont(ctx, R.font.share_tech_mono) ?: android.graphics.Typeface.MONOSPACE
        val display = ResourcesCompat.getFont(ctx, R.font.orbitron) ?: android.graphics.Typeface.DEFAULT_BOLD
        sprites = Sprites(mono, display)
    }
}

enum class Screen { INTRO, HOME, GAME, SECTORS, DIRECTIVES, ARCHIVE, RECORDS, SETTINGS, ABOUT }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        App.init(this)
        Haptics.init(this)
        Sfx.loadVoice(this)
        Music.install()

        setContent {
            OverrideTheme(App.store.phosphor) { Root() }
        }
    }

    override fun onResume() {
        super.onResume()
        Sfx.start()
        App.game.updateMusic()
    }

    override fun onPause() {
        App.game.saveIfRunning()
        Sfx.stop()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun open(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } catch (_: Exception) { }
    }

    private fun share(r: Result) {
        val s = r.sector
        if (s != null && s.kind != nl.lloydstellar.override.engine.Sector.Kind.SECTOR) {
            val bars = "■".repeat(r.bars) + "□".repeat(3 - r.bars)
            val what = if (s.kind == nl.lloydstellar.override.engine.Sector.Kind.WEEKLY) "Weekly Directive ${s.id.removePrefix("W")}"
            else "Daily Directive ${s.id.removePrefix("D")}"
            send("2048 Override // $what // ${s.name.substringAfter("// ")}\n$bars in ${r.moves} moves\nlloydstellar.nl/override.html")
            return
        }
        val body = "I reached ${"%,d".format(java.util.Locale.ENGLISH, r.score)} Hz and built a ${r.tile} ${signal(r.tile).title} in 2048 Override. " +
            "Fight NEXUS-9 yourself: lloydstellar.nl/override.html"
        send(body)
    }

    private fun send(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, "Share your result"))
    }

    @Composable
    private fun Root() {
        val store = App.store
        val game = App.game
        var screen by rememberSaveable { mutableStateOf(if (store.hintSeen("intro")) Screen.HOME else Screen.INTRO) }
        /* which save slot the game screen belongs to, for a restart after process death */
        var playing by rememberSaveable { mutableStateOf(Mode.CLASSIC) }
        fun play(mode: Mode) { playing = mode; screen = Screen.GAME }
        /* new players get the three-step tutorial before their first session */
        fun newClassic(level: Int = store.lastLevel) {
            if (store.hintSeen("tutorial")) game.newClassic(level) else game.startTutorial()
            play(if (store.hintSeen("tutorial")) Mode.CLASSIC else Mode.TUTORIAL)
        }
        fun tutorialDone() {
            store.markHint("tutorial")
            store.markHint("swipe")
            game.newClassic()
            play(Mode.CLASSIC)
        }

        /* debug builds: start straight into a demo board (see Demo) */
        LaunchedEffect(Unit) {
            val demo = if (BuildConfig.DEBUG) intent.getStringExtra("demo") else null
            val sector = demo?.removePrefix("sector")?.toIntOrNull()?.let { n -> nl.lloydstellar.override.engine.SECTORS.getOrNull(n - 1) }
            if (sector != null) {
                store.markHint("intro")
                game.newSector(sector, false)
                play(Mode.SECTOR)
            } else if (demo != null) {
                store.markHint("intro"); store.markHint("tutorial")
                val mode = Demo.load(demo, store)
                if (mode != null && game.continueSaved(mode)) {
                    store.markHint("intro")
                    game.resume()
                    play(mode)
                }
            }
        }

        /* after a process restart the game screen has no game in memory: pick up the saved one */
        LaunchedEffect(Unit) {
            if (screen == Screen.GAME && game.engine.moves == 0 && game.engine.tiles().isEmpty()) {
                if (!game.continueSaved(playing)) screen = Screen.HOME
            }
        }
        LaunchedEffect(store.sound) { Sfx.enabled = store.sound; game.updateMusic() }
        LaunchedEffect(store.haptics) { Haptics.enabled = store.haptics }
        C.phosphor = store.phosphor

        if (screen != Screen.HOME && screen != Screen.GAME && screen != Screen.INTRO) BackHandler { screen = Screen.HOME }

        fun home() { game.leave(); screen = Screen.HOME }

        Box(Modifier.fillMaxSize().background(C.Void)) {
            AnimatedContent(
                screen,
                transitionSpec = {
                    (fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 0.97f)) togetherWith
                        (fadeOut(tween(200)) + scaleOut(tween(260), targetScale = 1.02f))
                },
                label = "screen"
            ) { sc ->
                when (sc) {
                    Screen.INTRO -> IntroScreen {
                        val first = !store.hintSeen("intro")
                        store.markHint("intro")
                        if (first && !store.hintSeen("tutorial")) newClassic() else screen = Screen.HOME
                    }
                    Screen.HOME -> HomeScreen(
                        store,
                        onContinue = { if (game.continueSaved(Mode.CLASSIC)) play(Mode.CLASSIC) },
                        onStart = { newClassic(it) },
                        onSectors = { screen = Screen.SECTORS },
                        onDirectives = { screen = Screen.DIRECTIVES },
                        onRecords = { screen = Screen.RECORDS },
                        onArchive = { screen = Screen.ARCHIVE },
                        onSettings = { screen = Screen.SETTINGS },
                        onAbout = { screen = Screen.ABOUT },
                        onWebsite = { open("https://lloydstellar.nl/") },
                    )
                    Screen.GAME -> GameScreen(
                        game, store,
                        onHome = { home() },
                        onMap = {
                            val directive = game.mode == Mode.DAILY || game.mode == Mode.WEEKLY
                            game.leave()
                            screen = if (directive) Screen.DIRECTIVES else Screen.SECTORS
                        },
                        onShare = { share(it) },
                        onTutorialDone = { tutorialDone() },
                    )
                    Screen.SECTORS -> SectorsScreen(
                        store,
                        onBack = { screen = Screen.HOME },
                        onStart = { s, assist -> game.newSector(s, assist); play(Mode.SECTOR) },
                        onContinue = { if (game.continueSaved(Mode.SECTOR)) play(Mode.SECTOR) },
                    )
                    Screen.DIRECTIVES -> DirectivesScreen(
                        store,
                        onBack = { screen = Screen.HOME },
                        onStart = { s, date ->
                            game.newDirective(s, date)
                            play(if (s.kind == nl.lloydstellar.override.engine.Sector.Kind.WEEKLY) Mode.WEEKLY else Mode.DAILY)
                        },
                        onContinue = { mode -> if (game.continueSaved(mode)) play(mode) },
                    )
                    Screen.ARCHIVE -> ArchiveScreen(store, onBack = { screen = Screen.HOME }, onOpen = { open(it) }, onBriefing = { screen = Screen.INTRO })
                    Screen.RECORDS -> RecordsScreen(store) { screen = Screen.HOME }
                    Screen.SETTINGS -> SettingsScreen(
                        store, onBack = { screen = Screen.HOME }, onChanged = { game.updateMusic() },
                        onTutorial = { game.startTutorial(); play(Mode.TUTORIAL) },
                    )
                    Screen.ABOUT -> AboutScreen(onBack = { screen = Screen.HOME }, onOpen = { open(it) })
                }
            }
        }
    }
}
