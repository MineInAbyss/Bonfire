package com.mineinabyss.bonfire.listeners

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import com.github.shynixn.mccoroutine.bukkit.launch
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
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.geary.serialization.setPersisting
import com.mineinabyss.idofront.entities.toOfflinePlayer
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.success
import com.mineinabyss.idofront.nms.nbt.editOfflinePDC
import com.mineinabyss.idofront.nms.nbt.getOfflinePDC
import org.bukkit.Bukkit
import kotlinx.coroutines.delay
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

    private fun currentTime() = LocalDateTime.now().toInstant(ZoneOffset.UTC).epochSecond

    @EventHandler
    fun BlockBreakEvent.onBreakBlock() { // Cancel block-break if it is below a bonfire
        val boundingBox = block.boundingBox.shift(0.0, 1.0, 0.0)
        if (block.world.getNearbyEntities(boundingBox).none { it.isBonfire }) return
        isCancelled = true
    }

    @EventHandler
    fun BlockyFurniturePlaceEvent.onBonfirePlace() {
        baseEntity.toGearyOrNull()?.with { bonfire: Bonfire ->
            baseEntity.toGeary().setPersisting(bonfire.copy(bonfireOwner = player.uniqueId, bonfirePlayers = mutableListOf()))
            baseEntity.toGeary().setPersisting(BonfireExpirationTime(0.seconds, currentTime()))
            baseEntity.updateBonfireState()
        }
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
                    val totalUnlitTime = expiration.totalUnlitTime + (currentTime - expiration.lastUnlitTimeStamp).seconds
                    gearyEntity.setPersisting(expiration.copy(totalUnlitTime = totalUnlitTime, lastUnlitTimeStamp = currentTime))
                    if (totalUnlitTime >= bonfireData.bonfireExpirationTime) {
                        player.error(bonfire.messages.BONFIRE_EXPIRED)
                        BlockyFurnitures.removeFurniture(baseEntity)
                        isCancelled = true
                    } else Unit
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun BlockyFurnitureInteractEvent.onBonfireInteract() {
        if (!player.isSneaking || player.toGeary().has<BonfireCooldown>()) return
        if (hand != EquipmentSlot.HAND || abs(0 - player.velocity.y) < 0.001) return

        val gearyEntity = baseEntity.toGearyOrNull()
        gearyEntity?.with { bonfireData: Bonfire ->
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

                        player.toGeary().setPersisting(BonfireRespawn(baseEntity.uniqueId, baseEntity.location))
                        player.toGeary().setPersisting(BonfireEffectArea(baseEntity.uniqueId))
                        player.success("Respawn point set")
                    }
                }

                in bonfireData.bonfirePlayers -> {
                    bonfireData.bonfirePlayers -= player.uniqueId
                    player.toGeary().remove<BonfireRespawn>()
                    player.toGeary().remove<BonfireEffectArea>()
                    with(bonfire.config.respawnUnsetSound) {
                        baseEntity.world.playSound(baseEntity.location, sound, volume, pitch)
                    }
                    player.error(bonfire.messages.BONFIRE_BREAK)
                }
            }

            baseEntity.updateBonfireState()
            gearyEntity.encodeComponentsTo(baseEntity) // Ensure data is saved to PDC

            player.toGeary().set(BonfireCooldown(baseEntity.uniqueId))
            bonfire.plugin.launch {
                delay(bonfire.config.bonfireInteractCooldown)
                if (player.isOnline) player.toGeary().remove<BonfireCooldown>()
            }
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

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun BlockyFurnitureInteractEvent.onBonfireCooldown() {
        if (hand != EquipmentSlot.HAND || abs(0 - player.velocity.y) < 0.001) return
        if (player.fallDistance > bonfire.config.minFallDist) return

        player.toGeary().with { cooldown: BonfireCooldown ->
            if (cooldown.bonfire == baseEntity.uniqueId) isCancelled = true
            else player.toGeary().remove<BonfireCooldown>()
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
        if (!entity.isDead) return
        val bonfireData = (entity as? ItemDisplay)?.toGearyOrNull()?.get<Bonfire>() ?: return

        BlockyFurnitures.removeFurniture(entity as ItemDisplay)

        bonfireData.bonfirePlayers.map { it.toOfflinePlayer() }.forEach { p ->
            val onlinePlayer = p.player
            if (onlinePlayer != null) {
                onlinePlayer.toGeary().remove<BonfireEffectArea>()
                onlinePlayer.toGeary().remove<BonfireRespawn>()
                onlinePlayer.toGeary().remove<BonfireCooldown>()
            } else {
                p.editOfflinePDC {
                    encode(BonfireRemoved())
                    remove<BonfireRespawn>()
                    remove<BonfireCooldown>()
                }
            }
        }
    }
}
