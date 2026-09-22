package com.fluidreader.app.drinklog

/** One individual logged pour - what "Log drink" records each time you tap it. */
data class DrinkPour(
    val ounces: Double,
    val loggedAtEpochMillis: Long,
)

/**
 * One named entry in tonight's log - e.g. every time you log a "Yuengling" pour, it's appended
 * to [pours] rather than creating a new top-level row each time. [totalOz], [pourCount], and
 * [lastLoggedAtEpochMillis] are derived from [pours] so they can never drift out of sync with
 * the actual pour history, which is what lets the Drink log screen break a name back down into
 * its individual pours on demand.
 */
data class DrinkLogEntry(
    val name: String,
    val pours: List<DrinkPour>,
) {
    val totalOz: Double get() = pours.sumOf { it.ounces }
    val pourCount: Int get() = pours.size
    val lastLoggedAtEpochMillis: Long get() = pours.maxOfOrNull { it.loggedAtEpochMillis } ?: 0L
}
