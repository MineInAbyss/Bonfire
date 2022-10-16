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
        val blockIsBonfire = block.state is Campfire && (block.state as Campfire).isBonfire
        val corrIsBonfire = corr.state is Campfire && (corr.state as Campfire).isBonfire

        isCancelled = blockIsBonfire || corrIsBonfire || block.hasBonfireBelow() || corr.hasBonfireBelow()
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun BlockPlaceEvent.event() {
        val corr = block.location.correspondingLocation?.block ?: return

        val corrIsBonfire = corr.state is Campfire && (corr.state as Campfire).isBonfire

        isCancelled = corrIsBonfire || corr.hasBonfireBelow()
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun BlockBreakEvent.event() {
        val corr = block.location.correspondingLocation?.block ?: return
        val corrIsBonfire = corr.state is Campfire && (corr.state as Campfire).isBonfire

        isCancelled = corrIsBonfire
    }

}
