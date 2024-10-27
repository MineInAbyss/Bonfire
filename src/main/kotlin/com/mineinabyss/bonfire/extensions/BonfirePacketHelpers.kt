package com.mineinabyss.bonfire.extensions

import com.mineinabyss.blocky.api.BlockyFurnitures.isBlockyFurniture
import com.mineinabyss.blocky.helpers.FurnitureUUID
import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.geary.papermc.tracking.items.ItemTracking
import com.mineinabyss.geary.papermc.withGeary
import com.mineinabyss.idofront.nms.aliases.NMSEntity
import com.mineinabyss.idofront.nms.aliases.toNMS
import it.unimi.dsi.fastutil.ints.IntList
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
    data class BonfireAddonPacket(val addonEntity: Display.ItemDisplay, val addons: List<ClientboundSetEntityDataPacket>) {
        fun bundlePacket(furniture: NMSEntity, bonfireSize: Int) : ClientboundBundlePacket {
            addonEntity.teleportTo(furniture.x, furniture.y, furniture.z)
            return ClientboundBundlePacket(listOf(
                addonEntity.getAddEntityPacket(furniture.`moonrise$getTrackedEntity`().serverEntity),
                addons.elementAtOrNull(bonfireSize)
            ))
        }
    }

    private val bonfireAddons = mutableSetOf<BonfireAddonEntity>()
    private val bonfireAddonPackets = mutableMapOf<FurnitureUUID, BonfireAddonPacket>()

    fun sendAddonPacket(furniture: ItemDisplay) {
        furniture.world.players.filter { it.canSee(furniture) }.forEach { sendAddonPacket(furniture, it) }
    }

    fun sendAddonPacket(furniture: ItemDisplay, player: Player) {
        if (!furniture.isBlockyFurniture) return

        val bonfire = furniture.toGearyOrNull()?.get<Bonfire>() ?: return
        val nmsFurniture = furniture.toNMS()
        val bonfireAddonPacket = bonfireAddonPackets.computeIfAbsent(furniture.uniqueId) {
            val addonEntity = bonfireAddons.firstOrNull { it.furnitureUUID == furniture.uniqueId }?.addonEntity
                ?: BonfireAddonEntity(furniture.uniqueId, Display.ItemDisplay(EntityType.ITEM_DISPLAY, nmsFurniture.level()))
                    .apply(bonfireAddons::add).addonEntity

            val metadataPackets = bonfire.addons.mapNotNull { addon ->
                val item = player.withGeary { getAddon(ItemTracking) }.createItem(addon) ?: return@mapNotNull null
                ClientboundSetEntityDataPacket(addonEntity.id,
                    listOf(SynchedEntityData.DataValue(23, EntityDataSerializers.ITEM_STACK, CraftItemStack.asNMSCopy(item)))
                )
            }

            BonfireAddonPacket(addonEntity, metadataPackets)
        }.bundlePacket(nmsFurniture, bonfire.bonfirePlayers.size)

        (player as CraftPlayer).handle.connection.send(bonfireAddonPacket)
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