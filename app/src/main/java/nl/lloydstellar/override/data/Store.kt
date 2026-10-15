package nl.lloydstellar.override.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import nl.lloydstellar.override.engine.SECTORS
import org.json.JSONArray
import org.json.JSONObject

/** One finished Classic session on this phone. */
data class Run(
    val score: Int,
    val tile: Int,
    val time: Long,         /* epoch millis */
    val moves: Int,
    val seconds: Int,
    val won: Boolean,
    /** Threat level: -1 calm, 0 standard, 1..8 */
    val level: Int = 0,
)

class Achievement(val id: String, val title: String, val text: String)

val ACHIEVEMENTS = listOf(
    Achievement("first", "Jacked In", "Play your first session."),
    Achievement("t128", "Firewall Down", "Build a 128 Firewall Bypass."),
    Achievement("t512", "The Tube Room", "Build a 512 Sub-Server Node."),
    Achievement("t1024", "Radar Sweep", "Build a 1024 Quantum Transmitter."),
    Achievement("override", "System Override", "Assemble the 2048 Override Code in Classic."),
    Achievement("t4096", "Core Breach", "Build a 4096 in Global Purge mode."),
    Achievement("t8192", "Global Purge", "Build an 8192. Nobody has."),
    Achievement("clean", "Clean Hands", "Override in Classic without a rollback."),
    Achievement("cold", "Ghost in the Wire", "Override in Classic with three NEXUS-9 locks or fewer."),
    Achievement("fast", "Burst Transmission", "Override in Classic in under 1000 moves."),
    Achievement("purge", "Antivirus", "Purge a Corrupted Node with an equal signal."),
    Achievement("fry", "Fried", "Let an EMP Cell fry a Corrupted Node."),
    Achievement("double", "Double Discharge", "Fry two Corrupted Nodes in one move."),
    Achievement("nodes50", "Exterminator", "Remove 50 Corrupted Nodes in total."),
    Achievement("multiplex", "Multiplex", "Make four fusions in one move."),
    Achievement("siege", "Under Siege", "Get locked three times in one session and still build a 512."),
    Achievement("hz20k", "Ultrasonic", "Reach 20,000 Hz in one session."),
    Achievement("sectors", "Liberator", "Clear all twelve sectors."),
    Achievement("bars", "Full Signal", "Earn all three bars in every sector."),
    Achievement("games25", "Night Shift", "Play 25 sessions."),
    Achievement("threat4", "Hardline", "Override in Classic at Threat 4."),
    Achievement("threat8", "Full Alert", "Override in Classic at Threat 8."),
    Achievement("daily10", "Daily Operative", "Clear ten Daily Directives."),
    Achievement("month", "Full Calendar", "Clear every Daily Directive of a month."),
    Achievement("weekly4", "Long Haul", "Clear four Weekly Directives."),
)

/**
 * Everything the app remembers, all local on the phone (no network, no
 * accounts). Compose reads the observable fields directly.
 */
class Store(ctx: Context) {
    private val prefs: SharedPreferences = ctx.getSharedPreferences("override", Context.MODE_PRIVATE)

    /* ---------------- settings ---------------- */
    var sound by mutableStateOf(prefs.getBoolean("sound", true)); private set
    var music by mutableStateOf(prefs.getBoolean("music", true)); private set
    var haptics by mutableStateOf(prefs.getBoolean("haptics", true)); private set
    /** Screen shake, static and flashes. Off when the system asks for less motion. */
    var effects by mutableStateOf(prefs.getBoolean("effects", true)); private set
    var phosphorId by mutableStateOf(prefs.getString("theme", "green") ?: "green"); private set

    fun changeSound(v: Boolean) { sound = v; prefs.edit { putBoolean("sound", v) } }
    fun changeMusic(v: Boolean) { music = v; prefs.edit { putBoolean("music", v) } }
    fun changeHaptics(v: Boolean) { haptics = v; prefs.edit { putBoolean("haptics", v) } }
    fun changeEffects(v: Boolean) { effects = v; prefs.edit { putBoolean("effects", v) } }
    fun changePhosphor(id: String) {
        if (phosphor(id).req > bestTile) return
        phosphorId = id
        prefs.edit { putString("theme", id) }
    }
    val phosphor: Phosphor get() = phosphor(phosphorId).takeIf { it.req <= bestTile } ?: PHOSPHORS[0]

    /* one-time hints and the briefing */
    var hints by mutableStateOf(prefs.getStringSet("hints", emptySet())!!.toSet()); private set
    fun hintSeen(id: String) = id in hints
    fun markHint(id: String) {
        if (id in hints) return
        hints = hints + id
        prefs.edit { putStringSet("hints", hints) }
    }

    /* ---------------- progress (any mode) ---------------- */
    /** Highest tile ever made: unlocks phosphors and files, like the browser. */
    var bestTile by mutableIntStateOf(prefs.getInt("bestTile", 0)); private set
    var overrides by mutableIntStateOf(prefs.getInt("overrides", 0)); private set
    var games by mutableIntStateOf(prefs.getInt("games", 0)); private set

    /** Returns true when [tile] is a new best (so something may have unlocked). */
    fun reachTile(tile: Int): Boolean {
        if (tile <= bestTile) return false
        bestTile = tile
        prefs.edit { putInt("bestTile", tile) }
        return true
    }

    fun countGame() { games++; prefs.edit { putInt("games", games) } }

    /* ---------------- Classic records ---------------- */
    var runs by mutableStateOf(loadRuns()); private set
    /** Best Classic score at a threat level. */
    fun bestFor(level: Int): Int = runs.filter { it.level == level }.maxOfOrNull { it.score } ?: 0
    val best: Int get() = bestFor(lastLevel)

    var totalScore by mutableStateOf(prefs.getLong("totalScore", 0)); private set
    var fusions by mutableIntStateOf(prefs.getInt("fusions", 0)); private set
    var nodes by mutableIntStateOf(prefs.getInt("nodes", 0)); private set
    var locks by mutableIntStateOf(prefs.getInt("locks", 0)); private set
    var rollbacks by mutableIntStateOf(prefs.getInt("rollbacks", 0)); private set
    var moves by mutableIntStateOf(prefs.getInt("moves", 0)); private set
    var seconds by mutableStateOf(prefs.getLong("seconds", 0)); private set
    var fastestOverride by mutableIntStateOf(prefs.getInt("fastest", 0)); private set

    /** Adds a finished Classic session; returns its rank among the top 20 of its level (1-based) or 0. */
    fun addRun(run: Run): Int {
        val list = (runs + run).sortedWith(compareByDescending<Run> { it.score }.thenBy { it.time })
            .groupBy { it.level }.values.flatMap { it.take(20) }
            .sortedWith(compareByDescending<Run> { it.score }.thenBy { it.time })
        runs = list
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("s", it.score).put("t", it.tile).put("d", it.time)
                .put("m", it.moves).put("sec", it.seconds).put("w", it.won).put("l", it.level))
        }
        prefs.edit { putString("runs", arr.toString()) }
        val idx = list.filter { it.level == run.level }.indexOf(run)
        return if (idx >= 0) idx + 1 else 0
    }

    /* ---------------- threat levels ---------------- */
    /** The level a new session starts at. */
    var lastLevel by mutableIntStateOf(prefs.getInt("level", 0)); private set
    /** Bit per level (level + 1) at which the Override was made. */
    var threatWins by mutableIntStateOf(prefs.getInt("threatWins", 0)); private set

    fun chooseLevel(level: Int) { lastLevel = level; prefs.edit { putInt("level", level) } }
    fun threatOpen(level: Int) = level <= 0 || threatWins and (1 shl level) != 0
    fun overrideAt(level: Int) = threatWins and (1 shl (level + 1)) != 0

    /** An Override at [level]; opens the next rung. */
    fun winThreat(level: Int) {
        threatWins = threatWins or (1 shl (level + 1))
        prefs.edit { putInt("threatWins", threatWins) }
    }

    /** Lifetime counters, for every mode. */
    fun addStats(score: Int, fusions: Int, nodes: Int, locks: Int, moves: Int, seconds: Int) {
        totalScore += score
        this.fusions += fusions
        this.nodes += nodes
        this.locks += locks
        this.moves += moves
        this.seconds += seconds
        prefs.edit {
            putLong("totalScore", totalScore).putInt("fusions", this@Store.fusions).putInt("nodes", this@Store.nodes)
            putInt("locks", this@Store.locks).putInt("moves", this@Store.moves).putLong("seconds", this@Store.seconds)
        }
    }

    fun countRollback() { rollbacks++; prefs.edit { putInt("rollbacks", rollbacks) } }

    fun countOverride(moves: Int) {
        overrides++
        if (fastestOverride == 0 || moves < fastestOverride) fastestOverride = moves
        prefs.edit { putInt("overrides", overrides).putInt("fastest", fastestOverride) }
    }

    /* ---------------- sectors ---------------- */
    /** Bars earned per sector (0 = not cleared), index 0 = sector 1. */
    var bars by mutableStateOf(loadInts("bars")); private set
    var sectorBest by mutableStateOf(loadInts("sectorBest")); private set

    fun sectorOpen(number: Int) = number == 1 || bars[number - 2] > 0

    /** Records a cleared sector; returns true when the bars improved. */
    fun clearSector(number: Int, earned: Int, moves: Int): Boolean {
        val i = number - 1
        val better = earned > bars[i]
        resetFails(number)
        if (better) bars = bars.copyOf().also { it[i] = earned }
        if (sectorBest[i] == 0 || moves < sectorBest[i]) sectorBest = sectorBest.copyOf().also { it[i] = moves }
        prefs.edit { putString("bars", bars.joinToString(",")).putString("sectorBest", sectorBest.joinToString(",")) }
        return better
    }

    /** Lost attempts per sector since the last clear: after three, the briefing offers an assist. */
    var sectorFails by mutableStateOf(loadInts("fails")); private set
    fun failSector(number: Int) {
        sectorFails = sectorFails.copyOf().also { it[number - 1]++ }
        prefs.edit { putString("fails", sectorFails.joinToString(",")) }
    }
    private fun resetFails(number: Int) {
        if (sectorFails[number - 1] == 0) return
        sectorFails = sectorFails.copyOf().also { it[number - 1] = 0 }
        prefs.edit { putString("fails", sectorFails.joinToString(",")) }
    }

    /* ---------------- directives ---------------- */
    /** Bars and best moves per directive id (D2026-10-15, W2026-42). */
    var directives by mutableStateOf(loadDirectives()); private set
    /** The first day this app was opened: the calendar starts there. */
    val firstDay: Long = prefs.getLong("firstDay", -1).takeIf { it >= 0 }
        ?: java.time.LocalDate.now().toEpochDay().also { d -> prefs.edit { putLong("firstDay", d) } }

    fun directiveBars(id: String) = directives[id]?.first ?: 0
    fun directiveBest(id: String) = directives[id]?.second ?: 0

    /** Records a cleared directive; returns true when the bars improved. */
    fun clearDirective(id: String, earned: Int, moves: Int): Boolean {
        val (oldBars, oldMoves) = directives[id] ?: (0 to 0)
        val better = earned > oldBars
        directives = directives + (id to (maxOf(earned, oldBars) to (if (oldMoves == 0) moves else minOf(oldMoves, moves))))
        val o = JSONObject()
        directives.forEach { (k, v) -> o.put(k, JSONArray().put(v.first).put(v.second)) }
        prefs.edit { putString("directives", o.toString()) }
        return better
    }

    val dailiesCleared get() = directives.count { it.key.startsWith("D") && it.value.first > 0 }
    val weekliesCleared get() = directives.count { it.key.startsWith("W") && it.value.first > 0 }

    /** True when every daily of the month of [day] has at least one bar. */
    fun monthComplete(day: java.time.LocalDate): Boolean {
        val first = day.withDayOfMonth(1)
        return (0 until first.lengthOfMonth()).all { directiveBars("D" + first.plusDays(it.toLong())) > 0 }
    }

    /** Months of the year (1..12) whose dispatch is decrypted: every daily of such a month cleared, in any year. */
    fun dispatchMonths(): Set<Int> {
        val out = HashSet<Int>()
        var m = java.time.LocalDate.ofEpochDay(firstDay).withDayOfMonth(1)
        val now = java.time.LocalDate.now()
        while (!m.isAfter(now)) {
            if (monthComplete(m)) out.add(m.monthValue)
            m = m.plusMonths(1)
        }
        return out
    }

    /* ---------------- achievements ---------------- */
    var unlocked by mutableStateOf(loadUnlocked()); private set
    fun isUnlocked(id: String) = unlocked.containsKey(id)

    /** Returns the achievement if this call unlocked it. */
    fun unlock(id: String): Achievement? {
        if (unlocked.containsKey(id)) return null
        val a = ACHIEVEMENTS.firstOrNull { it.id == id } ?: return null
        unlocked = unlocked + (id to System.currentTimeMillis())
        val o = JSONObject()
        unlocked.forEach { (k, v) -> o.put(k, v) }
        prefs.edit { putString("unlocked", o.toString()) }
        return a
    }

    /* ---------------- saved games, one per mode ---------------- */
    var savedClassic by mutableStateOf(loadJson("save.classic")); private set
    var savedSector by mutableStateOf(loadJson("save.sector")); private set
    var savedDaily by mutableStateOf(loadJson("save.daily")); private set
    var savedWeekly by mutableStateOf(loadJson("save.weekly")); private set

    fun saveClassic(o: JSONObject?) { savedClassic = o; putJson("save.classic", o) }
    fun saveSector(o: JSONObject?) { savedSector = o; putJson("save.sector", o) }
    fun saveDaily(o: JSONObject?) { savedDaily = o; putJson("save.daily", o) }
    fun saveWeekly(o: JSONObject?) { savedWeekly = o; putJson("save.weekly", o) }

    fun resetRecords() {
        val keep = setOf("sound", "music", "haptics", "effects", "hints", "firstDay")
        prefs.edit { prefs.all.keys.filter { it !in keep }.forEach { remove(it) } }
        runs = emptyList()
        bestTile = 0; overrides = 0; games = 0
        totalScore = 0; fusions = 0; nodes = 0; locks = 0; rollbacks = 0; moves = 0; seconds = 0; fastestOverride = 0
        bars = IntArray(SECTORS.size); sectorBest = IntArray(SECTORS.size); sectorFails = IntArray(SECTORS.size)
        lastLevel = 0; threatWins = 0
        directives = emptyMap()
        unlocked = emptyMap()
        savedClassic = null; savedSector = null; savedDaily = null; savedWeekly = null
        phosphorId = "green"
    }

    /* ---------------- loading ---------------- */

    private fun putJson(key: String, o: JSONObject?) = prefs.edit {
        if (o == null) remove(key) else putString(key, o.toString())
    }

    private fun loadJson(key: String): JSONObject? =
        prefs.getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun loadInts(key: String): IntArray {
        val out = IntArray(SECTORS.size)
        prefs.getString(key, null)?.split(',')?.forEachIndexed { i, s -> if (i < out.size) out[i] = s.toIntOrNull() ?: 0 }
        return out
    }

    private fun loadRuns(): List<Run> = runCatching {
        val arr = JSONArray(prefs.getString("runs", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Run(o.getInt("s"), o.getInt("t"), o.getLong("d"), o.optInt("m"), o.optInt("sec"), o.optBoolean("w"), o.optInt("l"))
        }
    }.getOrDefault(emptyList())

    private fun loadDirectives(): Map<String, Pair<Int, Int>> = runCatching {
        val o = JSONObject(prefs.getString("directives", "{}")!!)
        o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { it.getInt(0) to it.getInt(1) } }
    }.getOrDefault(emptyMap())

    private fun loadUnlocked(): Map<String, Long> = runCatching {
        val o = JSONObject(prefs.getString("unlocked", "{}")!!)
        o.keys().asSequence().associateWith { o.getLong(it) }
    }.getOrDefault(emptyMap())
}
