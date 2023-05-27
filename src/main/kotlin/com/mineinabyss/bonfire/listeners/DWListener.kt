package com.mineinabyss.bonfire.listeners

import com.mineinabyss.bonfire.extensions.isBonfire
import com.mineinabyss.bonfire.listeners.BlockListener.hasBonfireBelow
import com.mineinabyss.deeperworld.event.BlockSyncEvent
import com.mineinabyss.deeperworld.world.section.correspondingLocation
import org.bukkit.block.Campfire
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent

object DWListener : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun BlockSyncEvent.event() {
        val corr = block.location.correspondingLocation?.block ?: return
        if ((block.state as? Campfire)?.isBonfire == true ||
            (corr.state as? Campfire)?.isBonfire == true ||
            block.hasBonfireBelow() || corr.hasBonfireBelow())
            isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun BlockPlaceEvent.event() {
        val corr = block.location.correspondingLocation?.block ?: return
        if ((corr.state as? Campfire)?.isBonfire == true || corr.hasBonfireBelow())
            isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun BlockBreakEvent.event() {
        val corr = block.location.correspondingLocation?.block ?: return
        if ((corr.state as? Campfire)?.isBonfire == true || corr.hasBonfireBelow())
            isCancelled = true
    }

}
