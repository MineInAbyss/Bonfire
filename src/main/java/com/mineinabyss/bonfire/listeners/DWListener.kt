package com.mineinabyss.bonfire.listeners

import com.derongan.minecraft.deeperworld.event.BlockSyncEvent
import com.derongan.minecraft.deeperworld.world.section.correspondingLocation
import com.mineinabyss.bonfire.extensions.isBonfire
import com.mineinabyss.bonfire.listeners.BlockListener.hasBonfireBelow
import org.bukkit.block.Block
import org.bukkit.block.Campfire
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockEvent
import org.bukkit.event.block.BlockPlaceEvent

object DWListener : Listener {

    @EventHandler
    fun BlockSyncEvent.event() {
        isCancelled = shouldCancel(block)
    }

    @EventHandler
    fun BlockPlaceEvent.event() {
        isCancelled = shouldCancel(block)
    }

    @EventHandler
    fun BlockBreakEvent.event() {
        isCancelled = shouldCancel(block)
    }

    fun BlockEvent.shouldCancel(block: Block): Boolean {
        val corr = block.location.correspondingLocation?.block ?: return false

        val blockIsBonfire = block.state is Campfire && (block.state as Campfire).isBonfire
        val corrIsBonfire = corr.state is Campfire && (corr.state as Campfire).isBonfire

        return blockIsBonfire || corrIsBonfire || block.hasBonfireBelow() || corr.hasBonfireBelow()
    }
}