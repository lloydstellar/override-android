package nl.lloydstellar.override.engine

/**
 * The campaign: twelve sectors of the grid, each with its own twist on the
 * rules. Clearing a sector opens the next one. Three signal bars per sector:
 * one for the Override, one for making it within [par] moves, one for
 * keeping NEXUS-9 to at most [lockBudget] locks.
 *
 * The numbers (targets, trace speeds, par) were tuned with SectorBalanceSim,
 * a bot that plays every sector a few hundred times.
 */
class Sector(
    val number: Int,
    val name: String,
    /** One line on the map. */
    val twist: String,
    /** The briefing, shown before the sector starts. */
    val briefing: List<String>,
    val rules: Rules,
    val par: Int,
    val lockBudget: Int = 0,
    /** Campaign sector, or one of the generated directives (see Directives). */
    val kind: Kind = Kind.SECTOR,
    /** Where results are kept, and the short code on screen. */
    val id: String = "S" + number.toString().padStart(2, '0'),
    /** Directives use a fixed seed, so everybody gets the same spawns. */
    val seed: Int? = null,
) {
    enum class Kind { SECTOR, DAILY, WEEKLY }

    val code get() = id
}

private val noNodes = 1 shl 30

val SECTORS = listOf(
    Sector(
        1, "BOOTSTRAP", "Reach 256. No tricks yet.",
        listOf(
            "A dead relay station on the edge of the grid. NEXUS-9 stopped watching it years ago.",
            "Warm up the terminal: slide, fuse, build a 256-bit Neural Jammer.",
        ),
        CLASSIC.copy(target = 256, endless = false, corruptFrom = noNodes),
        par = 160,
    ),
    Sector(
        2, "TRACE ROUTE", "The trace climbs twice as fast.",
        listOf(
            "This sector is wired to the AI's own routers. Every idle move gets noticed.",
            "A move without a fusion raises the trace by 10\u00A0%. At 100\u00A0% NEXUS-9 seals a cell. Keep fusing.",
        ),
        CLASSIC.copy(target = 256, endless = false, corruptFrom = noNodes, traceEmpty = 10),
        par = 160,
        lockBudget = 2,
    ),
    Sector(
        3, "ROGUE CODE", "Corrupted Nodes from the first move.",
        listOf(
            "The AI seeded this grid with rogue code. Red nodes refuse to fuse upward.",
            "Slide an equal signal into a red node and both disappear, for twice its value.",
        ),
        CLASSIC.copy(target = 256, endless = false, corruptFrom = 2, corruptChance = 0.14),
        par = 240,
        lockBudget = 1,
    ),
    Sector(
        4, "EMP", "Starts with an EMP Cell. Nodes everywhere.",
        listOf(
            "We smuggled one EMP Cell into the matrix. It sits in the corner, charged.",
            "Any red node that ends up next to a 64 gets fried. Use it, and build a 256.",
        ),
        CLASSIC.copy(
            target = 256, endless = false, corruptFrom = 2, corruptChance = 0.16, corruptMax = 3,
            startTiles = listOf(StartTile(0, 3, 64)),
        ),
        par = 230,
        lockBudget = 1,
    ),
    Sector(
        5, "WIDE BAND", "A 5 x 5 matrix, crawling with nodes.",
        listOf(
            "An old broadcast tower with a wider bus. Twenty-five slots, and more noise on the line.",
            "More room, and more red nodes than anywhere else. Build a 512 before the noise takes over.",
        ),
        CLASSIC.copy(size = 5, target = 512, endless = false, corruptFrom = 8, corruptChance = 0.16, corruptMax = 4, traceEmpty = 3),
        par = 470,
        lockBudget = 1,
    ),
    Sector(
        6, "DEAD PIXEL", "One cell is burned out for good.",
        listOf(
            "A power surge burned a hole in the tube. One slot of the matrix is dead.",
            "Nothing slides through it. Fifteen cells, one 256.",
        ),
        CLASSIC.copy(target = 256, endless = false, deadCells = listOf(1 to 1)),
        par = 175,
    ),
    Sector(
        7, "SWARM", "Up to four nodes at once.",
        listOf(
            "The AI floods the grid with rogue code. Four red nodes at a time, from the start.",
            "Keep an EMP Cell close and your matrix clean. Target 256.",
        ),
        CLASSIC.copy(target = 256, endless = false, corruptFrom = 2, corruptChance = 0.25, corruptMax = 4),
        par = 330,
        lockBudget = 4,
    ),
    Sector(
        8, "LOCKDOWN", "Locks last twice as long.",
        listOf(
            "NEXUS-9 has a daemon on this sector. Its locks hold for ten moves, and it never forgets you.",
            "After a lock the trace only drops to 50\u00A0%. Fuse, or lose the board.",
        ),
        CLASSIC.copy(target = 512, endless = false, traceEmpty = 6, lockTurns = 10, traceAfterLock = 50),
        par = 340,
        lockBudget = 3,
    ),
    Sector(
        9, "BURST WINDOW", "512 within 380 moves.",
        listOf(
            "The satellite passes over in eleven minutes. After that the uplink is gone.",
            "Build a 512 before the window closes. Every move counts.",
        ),
        CLASSIC.copy(target = 512, endless = false, moveLimit = 380),
        par = 315,
        lockBudget = 1,
    ),
    Sector(
        10, "NARROW BAND", "A 3 x 3 matrix. Target 128.",
        listOf(
            "A field radio with nine valves. That is all the matrix there is.",
            "Plan every slide. A 128 on nine cells is harder than it sounds.",
        ),
        CLASSIC.copy(size = 3, target = 128, endless = false, corruptFrom = noNodes),
        par = 75,
    ),
    Sector(
        11, "BLACKOUT", "5 x 5 with three dead cells.",
        listOf(
            "Half the city is dark. Three slots of the wide matrix are burned out, and the AI is listening.",
            "Twenty-two cells, a fast trace, and a 1024 to build.",
        ),
        CLASSIC.copy(size = 5, target = 1024, endless = false, traceEmpty = 6, deadCells = listOf(2 to 0, 0 to 2, 4 to 3)),
        par = 700,
        lockBudget = 6,
    ),
    Sector(
        12, "NEXUS CORE", "The core itself. 2048, every rule at full strength.",
        listOf(
            "The processing core under the old studio district. Everything we learned ends here.",
            "Corrupted Nodes, a faster trace, longer locks. Assemble the Override Code and take the core down.",
        ),
        CLASSIC.copy(endless = false, traceAfterLock = 35, corruptChance = 0.08, lockTurns = 7),
        par = 1250,
        lockBudget = 6,
    ),
)
