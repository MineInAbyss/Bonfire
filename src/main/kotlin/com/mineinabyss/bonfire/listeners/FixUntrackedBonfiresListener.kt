package com.mineinabyss.bonfire.listeners

import com.mineinabyss.blocky.api.BlockyFurnitures
import com.mineinabyss.blocky.components.core.BlockyFurniture
import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.geary.papermc.datastore.decodePrefabs
import com.mineinabyss.geary.papermc.datastore.hasComponentsEncoded
import com.mineinabyss.geary.papermc.tracking.entities.events.GearyEntityAddToWorldEvent
import com.mineinabyss.geary.papermc.withGeary
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
        chunk.entities.filterIsInstance<ItemDisplay>().forEach { entity ->
            if (entity.persistentDataContainer.hasComponentsEncoded) return@forEach
            val itemPrefabs = entity.withGeary { entity.itemStack.persistentDataContainer.decodePrefabs() }

            if (bonfireItemKey in itemPrefabs || bonfireLitItemKey in itemPrefabs) {
                Bukkit.getScheduler().scheduleSyncDelayedTask(bonfire.plugin, {
                    BlockyFurnitures.placeFurniture(bonfireItemKey, entity.location, entity.yaw)
                    entity.remove()
                }, 1)
            }
        }
    }

    @EventHandler
    fun GearyEntityAddToWorldEvent.onOldBonfireLoad() {
        if (entity !is ItemDisplay || gearyEntity.has<BlockyFurniture>() || !gearyEntity.has<Bonfire>()) return

        Bukkit.getScheduler().scheduleSyncDelayedTask(bonfire.plugin, {
            BlockyFurnitures.placeFurniture(bonfireItemKey, entity.location, entity.yaw)
            entity.remove()
        }, 1)
    }
}
