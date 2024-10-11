package com.mineinabyss.bonfire.listeners

import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.bonfire.components.BonfireRemoved
import com.mineinabyss.bonfire.components.BonfireRespawn
import com.mineinabyss.bonfire.extensions.filterIsBonfire
import com.mineinabyss.bonfire.extensions.isBonfire
import com.mineinabyss.bonfire.extensions.updateBonfireState
import com.mineinabyss.geary.papermc.datastore.remove
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.idofront.entities.rightClicked
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.info
import io.papermc.paper.event.player.PlayerTrackEntityEvent
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun PlayerBedEnterEvent.enter() {
        if (!bonfire.config.allowSettingBedRespawns) isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun PlayerInteractEvent.cancelBedRespawn() {
        if (clickedBlock?.blockData is Bed && rightClicked) setUseInteractedBlock(Event.Result.DENY)
    }

    @EventHandler
    fun PlayerRespawnEvent.onBonfireRespawn() {
        val bonfireRespawn = player.toGeary().get<BonfireRespawn>() ?: return
        val loc = bonfireRespawn.bonfireLocation

        loc.world.getChunkAtAsyncUrgently(loc).thenAccept { chunk ->
            val bonfireEntity =
                chunk.entities.filterIsBonfire().find { it.uniqueId == bonfireRespawn.bonfireUuid } ?: return@thenAccept
            val bonfireData = bonfireEntity.toGeary().get<Bonfire>() ?: return@thenAccept

            when {
                bonfireEntity.isBonfire && player.uniqueId in bonfireData.bonfirePlayers -> {
                    fun getHighestAirBlock(block: Block): Block {
                        return if (block.getRelative(BlockFace.UP).type.isAir || block == block.location.toHighestLocation().block) block
                        else getHighestAirBlock(block.getRelative(BlockFace.UP))
                    }

                    val height = loc.distance(getHighestAirBlock(loc.block).location)
                    loc.getNearbyEntities(0.5, height + 0.5, 0.5).filterIsInstance<Boat>().forEach(Boat::remove)

                    player.info(bonfire.messages.BONFIRE_RESPAWNING)
                    player.teleportAsync(loc.toCenterLocation())
                }

                else -> {
                    player.error(bonfire.messages.BONFIRE_NOT_FOUND)
                    player.toGeary().remove<BonfireRespawn>()
                }
            }
        }
    }

    @EventHandler
    fun PlayerTrackEntityEvent.onTrackBonfire() {
        (entity as? ItemDisplay)?.updateBonfireState()
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun PlayerJoinEvent.onJoinRemovedBonfire() {
        if (player.toGearyOrNull()?.has<BonfireRemoved>() != true) return

        bonfire.plugin.launch {
            delay(1.seconds)
            player.error(bonfire.messages.BONFIRE_REMOVED)
        }
    }

    @EventHandler
    fun PlayerQuitEvent.onPlayerQuit() {
        player.persistentDataContainer.remove<BonfireRemoved>()
    }
}
