package com.mineinabyss.bonfire.listeners

import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.blocky.api.BlockyFurnitures
import com.mineinabyss.blocky.api.events.furniture.BlockyFurnitureBreakEvent
import com.mineinabyss.blocky.api.events.furniture.BlockyFurnitureInteractEvent
import com.mineinabyss.blocky.api.events.furniture.BlockyFurniturePlaceEvent
import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.bonfire.components.*
import com.mineinabyss.bonfire.extensions.*
import com.mineinabyss.geary.helpers.with
import com.mineinabyss.geary.papermc.datastore.encode
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.idofront.entities.toOfflinePlayer
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.success
import kotlinx.coroutines.delay
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.inventory.EquipmentSlot
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

class BonfireListener : Listener {

    private fun currentTime() = LocalDateTime.now().toInstant(ZoneOffset.UTC).epochSecond

    @EventHandler
    fun BlockyFurniturePlaceEvent.onBonfirePlace() {
        baseEntity.toGearyOrNull()?.with { bonfire: Bonfire ->
            baseEntity.toGearyOrNull()?.setPersisting(bonfire.copy(bonfireOwner = player.uniqueId))
            baseEntity.toGearyOrNull()?.setPersisting(BonfireExpirationTime(0.seconds, currentTime()))
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

        val gearyEntity = baseEntity.toGearyOrNull() ?: return
        gearyEntity.with { bonfireData: Bonfire ->
            when (player.uniqueId) {
                !in bonfireData.bonfirePlayers -> {
                    if (bonfireData.bonfirePlayers.size >= bonfireData.maxPlayerCount) player.error(bonfire.messages.BONFIRE_FULL)
                    else {
                        gearyEntity.setPersisting(bonfireData.copy(bonfirePlayers = bonfireData.bonfirePlayers + player.uniqueId))
                        com.mineinabyss.bonfire.bonfire.config.respawnSetSound.run {
                            baseEntity.world.playSound(baseEntity.location, sound, volume, pitch)
                        }
                        // Load old bonfire and remove player from it if it exists
                        player.removeOldBonfire()

                        player.toGeary().apply {
                            setPersisting(BonfireRespawn(baseEntity.uniqueId, baseEntity.location))
                            setPersisting(BonfireEffectArea(baseEntity.uniqueId))
                        }
                        player.success("Respawn point set")
                    }
                }

                in bonfireData.bonfirePlayers -> {
                    gearyEntity.setPersisting(bonfireData.copy(bonfirePlayers = bonfireData.bonfirePlayers - player.uniqueId))
                    player.toGeary().apply {
                        remove<BonfireRespawn>()
                        remove<BonfireEffectArea>()
                    }
                    com.mineinabyss.bonfire.bonfire.config.respawnUnsetSound.run {
                        baseEntity.world.playSound(baseEntity.location, sound, volume, pitch)
                    }
                    player.error(bonfire.messages.BONFIRE_BREAK)
                }
            }

            baseEntity.updateBonfireState()

            player.toGeary().set(BonfireCooldown(baseEntity.uniqueId))
            com.mineinabyss.bonfire.bonfire.plugin.launch {
                delay(com.mineinabyss.bonfire.bonfire.config.bonfireInteractCooldown)
                if (player.isOnline) player.toGeary().remove<BonfireCooldown>()
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun BlockyFurnitureBreakEvent.onBreakBonfire() {
        baseEntity.toGearyOrNull()?.with { bonfireData: Bonfire ->
            when {
                bonfireData.bonfirePlayers.isEmpty() -> return
                player.uniqueId == bonfireData.bonfireOwner || player.hasPermission(BonfirePermissions.REMOVE_BONFIRE_PERMISSION) -> {
                    bonfireData.bonfirePlayers.map { it.toOfflinePlayer() }.forEach { p ->
                        when {
                            p.isOnline -> p.player?.error(com.mineinabyss.bonfire.bonfire.messages.BONFIRE_REMOVED)
                            else -> {
                                val pdc = p.getOfflinePDC() ?: return@forEach
                                pdc.encode(BonfireRemoved())
                                p.saveOfflinePDC(pdc)
                            }
                        }
                    }
                }

                else -> {
                    player.error(bonfire.messages.BONFIRE_BREAK_DENIED)
                    isCancelled = true
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun BlockyFurnitureInteractEvent.onBonfireCooldown() {
        if (hand != EquipmentSlot.HAND || abs(0 - player.velocity.y) < 0.001) return
        if (player.fallDistance > bonfire.config.minFallDist) return

        baseEntity.toGearyOrNull()?.with { bonfire: Bonfire ->
            val cooldown = player.toGeary().get<BonfireCooldown>() ?: return
            if (cooldown.bonfire == baseEntity.uniqueId) isCancelled = true
            else player.toGeary().remove<BonfireCooldown>()
        }
    }
}
