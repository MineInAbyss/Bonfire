package com.mineinabyss.bonfire.extensions

import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.blocky.api.BlockyFurnitures.isBlockyFurniture
import com.mineinabyss.blocky.helpers.FurnitureUUID
import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.geary.papermc.tracking.items.gearyItems
import com.mineinabyss.idofront.nms.aliases.toNMS
import com.mineinabyss.idofront.time.ticks
import it.unimi.dsi.fastutil.ints.IntList
import kotlinx.coroutines.delay
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityType
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player

object BonfirePacketHelpers {

    data class BonfireAddonEntity(val furnitureUUID: FurnitureUUID, val addonEntity: Display.ItemDisplay)
    data class BonfireAddonPacket(val addonEntity: Display.ItemDisplay, val addEntity: ClientboundAddEntityPacket, val addons: List<ClientboundSetEntityDataPacket>) {
        fun bundlePacket(bonfireSize: Int) : ClientboundBundlePacket {
            return ClientboundBundlePacket(listOf(addEntity, addons.elementAtOrNull(bonfireSize)))
        }
    }

    private val bonfireAddons = mutableSetOf<BonfireAddonEntity>()
    private val bonfireAddonPackets = mutableMapOf<FurnitureUUID, BonfireAddonPacket>()

    fun sendAddonPacket(furniture: ItemDisplay) {
        furniture.world.players.filter { it.canSee(furniture) }.forEach { sendAddonPacket(furniture, it) }
    }

    fun sendAddonPacket(furniture: ItemDisplay, player: Player) {
        if (!furniture.isBlockyFurniture) return

        val plugin = bonfire.plugin
        val bonfire = furniture.toGearyOrNull()?.get<Bonfire>() ?: return
        val nmsFurniture = furniture.toNMS()
        val nmsWorld = nmsFurniture.level()
        bonfireAddonPackets.computeIfAbsent(furniture.uniqueId) {
            val addonEntity = bonfireAddons.firstOrNull { it.furnitureUUID == furniture.uniqueId }?.addonEntity
                ?: BonfireAddonEntity(furniture.uniqueId, Display.ItemDisplay(EntityType.ITEM_DISPLAY, nmsWorld))
                    .apply(bonfireAddons::add).addonEntity.apply { teleportTo(furniture.x, furniture.y, furniture.z) }

            val entityPacket = ClientboundAddEntityPacket(addonEntity, nmsFurniture.`moonrise$getTrackedEntity`().serverEntity)
            val metadataPackets = bonfire.addons.mapNotNull { addon ->
                val item = gearyItems.createItem(addon) ?: return@mapNotNull null
                ClientboundSetEntityDataPacket(addonEntity.id,
                    listOf(SynchedEntityData.DataValue(23, EntityDataSerializers.ITEM_STACK, CraftItemStack.asNMSCopy(item)))
                )
            }
            BonfireAddonPacket(addonEntity, entityPacket, metadataPackets)
        }.bundlePacket(bonfire.bonfirePlayers.size).let {
            plugin.launch {
                delay(2.ticks)
                (player as CraftPlayer).handle.connection.send(it)
            }
        }
    }

    fun removeAddonPacket(furniture: ItemDisplay) {
        furniture.world.players.filter { it.canSee(furniture) }.forEach {
            removeAddonPacket(furniture, it)
        }
        bonfireAddons.removeIf { it.furnitureUUID == furniture.uniqueId }
        bonfireAddonPackets.remove(furniture.uniqueId)
    }

    fun removeAddonPacket(furniture: ItemDisplay, player: Player) {
        val addonEntity = bonfireAddons.firstOrNull { it.furnitureUUID == furniture.uniqueId }?.addonEntity ?: return
        (player as CraftPlayer).handle.connection.send(ClientboundRemoveEntitiesPacket(IntList.of(addonEntity.id)))
    }
}