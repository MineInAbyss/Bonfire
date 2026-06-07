package com.mineinabyss.bonfire.listeners

import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.bonfire.components.BonfireEffectArea
import com.mineinabyss.bonfire.components.BonfireExpirationTime
import com.mineinabyss.bonfire.components.BonfireRespawn
import com.mineinabyss.bonfire.extensions.canBreakBonfire
import com.mineinabyss.bonfire.extensions.removeOldBonfire
import com.mineinabyss.bonfire.extensions.updateBonfireState
import com.mineinabyss.geary.actions.ActionGroupContext
import com.mineinabyss.geary.actions.execute
import com.mineinabyss.geary.helpers.with
import com.mineinabyss.geary.papermc.datastore.decode
import com.mineinabyss.geary.papermc.datastore.encodeComponentsTo
import com.mineinabyss.geary.papermc.features.common.cooldowns.Cooldown
import com.mineinabyss.geary.papermc.features.common.cooldowns.Cooldowns
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.geary.papermc.withGeary
import com.mineinabyss.geary.serialization.setPersisting
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.success
import com.mineinabyss.idofront.nms.nbt.getOfflinePDC
import com.nexomc.nexo.api.NexoFurniture
import com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent
import com.nexomc.nexo.api.events.furniture.NexoFurnitureInteractEvent
import com.nexomc.nexo.api.events.furniture.NexoFurniturePlaceEvent
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.entity.ItemDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.inventory.EquipmentSlot
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

class NexoBonfireListener : Listener {
    private val cooldown = Cooldown(length = bonfire.config.bonfireInteractCooldown, display = null, "bonfire:interaction_cooldown")

    private fun currentTime() = LocalDateTime.now().toInstant(ZoneOffset.UTC).epochSecond

    @EventHandler
    fun NexoFurniturePlaceEvent.onBonfirePlace() {
        val gearyEntity = baseEntity.toGearyOrNull() ?: return
        val bonfire = gearyEntity.get<Bonfire>() ?: return

        gearyEntity.setPersisting(bonfire.copy(bonfireOwner = player.uniqueId, bonfirePlayers = mutableListOf()))
        gearyEntity.setPersisting(BonfireExpirationTime(0.seconds, currentTime()))
        baseEntity.withGeary {
            gearyEntity.encodeComponentsTo(baseEntity)
        }
        baseEntity.updateBonfireState()
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun NexoFurnitureInteractEvent.handleBonfireExpiration() {
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
                        NexoFurniture.remove(baseEntity)
                        isCancelled = true
                    } else Unit
                }
            }
            baseEntity.withGeary {
                gearyEntity.encodeComponentsTo(baseEntity) // Ensure data is saved to PDC
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun NexoFurnitureInteractEvent.onBonfireInteract() {
        if (!player.isSneaking || hand != EquipmentSlot.HAND || abs(0 - player.velocity.y) < 0.001) return
        if (player.fallDistance > (player.getAttribute(Attribute.SAFE_FALL_DISTANCE)?.value ?: 3.0)) return

        val gearyPlayer = player.toGeary()
        val gearyBonfire = baseEntity.toGearyOrNull() ?: return

        if (!Cooldowns.isComplete(gearyPlayer, "bonfire:interaction_cooldown")) {
            isCancelled = true
            return
        }
        cooldown.execute(ActionGroupContext(gearyPlayer))

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

                        gearyPlayer.setPersisting(BonfireRespawn(baseEntity.uniqueId, baseEntity.location))
                        gearyPlayer.setPersisting(BonfireEffectArea(baseEntity.uniqueId))
                        player.success("Respawn point set")
                    }
                }

                in bonfireData.bonfirePlayers -> {
                    bonfireData.bonfirePlayers -= player.uniqueId
                    gearyPlayer.remove<BonfireRespawn>()
                    gearyPlayer.remove<BonfireEffectArea>()
                    with(bonfire.config.respawnUnsetSound) {
                        baseEntity.world.playSound(baseEntity.location, sound, volume, pitch)
                    }
                    player.error(bonfire.messages.BONFIRE_BREAK)
                }
            }

            player.withGeary {
                gearyBonfire.encodeComponentsTo(baseEntity) // Ensure data is saved to PDC
                gearyPlayer.encodeComponentsTo(player)
            }
            baseEntity.updateBonfireState()
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun NexoFurnitureBreakEvent.onBreakBonfire() {
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
                else -> withGeary { offlinePlayer.getOfflinePDC()?.decode<BonfireRespawn>() }
            }
            respawn?.bonfireUuid == this.uniqueId
        }
        bonfireData.bonfirePlayers.clear()
        bonfireData.bonfirePlayers.addAll(validPlayers)
        return validPlayers.size == bonfireData.bonfirePlayers.size
    }
}
