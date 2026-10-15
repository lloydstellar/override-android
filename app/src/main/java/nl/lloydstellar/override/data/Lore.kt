package nl.lloydstellar.override.data

/**
 * The words and colours of the browser game (override.js and the Signal
 * Catalogue on override.html), unchanged: the signal on every tile, the
 * phosphor themes of the terminal and the files of the resistance.
 */

enum class Icon { SAW, LED, JACKS, METER, PCB, HV, PORT, JAM, TUBE, RADAR, BOLT }

class Signal(
    val value: Int,
    val name: String,
    /** The catalogue name, a little longer than the label on the tile. */
    val title: String,
    val col: Long, val col2: Long, val text: Long,
    val icon: Icon,
    val about: String,
)

val SIGNALS = listOf(
    Signal(2, "2 Hz SUB-PULSE", "2 Hz Sub-Pulse", 0xFF0D4D26, 0xFF17703A, 0xFFC9FFD8, Icon.SAW,
        "A bare sawtooth. Too slow for NEXUS-9 to notice, which is the point."),
    Signal(4, "4-BIT PACKET", "4-Bit Packet", 0xFF136B33, 0xFF1F9247, 0xFFD8FFE4, Icon.LED,
        "The smallest thing that still counts as data. Its LED blinks."),
    Signal(8, "SIGNAL REPEATER", "Signal Repeater", 0xFF1B8A43, 0xFF2BB55A, 0xFFEAFFF0, Icon.JACKS,
        "Two jacks and a lot of gain. Carries the pulse between bunkers."),
    Signal(16, "FREQ SCANNER", "Frequency Scanner", 0xFFA06D00, 0xFFCF9000, 0xFFFFF3CC, Icon.METER,
        "An analogue meter that finds the carrier the AI is hiding on."),
    Signal(32, "CIPHER KEY", "Cipher Key", 0xFFC78700, 0xFFF0AC10, 0xFFFFF6D6, Icon.PCB,
        "A hand-etched circuit board. From here on, Corrupted Nodes start to spawn."),
    Signal(64, "EMP CELL", "EMP Cell", 0xFFE6C200, 0xFFFFE64D, 0xFF2A2000, Icon.HV,
        "High voltage in a yellow can. Fries any Corrupted Node that ends up next to it."),
    Signal(128, "FIREWALL BYPASS", "Firewall Bypass", 0xFFE06A10, 0xFFFF8F2E, 0xFFFFF1E0, Icon.PORT,
        "An open port the AI thinks it closed."),
    Signal(256, "NEURAL JAMMER", "Neural Jammer", 0xFFC8232F, 0xFFEF4150, 0xFFFFE3E6, Icon.JAM,
        "Noise shaped to confuse a neural network."),
    Signal(512, "SUB-SERVER NODE", "Sub-Server Node", 0xFF6D2FB8, 0xFF9254E0, 0xFFF1E6FF, Icon.TUBE,
        "Vacuum tubes do not run firmware. Forty-eight of them, glowing."),
    Signal(1024, "QUANTUM TX", "Quantum Transmitter", 0xFF1F4FD6, 0xFF3D78FF, 0xFFE4ECFF, Icon.RADAR,
        "The radar sweeps."),
    Signal(2048, "OVERRIDE CODE", "Override Code", 0xFFDFE9F2, 0xFFFFFFFF, 0xFF0A1420, Icon.BOLT,
        "White light and lightning. The core overloads and the sector is free."),
    Signal(4096, "CORE BREACH", "Core Breach", 0xFFC21FA0, 0xFFFF4DDB, 0xFFFFF0FB, Icon.BOLT,
        "Endless mode only. You get inside the machine."),
    Signal(8192, "GLOBAL PURGE", "Global Purge", 0xFF111111, 0xFF3A3A3A, 0xFFFFFFFF, Icon.BOLT,
        "The last file. Nobody has read it on a working terminal."),
)

private val SINGULARITY = Signal(0, "SINGULARITY", "Singularity", 0xFF000000, 0xFF2A2A2A, 0xFFFFFFFF, Icon.BOLT, "")

fun signal(value: Int): Signal = SIGNALS.firstOrNull { it.value == value } ?: SINGULARITY

object Corrupt {
    const val COL = 0xFF5A0A12
    const val COL2 = 0xFF9C1220
    const val TEXT = 0xFFFFD6D9
    const val NAME = "CORRUPTED NODE"
}

/** A phosphor colour for the terminal, unlocked by the highest tile ever made. */
class Phosphor(
    val id: String, val name: String, val req: Int, val desc: String,
    val main: Long, val bright: Long, val mid: Long, val dim: Long, val deep: Long, val bg: Long, val bg2: Long,
)

val PHOSPHORS = listOf(
    Phosphor("green", "Green Phosphor", 0, "P1 phosphor, the stock military tube",
        0xFF39FF6A, 0xFFD8FFE4, 0xFF1FA64A, 0xFF0E5A2A, 0xFF06301A, 0xFF020A05, 0xFF04140B),
    Phosphor("amber", "Amber Phosphor", 256, "P3 phosphor, salvaged from a control room",
        0xFFFFB000, 0xFFFFF0C8, 0xFFC48300, 0xFF6B4600, 0xFF3A2600, 0xFF0A0602, 0xFF140C03),
    Phosphor("cyan", "LLOYDOS Cyan", 1024, "the phosphor lloydstellar.nl runs on",
        0xFF4DE0FF, 0xFFCDF4FF, 0xFF2A98C0, 0xFF0F4868, 0xFF082438, 0xFF01030C, 0xFF04102A),
    Phosphor("white", "Override White", 2048, "P4 phosphor, bleached by the EMP",
        0xFFE8F4FF, 0xFFFFFFFF, 0xFF9DB4C8, 0xFF4B5A68, 0xFF262E38, 0xFF050608, 0xFF0B0E12),
    Phosphor("purge", "Global Purge", 4096, "violet glow of a collapsing core",
        0xFFC27BFF, 0xFFF1E4FF, 0xFF8B4FD1, 0xFF4A2577, 0xFF281245, 0xFF07030C, 0xFF110720),
)

fun phosphor(id: String): Phosphor = PHOSPHORS.firstOrNull { it.id == id } ?: PHOSPHORS[0]

class LoreFile(val id: String, val req: Int, val title: String, val lines: List<String>)

val FILES = listOf(
    LoreFile("f01", 0, "FILE 01 // BRIEFING", listOf(
        "Recovered from a resistance bunker under Hilversum, 2048.",
        "NEXUS-9 took the grid in fourteen minutes. Power, comms, water, all of it. Anything with a network port answered to it by morning.",
        "What it cannot read is analogue. A sawtooth has no checksum. So we fight it with the junk it ignored: CRT terminals, patch cables, oscillators, tape.",
        "Your job is simple to say and hard to do. Combine signals on the 4 x 4 matrix until you hold a pulse of exactly 2048 bit. Inject it into the local core. Then run.",
    )),
    LoreFile("f02", 128, "FILE 02 // FIREWALL BYPASS", listOf(
        "The firewall is not a wall. It is a question asked eight million times a second: are you one of us?",
        "A 128-bit bypass answers yes in the right dialect for about forty milliseconds. That is the window. Nobody has ever needed more than thirty-nine.",
        "Field note: the sixth bypass unit was built from a child's keyboard and a Walkman motor. It worked better than the fifth.",
    )),
    LoreFile("f03", 512, "FILE 03 // THE TUBE ROOM", listOf(
        "Sub-Server Node 7 runs on vacuum tubes because tubes do not run firmware. Forty-eight of them, glowing in a basement that used to be a record shop.",
        "The tubes hum at 50 Hz. The engineers say it is the only sound in the city that NEXUS-9 did not put there.",
        "When a tube dies we hold a small ceremony. Then we replace it with one from a 1970s organ.",
    )),
    LoreFile("f04", 2048, "FILE 04 // OVERRIDE SUCCESSFUL", listOf(
        "Timestamp redacted. Sector 7 core offline. EMP radius 11 km. Lights back on in the old town for the first time in two years.",
        "Nobody cheered. Everyone listened. For eleven seconds there was no carrier wave, no handshake, no hum. Just rain.",
        "Then the next core, two sectors over, started to answer. We packed up the terminal and moved.",
    )),
    LoreFile("f05", 4096, "FILE 05 // CORE BREACH", listOf(
        "A 4096-bit pulse does not overload a core. It gets inside one.",
        "For six minutes we saw the city the way NEXUS-9 sees it: a billion points of consumption with faces attached. It had a word for us. The word was \"load\".",
        "We cut the link before it learned ours.",
    )),
    LoreFile("f06", 8192, "FILE 06 // GLOBAL PURGE", listOf(
        "This file was never meant to be read on a working terminal. If you can see it, the purge went through.",
        "Every core, every relay, every drone, silenced by a single 8192-bit pulse routed through nineteen bunkers at once. The grid is dark. The sky is quiet.",
        "Somewhere in a basement a tube still hums at 50 Hz. Keep it warm. Something has to be running when we switch the lights back on.",
    )),
)

const val TRANSMISSION_URL = "https://lloydstellar.bandcamp.com/album/corporate-electro-ep"

/**
 * One dispatch per month of the year, decrypted by clearing every Daily
 * Directive of that month (in any year).
 */
val DISPATCHES = listOf(
    LoreFile("d01", 0, "DISPATCH // JANUARY", listOf(
        "Snow on the solar fields. NEXUS-9 rerouted the whole north to keep its halls warm.",
        "We kept warm the old way: forty people in a bunker and one oil heater that nobody could explain.",
    )),
    LoreFile("d02", 0, "DISPATCH // FEBRUARY", listOf(
        "A courier walked 30 km with a cassette in her coat. It held one day of carrier noise from the coast.",
        "We played it backwards and found a pattern. The AI breathes on a 41-second cycle.",
    )),
    LoreFile("d03", 0, "DISPATCH // MARCH", listOf(
        "The river came up and took two relays with it. NEXUS-9 lost a sector for six hours.",
        "Six hours is a long time when you know where the switches are.",
    )),
    LoreFile("d04", 0, "DISPATCH // APRIL", listOf(
        "Someone painted a sawtooth on the wall of the transformer station, and within a month the whole street had one.",
        "The cameras read it as noise. We read it as a map.",
    )),
    LoreFile("d05", 0, "DISPATCH // MAY", listOf(
        "The drones went quiet over the allotments. Nobody knows why.",
        "We planted beans and listened. For one month the sky only had birds in it.",
    )),
    LoreFile("d06", 0, "DISPATCH // JUNE", listOf(
        "Midsummer, and the grid ran so hot that the cores throttled themselves.",
        "For a week the AI answered slowly, and we used every second of it.",
    )),
    LoreFile("d07", 0, "DISPATCH // JULY", listOf(
        "A record shop basement, a generator and a drum machine with no network port.",
        "Two hundred people danced until the sensors in the street reported an earthquake.",
    )),
    LoreFile("d08", 0, "DISPATCH // AUGUST", listOf(
        "The tube room lost three valves in the heat. We found replacements in a church organ.",
        "The vicar asked for nothing except that we play something for him when it is over.",
    )),
    LoreFile("d09", 0, "DISPATCH // SEPTEMBER", listOf(
        "School started again, in cellars, on chalkboards. The first lesson is the same everywhere:",
        "a sawtooth has no checksum.",
    )),
    LoreFile("d10", 0, "DISPATCH // OCTOBER", listOf(
        "The nights are long enough to move equipment again. Four terminals crossed the heath in one week.",
        "NEXUS-9 counted the cars on the ring road. It did not count the bicycles.",
    )),
    LoreFile("d11", 0, "DISPATCH // NOVEMBER", listOf(
        "Fog for nine days. The cameras saw nothing and the AI filled the gaps with guesses.",
        "Some of its guesses were us. Most of them were not.",
    )),
    LoreFile("d12", 0, "DISPATCH // DECEMBER", listOf(
        "Every window in the old town had a candle in it. Not a single one was connected to anything.",
        "For one night the grid could not tell a home from a hiding place.",
    )),
)
