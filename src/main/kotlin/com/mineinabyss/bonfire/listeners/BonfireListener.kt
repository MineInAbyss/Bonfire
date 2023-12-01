package com.mineinabyss.bonfire.listeners

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
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
import com.mineinabyss.geary.papermc.datastore.remove
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.idofront.entities.toOfflinePlayer
import com.mineinabyss.idofront.messaging.broadcast
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.success
import com.mineinabyss.idofront.nms.nbt.editOfflinePDC
import kotlinx.coroutines.delay
import org.bukkit.entity.ItemDisplay
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
                        bonfire.config.respawnSetSound.run {
                            baseEntity.world.playSound(baseEntity.location, sound, volume, pitch)
                        }
                        // Load old bonfire and remove player from it if it exists
                        player.removeOldBonfire()

                        player.toGeary().setPersisting(BonfireRespawn(baseEntity.uniqueId, baseEntity.location))
                        player.toGeary().setPersisting(BonfireEffectArea(baseEntity.uniqueId))
                        player.success("Respawn point set")
                    }
                }

                in bonfireData.bonfirePlayers -> {
                    gearyEntity.setPersisting(bonfireData.copy(bonfirePlayers = bonfireData.bonfirePlayers - player.uniqueId))
                    player.toGeary().remove<BonfireRespawn>()
                    player.toGeary().remove<BonfireEffectArea>()
                    bonfire.config.respawnUnsetSound.run {
                        baseEntity.world.playSound(baseEntity.location, sound, volume, pitch)
                    }
                    player.error(bonfire.messages.BONFIRE_BREAK)
                }
            }

            baseEntity.updateBonfireState()

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

    /**
     * When a bonfire is marked for removal, either via commands or being broken in any way
     * The below listener will handle completely removing it and all assosiacted playerdata
     * Since /kill commands wouldn't trigger BlockyFurnitureBreakEvent then the main logic should be done here
     */
    @EventHandler
    fun EntityRemoveFromWorldEvent.onRemoveBonfire() {
        if (!entity.isDead) return
        val bonfireData = (entity as? ItemDisplay)?.toGearyOrNull()?.get<Bonfire>() ?: return

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

        BlockyFurnitures.removeFurniture(entity as ItemDisplay)
    }
}
