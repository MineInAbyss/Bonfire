package com.mineinabyss.bonfire.listeners

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import com.mineinabyss.blocky.api.BlockyFurnitures
import com.mineinabyss.blocky.api.events.furniture.BlockyFurnitureBreakEvent
import com.mineinabyss.blocky.api.events.furniture.BlockyFurnitureInteractEvent
import com.mineinabyss.blocky.api.events.furniture.BlockyFurniturePlaceEvent
import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.bonfire.components.*
import com.mineinabyss.bonfire.extensions.canBreakBonfire
import com.mineinabyss.bonfire.extensions.isBonfire
import com.mineinabyss.bonfire.extensions.removeOldBonfire
import com.mineinabyss.bonfire.extensions.updateBonfireState
import com.mineinabyss.geary.helpers.with
import com.mineinabyss.geary.papermc.datastore.decode
import com.mineinabyss.geary.papermc.datastore.encode
import com.mineinabyss.geary.papermc.datastore.encodeComponentsTo
import com.mineinabyss.geary.papermc.datastore.remove
import com.mineinabyss.geary.papermc.features.general.cooldown.Cooldown
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.geary.serialization.setPersisting
import com.mineinabyss.idofront.entities.toOfflinePlayer
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.success
import com.mineinabyss.idofront.nms.nbt.editOfflinePDC
import com.mineinabyss.idofront.nms.nbt.getOfflinePDC
import org.bukkit.Bukkit
import org.bukkit.entity.ItemDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.inventory.EquipmentSlot
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

class BonfireListener : Listener {
    val cooldown = Cooldown(length = bonfire.config.bonfireInteractCooldown)

    private fun currentTime() = LocalDateTime.now().toInstant(ZoneOffset.UTC).epochSecond

    @EventHandler
    fun BlockBreakEvent.onBreakBlock() { // Cancel block-break if it is below a bonfire
        val boundingBox = block.boundingBox.shift(0.0, 1.0, 0.0)
        if (block.world.getNearbyEntities(boundingBox).none { it.isBonfire }) return
        isCancelled = true
    }

    @EventHandler
    fun BlockyFurniturePlaceEvent.onBonfirePlace() {
        val gearyEntity = baseEntity.toGearyOrNull() ?: return
        val bonfire = gearyEntity.get<Bonfire>() ?: return

        gearyEntity.setPersisting(bonfire.copy(bonfireOwner = player.uniqueId, bonfirePlayers = mutableListOf()))
        gearyEntity.setPersisting(BonfireExpirationTime(0.seconds, currentTime()))
        gearyEntity.encodeComponentsTo(baseEntity)
        baseEntity.updateBonfireState()
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun BlockyFurnitureInteractEvent.handleBonfireExpiration() {
        val gearyEntity = baseEntity.toGearyOrNull() ?: return
        val currentTime = currentTime()

        gearyEntity.with { bonfireData: Bonfire, expiration: BonfireExpirationTime ->
            when {
                !player.isSneaking -> return
                // Bonfire is lit, player is only registered player, player is unsetting
                // Since it is being unlit, set lastUnlitTimeStamp to currentTime
                bonfireData.bonfirePlayers.isNotEmpty() && bonfireData.bonfirePlayers.all { it == player.uniqueId } -> {
                    gearyEntity.setPersisting(expiration.copy(totalUnlitTime = expiration.totalUnlitTime, lastUnlitTimeStamp = currentTime))
                }
                //  Bonfire was empty and player is attempting to set spawn
                // Check if Bonfires new totalUnlittime is greater than expiration time
                else -> {
                    val totalUnlitTime = expiration.totalUnlitTime.plus(currentTime.minus(expiration.lastUnlitTimeStamp).seconds)
                    gearyEntity.setPersisting(expiration.copy(totalUnlitTime = totalUnlitTime, lastUnlitTimeStamp = currentTime))
                    if (totalUnlitTime >= bonfireData.bonfireExpirationTime) {
                        player.error(bonfire.messages.BONFIRE_EXPIRED)
                        BlockyFurnitures.removeFurniture(baseEntity)
                        isCancelled = true
                    } else Unit
                }
            }
            gearyEntity.encodeComponentsTo(baseEntity) // Ensure data is saved to PDC
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun BlockyFurnitureInteractEvent.onBonfireInteract() {
        if (!player.isSneaking || hand != EquipmentSlot.HAND || abs(0 - player.velocity.y) < 0.001) return

        val gearyPlayer = player.toGeary()
        val gearyBonfire = baseEntity.toGearyOrNull() ?: return
        if (!Cooldown.isComplete(gearyPlayer, gearyBonfire)) {
            isCancelled = true
            return
        }
        Cooldown.start(gearyPlayer, gearyBonfire, cooldown)

        gearyBonfire.with { bonfireData: Bonfire ->
            when (player.uniqueId) {
                !in bonfireData.bonfirePlayers -> {
                    if (bonfireData.bonfirePlayers.size >= bonfireData.maxPlayerCount &&
                        baseEntity.ensureSavedPlayersAreValid(bonfireData) // Will double-check that the players are still valid, returns true if size # of players didn't change
                    ) player.error(bonfire.messages.BONFIRE_FULL)
                    else {
                        // Load old bonfire and remove player from it if it exists
                        player.removeOldBonfire()

                        bonfireData.bonfirePlayers += player.uniqueId
                        with(bonfire.config.respawnSetSound) {
                            baseEntity.world.playSound(baseEntity.location, sound, volume, pitch)
                        }

                        player.persistentDataContainer.encode(BonfireRespawn(baseEntity.uniqueId, baseEntity.location))
                        player.persistentDataContainer.encode(BonfireEffectArea(baseEntity.uniqueId))
                        player.success("Respawn point set")
                    }
                }

                in bonfireData.bonfirePlayers -> {
                    bonfireData.bonfirePlayers -= player.uniqueId
                    player.persistentDataContainer.remove<BonfireRespawn>()
                    player.persistentDataContainer.remove<BonfireEffectArea>()
                    with(bonfire.config.respawnUnsetSound) {
                        baseEntity.world.playSound(baseEntity.location, sound, volume, pitch)
                    }
                    player.error(bonfire.messages.BONFIRE_BREAK)
                }
            }

            baseEntity.updateBonfireState()
            gearyBonfire.encodeComponentsTo(baseEntity) // Ensure data is saved to PDC
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun BlockyFurnitureBreakEvent.onBreakBonfire() {
        baseEntity.toGearyOrNull()?.with { bonfireData: Bonfire ->
            baseEntity.ensureSavedPlayersAreValid(bonfireData)
            if (!player.canBreakBonfire(bonfireData)) {
                player.error(bonfire.messages.BONFIRE_BREAK_DENIED)
                isCancelled = true
            }
        }
    }

    private fun ItemDisplay.ensureSavedPlayersAreValid(bonfireData: Bonfire): Boolean {
        val validPlayers = bonfireData.bonfirePlayers.filter {
            val offlinePlayer = Bukkit.getOfflinePlayer(it)
            val respawn = when {
                offlinePlayer.isOnline -> offlinePlayer.player?.toGearyOrNull()?.get<BonfireRespawn>()
                else -> offlinePlayer.getOfflinePDC()?.decode<BonfireRespawn>()
            }
            respawn?.bonfireUuid == this.uniqueId
        }
        bonfireData.bonfirePlayers.clear()
        bonfireData.bonfirePlayers.addAll(validPlayers)
        return validPlayers.size == bonfireData.bonfirePlayers.size
    }

    /**
     * When a bonfire is marked for removal, either via commands or being broken in any way
     * The below listener will handle completely removing it and all assosiacted playerdata
     * Since /kill commands wouldn't trigger BlockyFurnitureBreakEvent then the main logic should be done here
     */
    @EventHandler
    fun EntityRemoveFromWorldEvent.onRemoveBonfire() {
        val bonfireData = (entity as? ItemDisplay).takeIf { entity.isDead }?.toGearyOrNull()?.get<Bonfire>() ?: return

        BlockyFurnitures.removeFurniture(entity as ItemDisplay)

        bonfireData.bonfirePlayers.map { it.toOfflinePlayer() }.forEach { p ->
            val onlinePlayer = p.player
            if (onlinePlayer != null) {
                val gearyEntity = onlinePlayer.toGeary()
                gearyEntity.remove<BonfireEffectArea>()
                gearyEntity.remove<BonfireRespawn>()
                gearyEntity.encodeComponentsTo(onlinePlayer)
            } else {
                p.editOfflinePDC {
                    encode(BonfireRemoved())
                    remove<BonfireRespawn>()
                }
            }
        }
    }
}
