package com.mineinabyss.bonfire.listeners

import com.mineinabyss.blocky.api.BlockyFurnitures
import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.geary.papermc.datastore.decodePrefabs
import com.mineinabyss.geary.papermc.datastore.hasComponentsEncoded
import com.mineinabyss.geary.prefabs.PrefabKey
import org.bukkit.Bukkit
import org.bukkit.entity.ItemDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent

class FixUntrackedBonfiresListener : Listener {
    private val bonfireItemKey = PrefabKey.of("mineinabyss:bonfire")
    private val bonfireLitItemKey = PrefabKey.of("mineinabyss:bonfire_lit")

    @EventHandler
    fun ChunkLoadEvent.onAddToWorld() {
        chunk.entities.forEach { entity ->
            if (entity !is ItemDisplay) return@forEach
            if (entity.persistentDataContainer.hasComponentsEncoded) return@forEach
            val displayItemPDC = entity.itemStack?.itemMeta?.persistentDataContainer ?: return
            val itemPrefabs = displayItemPDC.decodePrefabs()

            if (itemPrefabs.contains(bonfireItemKey) || itemPrefabs.contains(bonfireLitItemKey)) {
                Bukkit.getScheduler().scheduleSyncDelayedTask(bonfire.plugin, {
                    BlockyFurnitures.placeFurniture(bonfireItemKey, entity.location, entity.yaw)
                    entity.remove()
                }, 1)
            }
        }
    }
}
