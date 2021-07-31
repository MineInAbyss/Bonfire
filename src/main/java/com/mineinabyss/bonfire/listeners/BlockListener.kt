package com.mineinabyss.bonfire.listeners

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent
import com.mineinabyss.bonfire.components.destroyBonfire
import com.mineinabyss.bonfire.components.updateModel
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.bonfire.extensions.bonfireData
import com.mineinabyss.bonfire.extensions.isBonfire
import com.mineinabyss.bonfire.extensions.makeBonfire
import com.mineinabyss.bonfire.extensions.setBonfireModel
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
                player.error("There is not enough room.")
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
        }
    }

    @EventHandler
    fun BlockBreakEvent.breakBlock() {
        if (block.type != Material.CAMPFIRE && block.type != Material.SOUL_CAMPFIRE) return

        val bonfire = (block.state as Campfire).bonfireData() ?: return

        transaction {
            val playerCount = Players.select { Players.bonfireUUID eq bonfire.uuid }.count()

            if (playerCount > 0) {
                player.error("You can not break this bonfire rekindled one")
                isCancelled = true
                return@transaction
            }

            bonfire.destroyBonfire(false)
        }
    }

    @EventHandler
    fun EntityAddToWorldEvent.load() {
        if (entity !is ArmorStand) return
        if (entity.location.block.state !is Campfire) {
            entity.remove()
            return
        }
        val campfire = entity.location.block.state as Campfire
        campfire.bonfireData()?.updateModel()
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