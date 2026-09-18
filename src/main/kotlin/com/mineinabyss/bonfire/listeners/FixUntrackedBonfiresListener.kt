package com.mineinabyss.bonfire.listeners

import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.geary.papermc.datastore.decodePrefabs
import com.mineinabyss.geary.papermc.datastore.hasComponentsEncoded
import com.mineinabyss.geary.papermc.tracking.entities.events.GearyEntityAddToWorldEvent
import com.mineinabyss.geary.papermc.withGeary
import com.mineinabyss.geary.prefabs.PrefabKey
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.nexomc.nexo.api.NexoFurniture
import org.bukkit.Bukkit
import org.bukkit.block.BlockFace
import org.bukkit.entity.ItemDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent

class FixUntrackedBonfiresListener : Listener {
    private val bonfireItemKey = PrefabKey.of("mineinabyss:bonfire")
    private val bonfireLitItemKey = PrefabKey.of("mineinabyss:bonfire_lit")

    @EventHandler
    fun ChunkLoadEvent.onAddToWorld() {
        chunk.entities.filterIsInstance<ItemDisplay>().forEach { entity ->
            if (entity.persistentDataContainer.hasComponentsEncoded) return@forEach
            val itemPrefabs = entity.withGeary { entity.itemStack.persistentDataContainer.decodePrefabs() }

            if (bonfireItemKey in itemPrefabs || bonfireLitItemKey in itemPrefabs) {
                Bukkit.getScheduler().scheduleSyncDelayedTask(bonfire, { entity.replaceWithNexoFurniture() }, 1)
            }
        }
    }

    @EventHandler
    fun GearyEntityAddToWorldEvent.onOldBonfireLoad() {
        val itemDisplay = entity as? ItemDisplay ?: return
        if (NexoFurniture.isFurniture(itemDisplay) || !gearyEntity.has<Bonfire>()) return

        Bukkit.getScheduler().scheduleSyncDelayedTask(bonfire, { itemDisplay.replaceWithNexoFurniture() }, 1)
    }

    /** Only drops the old entity once Nexo accepted the placement, a wrong id would otherwise delete the bonfire */
    private fun ItemDisplay.replaceWithNexoFurniture() {
        val furnitureId = bonfire.config.nexoFurnitureId
        if (NexoFurniture.place(furnitureId, location, yaw, BlockFace.UP) != null) remove()
        else bonfire.logger.w(
            "<red>Could not place nexo furniture <yellow>$furnitureId</yellow> for the untracked bonfire at ${location.blockX}, ${location.blockY}, ${location.blockZ}".miniMsg()
        )
    }
}
