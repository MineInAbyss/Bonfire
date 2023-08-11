package com.mineinabyss.bonfire.listeners

import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.bonfire.components.BonfireCooldown
import com.mineinabyss.bonfire.components.BonfireRemoved
import com.mineinabyss.bonfire.components.BonfireRespawn
import com.mineinabyss.bonfire.extensions.isBonfire
import com.mineinabyss.bonfire.extensions.updateBonfireState
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.info
import com.mineinabyss.idofront.time.ticks
import kotlinx.coroutines.delay
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.type.Bed
import org.bukkit.entity.Boat
import org.bukkit.entity.ItemDisplay
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.*
import kotlin.time.Duration.Companion.seconds

class PlayerListener : Listener {

    @EventHandler(ignoreCancelled = true)
    fun PlayerBedEnterEvent.enter() {
        isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun PlayerInteractEvent.cancelBedRespawn() {
        if (clickedBlock?.blockData is Bed) setUseInteractedBlock(Event.Result.DENY)
    }

    @EventHandler
    fun PlayerRespawnEvent.onBonfireRespawn() {
        val bonfireRespawn = player.toGeary().get<BonfireRespawn>() ?: return
        val loc = bonfireRespawn.bonfireLocation

        fun respawnAtBonfire() {
            val bonfireEntity = loc.world.getEntity(bonfireRespawn.bonfireUuid) as? ItemDisplay ?: return
            val bonfireData = bonfireEntity.toGeary().get<Bonfire>() ?: return

            when {
                bonfireEntity.isBonfire && player.uniqueId in bonfireData.bonfirePlayers -> {
                    fun getHighestAirBlock(block: Block): Block {
                        return if (block.getRelative(BlockFace.UP).type.isAir || block == block.location.toHighestLocation().block) block
                        else getHighestAirBlock(block.getRelative(BlockFace.UP))
                    }

                    val height = loc.distance(getHighestAirBlock(loc.block).location)
                    loc.getNearbyEntities(0.5, height + 0.5, 0.5).filterIsInstance<Boat>().forEach(Boat::remove)

                    player.info(bonfire.messages.BONFIRE_RESPAWNING)
                    respawnLocation = loc.toCenterLocation()
                }
                else -> {
                    player.error(bonfire.messages.BONFIRE_NOT_FOUND)
                    player.toGeary().remove<BonfireRespawn>()
                }
            }
            bonfire.plugin.launch {
                delay(3.ticks)
                bonfireEntity.updateBonfireState()
            }
        }

        if (!loc.isChunkLoaded) player.teleportAsync(loc.toCenterLocation()).thenRun(::respawnAtBonfire)
        else respawnAtBonfire()
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun PlayerJoinEvent.onJoinRemovedBonfire() {
        val gearyPlayer = player.toGearyOrNull() ?: return
        if (!gearyPlayer.has<BonfireRemoved>()) return

        bonfire.plugin.launch {
            delay(1.seconds)
            player.error(bonfire.messages.BONFIRE_REMOVED)
            gearyPlayer.remove<BonfireRespawn>()
            gearyPlayer.remove<BonfireCooldown>()
            gearyPlayer.remove<BonfireRemoved>()
        }
    }

    @EventHandler
    fun PlayerJoinEvent.onPlayerJoin() {
        player.toGearyOrNull()?.remove<BonfireCooldown>()
        val bonfire = player.toGeary().get<BonfireRespawn>() ?: return
        val bonfireEntity = bonfire.bonfireLocation.world.getEntity(bonfire.bonfireUuid) as? ItemDisplay ?: return
        com.mineinabyss.bonfire.bonfire.plugin.launch {
            delay(3.ticks)
            bonfireEntity.updateBonfireState()
        }
    }
    @EventHandler
    fun PlayerQuitEvent.onPlayerQuit() {
        player.toGearyOrNull()?.remove<BonfireCooldown>()
    }
}
