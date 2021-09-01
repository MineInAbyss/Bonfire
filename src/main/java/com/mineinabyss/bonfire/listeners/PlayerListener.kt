package com.mineinabyss.bonfire.listeners

import com.mineinabyss.bonfire.bonfirePlugin
import com.mineinabyss.bonfire.config.BonfireConfig
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.MessageQueue
import com.mineinabyss.bonfire.data.MessageQueue.content
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.bonfire.data.Players.bonfireUUID
import com.mineinabyss.bonfire.ecs.components.updateFire
import com.mineinabyss.bonfire.extensions.*
import com.mineinabyss.bonfire.logging.BonfireLogger
import com.mineinabyss.idofront.entities.rightClicked
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.info
import com.mineinabyss.idofront.util.toMCKey
import com.okkero.skedule.SynchronizationContext
import com.okkero.skedule.schedule
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Campfire
import org.bukkit.block.data.type.Bed
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Boat
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
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

object PlayerListener : Listener {

    @EventHandler
    fun EntityDeathEvent.click() {
        if (entityType == EntityType.ARMOR_STAND && entity.killer != null) {
            val campfireModel = entity as ArmorStand
            if (campfireModel.isBonfireModel()) {
                isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun PlayerInteractEvent.rightClickCampfire() {
        val clicked = clickedBlock ?: return // If no block was clicked, return

        if (hand == EquipmentSlot.OFF_HAND) return  //the event is called twice, on for each hand. We want to ignore the offhand call

        if (!rightClicked) return   //only do stuff when player rightclicks

        if (abs(0 - player.velocity.y) < 0.001) return  // Only allow if player is on ground

        if (clicked.blockData is Bed) {
            isCancelled = true
            return
        }

        if (clicked.type == Material.CAMPFIRE) {
            val respawnCampfire = clicked.state as Campfire
            val bonfire = respawnCampfire.bonfireData() ?: return

            if (!player.isSneaking) {
                if (!(player.inventory.itemInMainHand.isCookableOnCampfire())) isCancelled = true
                return bonfire.updateFire()
            }

            if (player.fallDistance > BonfireConfig.data.minFallDist) {
                return
            }

            bonfirePlugin.schedule(SynchronizationContext.ASYNC) {
                val playersInBonfire = transaction { Players.select { bonfireUUID eq bonfire.uuid }.toList() }
                switchContext(SynchronizationContext.SYNC)

                if (playersInBonfire.firstOrNull { it[Players.playerUUID] == player.uniqueId } !== null) {
                    if (!player.removeBonfireSpawnLocation(bonfire.uuid)) {
                        player.error("This is not your respawn point")
                    }
                } else {  //add player to bonfire if bonfire not maxed out
                    if (playersInBonfire.count() >= BonfireConfig.data.maxPlayerCount) {
                        return@schedule player.error("This bonfire is full!")
                    } else {
                        player.setRespawnLocation(bonfire.uuid)
                    }
                }
            }

            isCancelled = true //I think we can cancel this event in any situation where we set/unset respawn.
            // We don't want to have any regular behavior happen.
        }
    }

    @EventHandler
    fun EntityDamageEvent.event() {
        if (!(entity is Player && cause == EntityDamageEvent.DamageCause.FIRE)) return
        val player = entity as Player
        player.location.findLocationAround(radius = 1, scale = 0.3) {
            it.block.state is Campfire
        }?.let {
            isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun PlayerRespawnEvent.event() {
        transaction {
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
                        campfire.bonfireData()?.updateFire()
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
    fun PlayerJoinEvent.joinServer() {
        player.discoverRecipe(BonfireConfig.data.bonfireRecipe.key.toMCKey())

        transaction {
            val respawnBonfire = Players
                .innerJoin(Bonfire, { bonfireUUID }, { entityUUID })
                .select { Players.playerUUID eq player.uniqueId }
                .firstOrNull() ?: return@transaction

            val respawnBonfireLocation = respawnBonfire[Bonfire.location]
            val respawnBlock = respawnBonfireLocation.world.getBlockAt(respawnBonfireLocation)
            if (respawnBlock.state is Campfire) {
                val campfire = respawnBlock.state as Campfire
                if (campfire.isBonfire(respawnBonfire[Bonfire.entityUUID])) {
                    campfire.bonfireData()?.updateFire()
                }
            }
        }
        bonfirePlugin.schedule {
            waitFor(20)
            transaction {
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

}



