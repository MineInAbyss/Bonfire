package com.mineinabyss.bonfire.listeners

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent
import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.bonfire.BonfireContext
import com.mineinabyss.bonfire.Permissions
import com.mineinabyss.bonfire.bonfirePlugin
import com.mineinabyss.bonfire.config.BonfireConfig
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.Bonfire.ownerUUID
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.bonfire.extensions.*
import com.mineinabyss.bonfire.logging.BonfireLogger
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.time.ticks
import kotlinx.coroutines.delay
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Campfire
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
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
import kotlin.math.floor
import org.bukkit.block.data.type.Campfire as CampfireBlockData

object BlockListener : Listener {

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun BlockPlaceEvent.place() {
        if (blockPlaced.hasBonfireBelow()) {
            isCancelled = true
            return
        }

        itemInHand.isSimilar(BonfireConfig.data.bonfireItem.toItemStack()) || return

        if (blockPlaced.getRelative(BlockFace.UP).type != Material.AIR ||
            blockPlaced.getRelative(BlockFace.UP, 2).type != Material.AIR
        ) {
            player.error("There is not enough space here for a bonfire.")
            isCancelled = true
            return
        }

        // If we are trying to place our custom campfire
        // we need to save this as a respawn campfire instead of just a regular campfire
        val respawnCampfire = blockPlaced.state as Campfire
        val campfireData = blockPlaced.blockData as CampfireBlockData
        respawnCampfire.blockData = campfireData.apply { isLit = false }

        // Spawn armor stand
        val armorStand = (blockPlaced.location.world.spawnEntity(
            blockPlaced.location.toCenterLocation().apply { this.y = floor(y) }, EntityType.ARMOR_STAND
        ) as ArmorStand).setDefaults()

        respawnCampfire.createBonfire(armorStand.uniqueId, player.uniqueId)

        BonfireLogger.logBonfirePlace(blockPlaced.location, player)
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun BlockBreakEvent.breakBlock() {
        block.type == Material.CAMPFIRE || return

        val campfire = (block.state as Campfire)
        campfire.isBonfire || return

        transaction(BonfireContext.db) {
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
    fun EntityAddToWorldEvent.load() {
        val armorStand = entity as? ArmorStand ?: return
        if (!armorStand.isBonfireModel()) return

        val campfire = entity.location.block.state as? Campfire ?: return entity.remove()
        if (campfire.uuid != armorStand.uniqueId) entity.remove()

        campfire.updateBonfire()
    }

    @EventHandler
    fun EntitiesLoadEvent.load() {
        if (chunk.isLoaded && chunk.isEntitiesLoaded) {
            entities.filterIsInstance<ArmorStand>().filter { it.isMarker && it.isBonfireModel() }.forEach {
                val campfire = it.location.block.state as? Campfire ?: return it.remove()
                if (campfire.uuid != it.uniqueId) it.remove()
                else campfire.updateBonfire()
            }
        }
    }

    @EventHandler
    fun BlockCookEvent.cook() {
        val campfire = block.state as? Campfire ?: return
        bonfirePlugin.launch {
            delay(1.ticks)
            campfire.updateFire()
        }
    }

    @EventHandler
    fun BlockPistonExtendEvent.pistonExtend() {
        if (block.getRelative(direction).hasBonfireBelow()) {
            isCancelled = true
            return
        }

        blocks.forEach {
            if (it.getRelative(direction).hasBonfireBelow()) {
                isCancelled = true
                return
            }
        }
    }

    @EventHandler
    fun BlockPistonRetractEvent.pistonRetract() {
        if (block.getRelative(direction).hasBonfireBelow()) {
            isCancelled = true
            return
        }

        blocks.forEach {
            if (it.getRelative(direction).hasBonfireBelow()) {
                isCancelled = true
                return
            }
        }
    }

    fun Block.hasBonfireBelow(): Boolean {
        val blockBelow = getRelative(BlockFace.DOWN)
        val blockBelowBelowBlock = blockBelow.getRelative(BlockFace.DOWN)

        return ((blockBelow.state is Campfire && (blockBelow.state as Campfire).isBonfire) || (blockBelowBelowBlock.state is Campfire && (blockBelowBelowBlock.state as Campfire).isBonfire))
    }

    @EventHandler
    fun EntityChangeBlockEvent.douseBonfire() {
        if (entity is ThrownPotion && (this.block.state as Campfire).isBonfire) isCancelled = true
    }

    @EventHandler
    fun PlayerInteractEvent.onWaterlogging() {
        if (action != Action.RIGHT_CLICK_BLOCK) return
        if (hand != EquipmentSlot.HAND) return
        if (((clickedBlock?.state as? Campfire)?.isBonfire == true ||
            (interactionPoint?.block?.state as? Campfire)?.isBonfire == true) &&
            item?.type == Material.WATER_BUCKET
        ) isCancelled = true
    }
}
