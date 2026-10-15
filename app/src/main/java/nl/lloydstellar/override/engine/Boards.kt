package nl.lloydstellar.override.engine

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * A hand-made board in the format of [Engine.toJson], for the tutorial and
 * the demo boards. [rows] go top to bottom; 0 is empty and a negative value
 * is a Corrupted Node.
 */
fun boardJson(
    rows: List<List<Int>>,
    score: Int = 0,
    moves: Int = 0,
    trace: Int = 0,
    locks: List<Triple<Int, Int, Int>> = emptyList(),
    won: Boolean = false,
    keep: Boolean = false,
    rng: Int = 20481,
): JSONObject {
    val cells = JSONArray()
    var max = 0
    rows.forEachIndexed { y, row ->
        row.forEachIndexed { x, v ->
            if (v != 0) {
                cells.put(JSONArray().put(x).put(y).put(abs(v)).put(if (v < 0) 1 else 0))
                if (v > max) max = v
            }
        }
    }
    val ls = JSONArray()
    locks.forEach { (x, y, t) -> ls.put(JSONArray().put(x).put(y).put(t)) }
    return JSONObject()
        .put("cells", cells).put("locks", ls).put("score", score).put("moves", moves).put("trace", trace)
        .put("max", max).put("won", won).put("keep", keep).put("state", "PLAYING").put("end", "NONE")
        .put("rng", rng).put("id", 1).put("peakTrace", trace)
}
