package com.mineinabyss.bonfire.listeners

import com.mineinabyss.bonfire.config.BonfireConfig
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.bonfire.data.Players.bonfireUUID
import com.mineinabyss.bonfire.extensions.*
import com.mineinabyss.idofront.entities.leftClicked
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.info
import com.mineinabyss.idofront.messaging.logVal
import org.bukkit.Material
import org.bukkit.block.Campfire
import org.bukkit.block.data.type.Bed
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.player.PlayerBedEnterEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.EquipmentSlot
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

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

    @EventHandler
    fun PlayerInteractEvent.rightClickCampfire() {
        val clicked = clickedBlock ?: return // If no block was clicked, return

        if (hand == EquipmentSlot.OFF_HAND) return

        if (clicked.type == Material.CAMPFIRE) {
            val respawnCampfire = clicked.state as Campfire
            val bonfire = respawnCampfire.bonfireData() ?: return

            if (leftClicked) {
                if (!player.removeBonfireSpawnLocation(bonfire.uuid)) {
                    player.error("This is not your respawn point")
                }
                return
            }

            transaction {
                val playerCount = Players.select { bonfireUUID eq bonfire.uuid }.count()
                if (playerCount >= BonfireConfig.data.maxPlayerCount) {
                    return@transaction player.error("This campfire is full!")
                }
            }

            player.setRespawnLocation(bonfire.uuid)

            if (item?.type.toString().contains("shovel", true) ||
                item?.type == Material.WATER_BUCKET ||
                item?.type == Material.PUFFERFISH_BUCKET ||
                item?.type == Material.SALMON_BUCKET ||
                item?.type == Material.COD_BUCKET ||
                item?.type == Material.TROPICAL_FISH_BUCKET ||
                item?.type == Material.AXOLOTL_BUCKET
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
                val respawnBlock = player.world.getBlockAt(respawnBonfireLocation)
                if (respawnBlock.state is Campfire) {
                    val campfire = respawnBlock.state as Campfire
                    if (campfire.isBonfire(respawnBonfire[Bonfire.entityUUID])) {
                        player.info("Respawning at bonfire")
                        respawnLocation = respawnBonfireLocation.toCenterLocation()
                        return@transaction
                    }
                }

                logVal("Bonfire missing for player " + player.name)
                player.error("Bonfire not found")
                Players.deleteWhere { Players.playerUUID eq player.uniqueId }
                Bonfire.deleteWhere { Bonfire.entityUUID eq respawnBonfire[Bonfire.entityUUID] }
            }
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
}



