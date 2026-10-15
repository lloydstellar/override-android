package nl.lloydstellar.override.engine

/**
 * The Deep Grid: sector 13, 14, 15 and on without end, below the core of
 * sector 12. Each sector stacks twists of the directives, the special blocks
 * among them, on Classic's rules, and they get harder with depth until they
 * level off:
 *
 *   sector 13 to 17   a 1024 with two twists
 *   sector 18 to 27   a 1024 with three twists, and locks that pin
 *   sector 28 to 37   the 2048 with two twists
 *   sector 38 and on  the 2048 with three twists
 *
 * Within a stage the twists are drawn so their factors (moves to the target
 * compared with Classic) land in a band, which keeps a stage even: no free
 * sector after a brutal one. The rules of a sector come from its number, so
 * every player meets the same sector 27; the spawns are random, as in the
 * campaign, so a retry is a new game. DeepGridSim plays them with the bots.
 */
object DeepGrid {
    const val FIRST = 13

    private class Stage(val from: Int, val target: Int, val twists: Int, val min: Double, val max: Double, val pins: Boolean)

    private val STAGES = listOf(
        Stage(13, 1024, 2, 1.2, 1.9, false),
        Stage(18, 1024, 3, 1.4, 2.3, true),
        Stage(28, 2048, 2, 1.1, 1.6, true),
        Stage(38, 2048, 3, 1.3, 1.9, true),
    )

    /* twists that rule each other out: one matrix size, one kind of dead cells, one source of red nodes,
       and no move limit while capacitors keep wiping the board */
    private val CLASHES = listOf(setOf("pixel", "blackout"), setOf("rogue", "swarm", "emp"), setOf("burst", "surge"))
    /* burned-out cells only on the 5 x 5: on a 4 x 4 they leave too little room for a 1024 or a 2048 */
    private val DEAD = setOf("pixel", "blackout")

    private val ADJECTIVES = listOf(
        "COLD", "BLACK", "HOLLOW", "IRON", "SUNKEN", "BURIED", "FROZEN", "MUTE",
        "BLIND", "RUSTED", "PALE", "BROKEN", "LOW", "LAST", "SEALED", "DRY",
    )
    private val NOUNS = listOf(
        "VAULT", "RELAY", "ROOT", "SHAFT", "CELLAR", "TUNNEL", "BUNKER", "SPOOL",
        "CONDUIT", "CISTERN", "FURNACE", "GATE", "LOOP", "MIRROR", "KERNEL", "WELL",
    )

    private val PLACES = listOf(
        "The core had a basement. Nobody on our side knew.",
        "Old copper cables, still warm. NEXUS-9 keeps a backup down here.",
        "A cooling tunnel under the river. The fans run on a schedule nobody wrote.",
        "Test servers from before the AI. It found them before we did.",
        "A bunker from the cold war, full of blinking racks.",
        "The deeper we dig, the older the code. Some of it is COBOL.",
        "Down here NEXUS-9 keeps its mistakes. They still run.",
        "A metro line that never opened. The signals in its tunnels are not trains.",
        "Every level down, the trace answers faster. It knows we are coming.",
        "A vault of tape drives, spinning. Every reel is a copy of NEXUS-9.",
        "The air smells of ozone and old dust. Somewhere a relay clicks.",
        "Our map ends here. From now on we draw it ourselves.",
    )

    /** How far below sector 12: 1 for sector 13. */
    fun depth(n: Int) = n - FIRST + 1

    fun name(n: Int): String {
        val i = n - FIRST
        return ADJECTIVES[i % 16] + " " + NOUNS[(i / 16 + i * 5) % 16]
    }

    /** Sector [n] of the Deep Grid, 13 or more. */
    fun sector(n: Int): Sector {
        require(n >= FIRST)
        val stage = STAGES.last { n >= it.from }
        val g = Rng(Directives.mix(n * 7919 + 4242))
        val pool = Directives.TWISTS.filter { it.id != "narrow" }
        var twists: List<Directives.Twist>
        while (true) {
            val pick = ArrayList<Directives.Twist>()
            while (pick.size < stage.twists) {
                val t = pool[g.below(pool.size)]
                if (t !in pick) pick.add(t)
            }
            val ids = pick.map { it.id }.toSet()
            val factor = pick.fold(1.0) { f, t -> f * t.deepFactor }
            val fits = pick.count { it.size != 0 } <= 1 && CLASHES.none { (it intersect ids).size > 1 } &&
                (ids.none { it in DEAD } || "wide" in ids) && factor in stage.min..stage.max
            if (fits) { twists = pick.sortedBy { if (it.size != 0) 0 else 1 }; break }
        }
        val place = PLACES[g.below(PLACES.size)]
        val base = if (stage.pins) CLASSIC.copy(pinChance = PIN_CHANCE) else CLASSIC
        return Directives.build(
            Sector.Kind.DEEP, "S$n", name(n), twists, stage.target, place, Rng(Directives.mix(n * 31 + 99)), null,
            number = n, base = base, deep = true,
        )
    }
}
