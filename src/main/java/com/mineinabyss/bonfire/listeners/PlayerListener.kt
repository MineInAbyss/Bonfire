package com.mineinabyss.bonfire.listeners

import com.mineinabyss.bonfire.config.BonfireConfig
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.bonfire.data.Players.bonfireUUID
import com.mineinabyss.bonfire.extensions.*
import com.mineinabyss.bonfire.logging.BonfireLogger
import com.mineinabyss.idofront.entities.leftClicked
import com.mineinabyss.idofront.entities.rightClicked
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.info
import com.mineinabyss.idofront.util.toMCKey
import org.bukkit.Material
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
import org.bukkit.inventory.EquipmentSlot
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

        if (clicked.type == Material.CAMPFIRE) {
            val respawnCampfire = clicked.state as Campfire
            val bonfire = respawnCampfire.bonfireData() ?: return

            if (player.fallDistance > BonfireConfig.data.minFallDist) {
                isCancelled = true
                return
            }

            transaction {
                val playerFromDB = Players
                    .select { Players.playerUUID eq player.uniqueId }
                    .firstOrNull()

                if (playerFromDB != null && bonfire.uuid == playerFromDB[bonfireUUID]) {
                    if (!player.removeBonfireSpawnLocation(bonfire.uuid)) {
                        player.error("This is not your respawn point")
                    }
                } else {  //add player to bonfire if bonfire not maxed out
                    val playerCount = Players.select { bonfireUUID eq bonfire.uuid }.count()
                    if (playerCount >= BonfireConfig.data.maxPlayerCount) {
                        return@transaction player.error("This bonfire is full!")
                    } else {
                        player.setRespawnLocation(bonfire.uuid)
                    }
                }
            }


            if (item?.type.toString().contains("shovel", true) ||
                item?.type == Material.WATER_BUCKET ||
                item?.type == Material.PUFFERFISH_BUCKET ||
                item?.type == Material.SALMON_BUCKET ||
                item?.type == Material.COD_BUCKET ||
                item?.type == Material.TROPICAL_FISH_BUCKET ||
                item?.type == Material.AXOLOTL_BUCKET ||
                item?.type == Material.BIRCH_BOAT ||
                item?.type == Material.ACACIA_BOAT ||
                item?.type == Material.DARK_OAK_BOAT ||
                item?.type == Material.JUNGLE_BOAT ||
                item?.type == Material.SPRUCE_BOAT ||
                item?.type == Material.OAK_BOAT
            ) isCancelled = true
        } else if (clicked.blockData is Bed) {
            isCancelled = true
        }
    }

    @EventHandler
    fun EntityDamageEvent.event() {
        if (!(entity is Player && cause == EntityDamageEvent.DamageCause.FIRE)) return
        val player = entity as Player
        if (player.world.getBlockAt(player.location).state is Campfire) isCancelled = true
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
                        val entitiesOnRespawn = respawnBonfireLocation.world.getNearbyEntities(
                            respawnCenterLocation, 0.5, 1.5, 0.5
                        )
                        entitiesOnRespawn.filterIsInstance<Boat>().forEach {
                            it.remove()
                        }

                        player.info("Respawning at bonfire")
                        respawnLocation = respawnCenterLocation
                        BonfireLogger.logRespawnAtBonfire(player, respawnBonfireLocation)
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
    }
}



