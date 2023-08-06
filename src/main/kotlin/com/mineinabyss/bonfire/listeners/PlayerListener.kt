package com.mineinabyss.bonfire.listeners

import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.bonfire.components.BonfireCooldown
import com.mineinabyss.bonfire.components.BonfireRespawn
import com.mineinabyss.bonfire.extensions.OFFLINE_MESSAGE_FILE
import com.mineinabyss.bonfire.extensions.isBonfire
import com.mineinabyss.bonfire.extensions.removeFromOfflineMessager
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
            val bonfire = bonfireEntity.toGeary().get<Bonfire>() ?: return

            when {
                bonfireEntity.isBonfire && player.uniqueId in bonfire.bonfirePlayers -> {
                    fun getHighestAirBlock(block: Block): Block {
                        return if (block.getRelative(BlockFace.UP).type.isAir || block == block.location.toHighestLocation().block) block
                        else getHighestAirBlock(block.getRelative(BlockFace.UP))
                    }

                    val height = loc.distance(getHighestAirBlock(loc.block).location)
                    loc.getNearbyEntities(0.5, height + 0.5, 0.5).filterIsInstance<Boat>().forEach(Boat::remove)

                    player.info("Respawning at bonfire...")
                    respawnLocation = loc.toCenterLocation()
                }
                else -> {
                    player.error("Bonfire was not found...")
                    player.toGeary().remove<BonfireRespawn>()
                }
            }
            com.mineinabyss.bonfire.bonfire.plugin.launch {
                delay(3.ticks)
                bonfireEntity.updateBonfireState(player)
            }
        }

        if (!loc.isChunkLoaded) player.teleportAsync(loc.toCenterLocation()).thenRun(::respawnAtBonfire)
        else respawnAtBonfire()
    }

    @EventHandler
    fun PlayerJoinEvent.onJoinRemovedBonfire() {
        if (player.uniqueId.toString() !in OFFLINE_MESSAGE_FILE.readLines()) return
        bonfire.plugin.launch {
            delay(2.seconds)
            player.error("Your respawn point was unset because the bonfire was broken by the owner")
            player.uniqueId.removeFromOfflineMessager()
            player.toGeary().remove<BonfireRespawn>()
        }
    }

    @EventHandler fun PlayerJoinEvent.onPlayerJoin() {
        player.toGearyOrNull()?.remove<BonfireCooldown>()
        val bonfire = player.toGeary().get<BonfireRespawn>() ?: return
        val bonfireEntity = bonfire.bonfireLocation.world.getEntity(bonfire.bonfireUuid) as? ItemDisplay ?: return
        com.mineinabyss.bonfire.bonfire.plugin.launch {
            delay(3.ticks)
            bonfireEntity.updateBonfireState(player)
        }
    }
    @EventHandler fun PlayerQuitEvent.onPlayerQuit() {
        player.toGearyOrNull()?.remove<BonfireCooldown>()
    }
}
