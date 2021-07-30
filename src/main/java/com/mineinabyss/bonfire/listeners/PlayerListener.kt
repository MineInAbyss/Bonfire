package com.mineinabyss.bonfire.listeners

import com.mineinabyss.bonfire.config.BonfireConfig
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.bonfire.extensions.*
import com.mineinabyss.idofront.entities.leftClicked
import com.mineinabyss.idofront.messaging.*
import org.bukkit.Material
import org.bukkit.block.Campfire
import org.bukkit.block.data.type.Bed
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.*
import org.bukkit.inventory.EquipmentSlot
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
                val playerCount = Players.select { Players.bonfireUUID eq bonfire.uuid }.count()
                if (playerCount >= BonfireConfig.data.maxPlayerCount) {
                    return@transaction player.error("This campfire is full!")
                }
            }

            player.setRespawnLocation(bonfire.uuid)
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

    @EventHandler
    fun PlayerRespawnEvent.event() {
        val playerUUID = player.uniqueId
        transaction {
            val respawnBonfire = (Players innerJoin Bonfire)
                .select { Players.playerUUID eq playerUUID }
                .firstOrNull() ?: return@transaction
            val respawnBonfireLocation = respawnBonfire[Bonfire.location]
            respawnBonfireLocation.broadcastVal()
            val respawnBlock = player.world.getBlockAt(respawnBonfireLocation)
            respawnBlock.broadcastVal()
            if (respawnBlock.state is Campfire) {
                val campfire = respawnBlock.state as Campfire
                campfire.bonfireData()?.uuid.broadcastVal("Bonfire UUID")
                if (campfire.isBonfire(respawnBonfire[Bonfire.entityUUID])) {
                    player.info("Respawning at bonfire")
                    respawnLocation = respawnBonfireLocation.toCenterLocation()
                    return@transaction
                }
            }

            //TODO: Handle if there is no bonfire at the location (remove from Bonfire table and remove player entry)
        }

//        val gearyPlayer = geary(player)
//        val respawnLocation = gearyPlayer.get<RespawnLocation>() ?: return
//        respawnLocation.broadcastVal()
//        val respawnBlock = player.world.getBlockAt(respawnLocation.location)
//        respawnBlock.type.broadcastVal()
//        if (respawnBlock.state is Campfire) {
//            val campfire = respawnBlock.state as Campfire
//            campfire.bonfireData()?.uuid.broadcastVal("Bonfire UUID")
//            respawnLocation.uuid.broadcastVal("Respawn UUID")
//            if (campfire.isBonfire(respawnLocation.uuid)) {
//                player.info("Respawning at bonfire")
//                setRespawnLocation(respawnLocation.location.toCenterLocation())
//                return
//            }
//        }
//
//        logVal("Bonfire missing for player " + player.name)
//        player.error("Bonfire not found")
//        gearyPlayer.remove(RespawnLocation::class)
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



