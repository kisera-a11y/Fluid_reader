package com.fluidreader.app.drinklog

/**
 * One named running total in tonight's log - e.g. every time you log a "Yuengling" pour, its
 * [totalOz] grows and [pourCount] increments, rather than creating a new row each time.
 */
data class DrinkLogEntry(
    val name: String,
    val totalOz: Double,
    val pourCount: Int,
    val lastLoggedAtEpochMillis: Long,
)
