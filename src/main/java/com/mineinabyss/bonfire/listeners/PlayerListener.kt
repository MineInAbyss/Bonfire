package com.mineinabyss.bonfire.listeners

import com.mineinabyss.bonfire.components.RespawnLocation
import com.mineinabyss.bonfire.components.save
import com.mineinabyss.bonfire.extensions.*
import com.mineinabyss.geary.minecraft.access.geary
import com.mineinabyss.idofront.entities.leftClicked
import com.mineinabyss.idofront.entities.rightClicked
import com.mineinabyss.idofront.messaging.*
import org.bukkit.Material
import org.bukkit.block.data.type.Bed
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.player.PlayerBedEnterEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.block.Campfire as BlockCampfire

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
        if (hand == EquipmentSlot.OFF_HAND) return;
        if (clicked.type == Material.CAMPFIRE) {
            val respawnCampfire = clicked.state as BlockCampfire
            val bonfire = respawnCampfire.bonfireData() ?: return

            if (leftClicked) {
                if (!player.removeBonfireSpawnLocation(bonfire.uuid)) {
                    player.error("This is not your respawn point")
                }
                return
            }

            if(bonfire.players.size >= 4) {
                return player.error("This campfire is full!")
            }

            player.setRespawnLocation(RespawnLocation(clicked.location, bonfire.uuid))
        } else
        if (clicked.blockData is Bed) {
            isCancelled = true
        }
    }

    @EventHandler
    fun EntityDamageEvent.event() {
        if(!(entity is Player && cause == EntityDamageEvent.DamageCause.FIRE)) return
        val player = entity as Player
        if(player.world.getBlockAt(player.location).state is BlockCampfire) isCancelled = true
    }

    @EventHandler
    fun PlayerRespawnEvent.event() {
        val gearyPlayer = geary(player)
        val respawnLocation = gearyPlayer.get<RespawnLocation>() ?: return
        respawnLocation.broadcastVal()
        val respawnBlock = player.world.getBlockAt(respawnLocation.location)
        respawnBlock.type.broadcastVal()
        if(respawnBlock.state is BlockCampfire) {
            val campfire = respawnBlock.state as BlockCampfire
            campfire.bonfireData()?.uuid.broadcastVal("Bonfire UUID")
            respawnLocation.uuid.broadcastVal("Respawn UUID")
            if (campfire.isBonfire(respawnLocation.uuid)) {
                player.info("Respawning at bonfire")
                setRespawnLocation(respawnLocation.location.toCenterLocation())
                return
            }
        }

        logVal("Bonfire missing for player " + player.name)
        player.error("Bonfire not found")
        gearyPlayer.remove(RespawnLocation::class)
    }

    @EventHandler
    fun PlayerArmorStandManipulateEvent.event() {
        if(rightClicked.isBonfireModel()) isCancelled = true
    }

    @EventHandler
    fun PlayerBedEnterEvent.enter() {
        isCancelled = true
    }
}



