package com.mineinabyss.bonfire.extensions

import org.bukkit.Location

internal fun Location.findLocationAround(radius: Int, scale: Double, predicate: (Location) -> Boolean): Location? {
    for (x in -radius..radius) {
        for (z in -radius..radius) {
            val checkLoc = clone().add(x * scale, 0.0, z * scale)
            if (predicate(checkLoc))
                return checkLoc
        }
    }
    return null
}