package com.mineinabyss.bonfire.listeners

import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.bonfire.Permissions
import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.Bonfire.ownerUUID
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.bonfire.extensions.*
import com.mineinabyss.bonfire.logging.BonfireLogger
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.spawning.spawn
import com.mineinabyss.idofront.time.ticks
import kotlinx.coroutines.delay
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Campfire
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.ThrownPotion
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.*
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.inventory.EquipmentSlot
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.bukkit.block.data.type.Campfire as CampfireBlockData

object BlockListener : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun BlockPlaceEvent.place() {
        if (!itemInHand.isSimilar(bonfire.config.bonfireItem.toItemStack())) return

        if (blockPlaced.hasBonfireBelow()) isCancelled = true
        if (!blockPlaced.getRelative(BlockFace.UP).type.isAir) isCancelled = true
        if (!blockPlaced.getRelative(BlockFace.UP, 2).type.isAir) isCancelled = true
        if (isCancelled) return

        // If we are trying to place our custom campfire
        // we need to save this as a respawn campfire instead of just a regular campfire
        val respawnCampfire = blockPlaced.state as Campfire
        val campfireData = blockPlaced.blockData as CampfireBlockData
        respawnCampfire.blockData = campfireData.apply { isLit = false }

        // Spawn Item Display
        val itemDisplay = blockPlaced.location.toCenterLocation().spawn<ItemDisplay> { this.setDefaults() } ?: return

        respawnCampfire.createBonfire(itemDisplay.uniqueId, player.uniqueId)

        BonfireLogger.logBonfirePlace(blockPlaced.location, player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun BlockBreakEvent.breakBlock() {
        val campfire = (block.state as? Campfire) ?: return
        campfire.isBonfire || return

        transaction(bonfire.db) {
            val hasRegisteredPlayers = Players.select { Players.bonfireUUID eq campfire.uuid }.any()
            val bonfireRow = Bonfire.select { Bonfire.entityUUID eq campfire.uuid }.firstOrNull()

            if (player.hasPermission(Permissions.BREAK_BONFIRE_PERMISSION) || !hasRegisteredPlayers || (bonfireRow !== null && bonfireRow[ownerUUID] == player.uniqueId)) {
                campfire.destroy(false)
                BonfireLogger.logBonfireBreak(block.location, player)
            } else {
                player.error("You cannot break this bonfire, unkindled one")
                isCancelled = true
            }
        }
    }

    @EventHandler
    fun EntitiesLoadEvent.load() {
        val location = entities.firstOrNull()?.location ?: return
        if (!location.isWorldLoaded || !location.isChunkLoaded) return
        if (!chunk.isLoaded || !chunk.isEntitiesLoaded) return

        entities.filter { it.isBonfireModel() }.forEach {
            val campfire = it.location.block.state as? Campfire ?: return it.remove()
            if (campfire.uuid != it.uniqueId) it.remove()
            else campfire.updateBonfire()
        }

        // Attempt to convert old bonfires
        entities.filter { it.isOldBonfireModel() }.forEach {
            val campfire = it.location.block.state as? Campfire ?: return it.remove()
            it.remove()
            campfire.createModel()
            campfire.updateBonfire()
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun BlockCookEvent.cook() {
        val campfire = block.state as? Campfire ?: return
        bonfire.plugin.launch {
            delay(1.ticks)
            campfire.updateFire()
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun BlockPistonExtendEvent.pistonExtend() {
        if (block.getRelative(direction).hasBonfireBelow())
            isCancelled = true
        else blocks.forEach {
            if (it.getRelative(direction).hasBonfireBelow())
                isCancelled = true
        }

    }

    @EventHandler(ignoreCancelled = true)
    fun BlockPistonRetractEvent.pistonRetract() {
        if (block.getRelative(direction).hasBonfireBelow())
            isCancelled = true
        else blocks.forEach {
            if (it.getRelative(direction).hasBonfireBelow())
                isCancelled = true
        }
    }

    fun Block.hasBonfireBelow(): Boolean {
        return (getRelative(BlockFace.DOWN).state as? Campfire)?.isBonfire == true ||
                (getRelative(BlockFace.DOWN,2).state as? Campfire)?.isBonfire == true
    }

    @EventHandler(ignoreCancelled = true)
    fun EntityChangeBlockEvent.douseBonfire() {
        if (entity is ThrownPotion && (this.block.state as Campfire).isBonfire) isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun PlayerInteractEvent.onWaterlogging() {
        if (action != Action.RIGHT_CLICK_BLOCK || hand != EquipmentSlot.HAND) return
        if (item?.type != Material.WATER_BUCKET) return
        if ((clickedBlock?.state as? Campfire)?.isBonfire == true || (interactionPoint?.block?.state as? Campfire)?.isBonfire == true) isCancelled = true
    }
}
