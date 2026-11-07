package org.jeudego.pairgoth.store

/**
 * Naming of tournament history snapshots: `<id>-<short>.tour-<yyyyMMddHHmmss>-<NN>-<slug>`.
 *
 * NN is needed for sub-second sequential snapshots. Slug comes from [org.jeudego.pairgoth.server.Event.slug].
 */
data class HistorySnapshot(val filename: String, val timestamp: String, val seq: Int, val slug: String?) {
    /** Total-order key across same-second snapshots (lexicographic on this == chronological). */
    val order get() = "$timestamp-${"%02d".format(seq)}"
}

private const val TOUR_MARKER = ".tour-"
private val SNAPSHOT_SUFFIX = Regex("""^(\d{14})(?:-(\d{2})-(.*))?$""")

/** Parses a history snapshot filename, or null if it isn't one. */
fun parseHistorySnapshot(filename: String): HistorySnapshot? {
    val idx = filename.lastIndexOf(TOUR_MARKER)
    if (idx < 0) return null
    val m = SNAPSHOT_SUFFIX.matchEntire(filename.substring(idx + TOUR_MARKER.length)) ?: return null
    return HistorySnapshot(filename, m.groupValues[1], m.groupValues[2].toIntOrNull() ?: 0, m.groupValues[3].ifEmpty { null })
}

/** Builds the history snapshot filename for the current `.tour` [filename]. */
fun historySnapshotName(filename: String, timestamp: String, seq: Int, slug: String?) =
    "$filename-$timestamp-${"%02d".format(seq)}-${slug ?: "unknown"}"
