package com.fluidreader.core.units

/** Conversions between the two volume units the app displays. */
object UnitConverter {

    /** 1 US fluid ounce, in milliliters. */
    const val ML_PER_US_FL_OZ: Double = 29.5735295625

    fun ozToMl(oz: Double): Double = oz * ML_PER_US_FL_OZ

    fun mlToOz(ml: Double): Double = ml / ML_PER_US_FL_OZ
}

enum class VolumeUnit {
    FLUID_OUNCES,
    MILLILITERS;

    val label: String get() = when (this) {
        FLUID_OUNCES -> "oz"
        MILLILITERS -> "mL"
    }

    /** Numeric value only, no unit suffix - e.g. "8.3" or "245". */
    fun formatValue(oz: Double): String = when (this) {
        FLUID_OUNCES -> String.format("%.1f", oz)
        MILLILITERS -> String.format("%.0f", UnitConverter.ozToMl(oz))
    }

    fun format(oz: Double): String = "${formatValue(oz)} $label"
}
