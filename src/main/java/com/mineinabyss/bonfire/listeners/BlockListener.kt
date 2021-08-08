package com.mineinabyss.bonfire.listeners

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent
import com.mineinabyss.bonfire.Permissions
import com.mineinabyss.bonfire.components.destroyBonfire
import com.mineinabyss.bonfire.components.updateModel
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.bonfire.extensions.*
import com.mineinabyss.bonfire.logging.BonfireLogger
import com.mineinabyss.idofront.items.editItemMeta
import com.mineinabyss.idofront.messaging.error
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Campfire
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.math.floor
import org.bukkit.block.data.type.Campfire as CampfireBlockData

object BlockListener : Listener {

    @EventHandler
    fun BlockPlaceEvent.place() {
        if(blockPlaced.hasBonfireBelow()){
            isCancelled = true
            return
        }

        if (
            itemInHand.type === Material.CAMPFIRE
            && itemInHand.itemMeta.hasCustomModelData()
            && itemInHand.itemMeta.customModelData == 1
        ) {
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
            campfireData.isLit = false
            respawnCampfire.blockData = campfireData

            // Spawn armor stand
            val armorStand = blockPlaced.location.world.spawnEntity(
                blockPlaced.location.toCenterLocation().apply { this.y = floor(y) }, EntityType.ARMOR_STAND
            ) as ArmorStand
            armorStand.setGravity(false)
            armorStand.isInvulnerable = true
            armorStand.isInvisible = true
            armorStand.isPersistent = true
            armorStand.isSmall = true
            armorStand.isMarker = true
            armorStand.setBonfireModel()
            // TODO: Add to config
            val modelStick = (ItemStack(Material.WOODEN_SHOVEL))
            val modelStickMeta = modelStick.itemMeta
            modelStickMeta.setCustomModelData(1)
            modelStick.itemMeta = modelStickMeta
            armorStand.equipment?.helmet = modelStick.editItemMeta { setCustomModelData(1) }

            respawnCampfire.run {
                makeBonfire(armorStand.uniqueId)
                update()
            }

            BonfireLogger.logBonfirePlace(blockPlaced.location, player)
        }
    }

    @EventHandler
    fun BlockBreakEvent.breakBlock() {
        if (block.type != Material.CAMPFIRE && block.type != Material.SOUL_CAMPFIRE) return

        val bonfire = (block.state as Campfire).bonfireData() ?: return

        transaction {
            val hasRegisteredPlayers = Players.select { Players.bonfireUUID eq bonfire.uuid }.any()

            if(player.hasPermission(Permissions.BREAK_BONFIRE_PERMISSION) || !hasRegisteredPlayers){
                bonfire.destroyBonfire(false)
                BonfireLogger.logBonfireBreak(block.location, player)
            }
            else{
                player.error("You cannot break this bonfire, unkindled one")
                isCancelled = true
            }
        }
    }

    @EventHandler
    fun EntityAddToWorldEvent.load() {
        val armorStand = entity as? ArmorStand ?: return
        if (armorStand.isBonfireModel()) {
            if(armorStand.location.block.state !is Campfire){
                entity.remove()
            }
            else{
                val campfire = entity.location.block.state as Campfire
                campfire.bonfireData()?.updateModel()
            }
        }
    }

    @EventHandler
    fun BlockPistonExtendEvent.pistonExtend() {
        if(block.getRelative(direction).hasBonfireBelow()){
            isCancelled = true
            return
        }

        blocks.forEach {
            if(it.getRelative(direction).hasBonfireBelow()){
                isCancelled = true
                return
            }
        }
    }

    @EventHandler
    fun BlockPistonRetractEvent.pistonRetract() {
        if(block.getRelative(direction).hasBonfireBelow()){
            isCancelled = true
            return
        }

        blocks.forEach {
            if(it.getRelative(direction).hasBonfireBelow()){
                isCancelled = true
                return
            }
        }
    }

    private fun Block.hasBonfireBelow() : Boolean {
        val blockBelow = getRelative(BlockFace.DOWN)
        val blockBelowBelowBlock = blockBelow.getRelative(BlockFace.DOWN)

        return ((blockBelow.state is Campfire && (blockBelow.state as Campfire).isBonfire) || (blockBelowBelowBlock.state is Campfire && (blockBelowBelowBlock.state as Campfire).isBonfire))
    }

    //TODO: Prevent splash potions from extinguishing bonfires, might require some NMS magic...

}