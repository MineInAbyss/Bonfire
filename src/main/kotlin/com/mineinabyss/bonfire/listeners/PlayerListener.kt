package com.mineinabyss.bonfire.listeners

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.mineinabyss.bonfire.BonfireContext
import com.mineinabyss.bonfire.bonfirePlugin
import com.mineinabyss.bonfire.config.BonfireConfig
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.MessageQueue
import com.mineinabyss.bonfire.data.MessageQueue.content
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.bonfire.data.Players.bonfireUUID
import com.mineinabyss.bonfire.ecs.components.BonfireCooldown
import com.mineinabyss.bonfire.extensions.*
import com.mineinabyss.bonfire.logging.BonfireLogger
import com.mineinabyss.geary.papermc.access.toGeary
import com.mineinabyss.idofront.entities.rightClicked
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.info
import io.papermc.paper.event.entity.EntityInsideBlockEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Campfire
import org.bukkit.block.data.type.Bed
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Boat
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.*
import org.bukkit.inventory.CampfireRecipe
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

object PlayerListener : Listener {

    @EventHandler
    fun EntityDeathEvent.death() {
        if (entityType == EntityType.ARMOR_STAND && entity.killer != null) {
            val campfireModel = entity as ArmorStand
            if (campfireModel.isBonfireModel()) {
                isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun PlayerInteractEvent.rightClickCampfire() {
        val gearyPlayer = player.toGeary()
        val config = BonfireConfig.data
        val clicked = clickedBlock ?: return // If no block was clicked, return

        if (hand == EquipmentSlot.OFF_HAND || !rightClicked) return  //the event is called twice, on for each hand. We want to ignore the offhand call
        if (abs(0 - player.velocity.y) < 0.001) return  // Only allow if player is on ground

        if (clicked.blockData is Bed) {
            isCancelled = true
            return
        }

        val campfire = clicked.state as? Campfire ?: return
        campfire.isBonfire || return
        if (!player.isSneaking) {
            if (player.inventory.itemInMainHand.isCookableOnCampfire()) return campfire.updateFire()
            isCancelled = true
            return campfire.updateFire()
        }

        if (player.fallDistance > config.minFallDist) return
        if (gearyPlayer.has<BonfireCooldown>()) {
            if (gearyPlayer.get<BonfireCooldown>()?.bonfire == campfire.uuid) return
            else gearyPlayer.remove<BonfireCooldown>() // Remove so setting it below corrects the uuid
        }

        bonfirePlugin.launch(bonfirePlugin.asyncDispatcher) {
            val playersInBonfire = transaction(BonfireContext.db) {
                Players.select { bonfireUUID eq campfire.uuid }.toList()
            }

            withContext(bonfirePlugin.minecraftDispatcher) {
                if (playersInBonfire.firstOrNull { it[Players.playerUUID] == player.uniqueId } !== null) {
                    if (!player.removeBonfireSpawnLocation(campfire.uuid)) {
                        player.error("This is not your respawn point")
                    }
                } else {  //add player to bonfire if bonfire not maxed out
                    if (playersInBonfire.count() >= config.maxPlayerCount) {
                        return@withContext player.error("This bonfire is full!")
                    } else {
                        player.setRespawnLocation(campfire.uuid)
                        gearyPlayer.setPersisting(BonfireCooldown(campfire.uuid))
                        bonfirePlugin.launch {
                            delay(config.bonfireInteractCooldown)
                            gearyPlayer.remove<BonfireCooldown>()
                        }
                    }
                }

            }
        }

        isCancelled = true //I think we can cancel this event in any situation where we set/unset respawn.
        // We don't want to have any regular behavior happen.
    }

    @EventHandler
    fun EntityInsideBlockEvent.standingOnBonfire() {
        if ((block.state as? Campfire)?.isBonfire == true) isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun PlayerRespawnEvent.event() {
        transaction(BonfireContext.db) {
            val respawnBonfire = Players
                .innerJoin(Bonfire, { bonfireUUID }, { entityUUID })
                .select { Players.playerUUID eq player.uniqueId }
                .firstOrNull()

            if (respawnBonfire != null) {
                val respawnBonfireLocation = respawnBonfire[Bonfire.location]
                val respawnBlock = respawnBonfireLocation.world.getBlockAt(respawnBonfireLocation)
                if (respawnBlock.state is Campfire) {
                    val campfire = respawnBlock.state as Campfire
                    if (campfire.isBonfire(respawnBonfire[Bonfire.entityUUID])) {
                        val respawnCenterLocation = respawnBonfireLocation.toCenterLocation()

                        respawnBonfireLocation.chunk.load()

                        fun getHighestAirBlock(block: Block): Block {
                            return if (block.getRelative(BlockFace.UP).type != Material.AIR || block == block.location.toHighestLocation().block) {
                                block
                            } else {
                                getHighestAirBlock(block.getRelative(BlockFace.UP))
                            }
                        }

                        val height =
                            respawnBonfireLocation.distance(getHighestAirBlock(respawnBonfireLocation.block).location)
                        val entitiesOnRespawn = respawnBonfireLocation.world.getNearbyEntities(
                            respawnCenterLocation, 0.5, height + 0.5, 0.5
                        )
                        entitiesOnRespawn.filterIsInstance<Boat>().forEach {
                            it.remove()
                        }

                        player.info("Respawning at bonfire")
                        respawnLocation = respawnCenterLocation
                        BonfireLogger.logRespawnAtBonfire(player, respawnBonfireLocation)
                        campfire.updateFire()
                        return@transaction
                    }
                }

                player.error("Bonfire not found")
                BonfireLogger.logRespawnFailed(player, respawnBonfire[Bonfire.location])
                Players.deleteWhere { Players.playerUUID eq player.uniqueId }
                Bonfire.deleteWhere { Bonfire.entityUUID eq respawnBonfire[Bonfire.entityUUID] }
            }
            respawnLocation = player.server.worlds.first().spawnLocation
            BonfireLogger.logRespawnAtWorldSpawn(player)
        }
    }

    @EventHandler
    fun PlayerArmorStandManipulateEvent.event() {
        if (rightClicked.isBonfireModel()) isCancelled = true
    }

    @EventHandler
    fun PlayerBedEnterEvent.enter() {
        isCancelled = true
    }

    @EventHandler
    suspend fun PlayerJoinEvent.joinServer() {
        val (respawnBonfireLocation, uuid) = withContext(bonfirePlugin.asyncDispatcher) {
            transaction(BonfireContext.db) {
                val respawnBonfire = Players
                    .innerJoin(Bonfire, { bonfireUUID }, { entityUUID })
                    .select { Players.playerUUID eq player.uniqueId }
                    .firstOrNull() ?: return@transaction null
                respawnBonfire[Bonfire.location] to respawnBonfire[Bonfire.entityUUID]
            }
        } ?: return
        val respawnBlock = respawnBonfireLocation.world.getBlockAt(respawnBonfireLocation)
        (respawnBlock.state as? Campfire)?.let {
            if (it.isBonfire(uuid)) it.updateFire()
        }
        withContext(bonfirePlugin.asyncDispatcher) {
            delay(1.seconds)
            transaction(BonfireContext.db) {
                MessageQueue.select { MessageQueue.playerUUID eq player.uniqueId }.forEach {
                    player.error(it[content])
                }
                MessageQueue.deleteWhere { MessageQueue.playerUUID eq player.uniqueId }
            }
        }
    }

    fun ItemStack.isCookableOnCampfire(): Boolean {
        var valid = false
        Bukkit.recipeIterator().forEach {
            if (it is CampfireRecipe && it.input.isSimilar(this)) {
                valid = true
            }
        }

        return valid
    }

    @EventHandler
    fun PlayerQuitEvent.onQuit() {
        player.toGeary().remove<BonfireCooldown>()
    }
}
