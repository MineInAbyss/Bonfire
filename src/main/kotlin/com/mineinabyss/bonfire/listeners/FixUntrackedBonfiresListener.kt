package com.mineinabyss.bonfire.listeners

import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.bonfire.extensions.updateBonfireState
import com.mineinabyss.geary.papermc.datastore.decodePrefabs
import com.mineinabyss.geary.papermc.datastore.encodePrefabs
import com.mineinabyss.geary.papermc.datastore.hasComponentsEncoded
import com.mineinabyss.geary.papermc.datastore.loadComponentsFrom
import com.mineinabyss.geary.papermc.tracking.entities.events.GearyEntityAddToWorldEvent
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.geary.papermc.withGeary
import com.mineinabyss.geary.prefabs.PrefabKey
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.nexomc.nexo.api.NexoFurniture
import com.nexomc.nexo.mechanics.furniture.FurnitureMechanic
import org.bukkit.Bukkit
import org.bukkit.entity.ItemDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.persistence.PersistentDataType

class FixUntrackedBonfiresListener : Listener {
    private val bonfireItemKey = PrefabKey.of("mineinabyss:bonfire")
    private val bonfireLitItemKey = PrefabKey.of("mineinabyss:bonfire_lit")

    /** Bonfires old enough to predate geary writing prefabs to the base-entity only carry them on the displayed item */
    @EventHandler
    fun ChunkLoadEvent.onAddToWorld() {
        chunk.entities.filterIsInstance<ItemDisplay>().forEach { entity ->
            if (entity.persistentDataContainer.hasComponentsEncoded) return@forEach
            val displayed = entity.itemStack
            val itemPrefabs = entity.withGeary { displayed.persistentDataContainer.decodePrefabs() }
            if (bonfireItemKey !in itemPrefabs && bonfireLitItemKey !in itemPrefabs) return@forEach

            Bukkit.getScheduler().scheduleSyncDelayedTask(bonfire, {
                entity.withGeary {
                    entity.persistentDataContainer.encodePrefabs(itemPrefabs)
                    // Everything this bonfire owns is still on the displayed item, the entity only just got its prefabs
                    entity.toGearyOrNull()?.loadComponentsFrom(displayed.persistentDataContainer)
                }
                entity.adoptAsNexoFurniture()
            }, 1)
        }
    }

    @EventHandler
    fun GearyEntityAddToWorldEvent.onOldBonfireLoad() {
        val itemDisplay = entity as? ItemDisplay ?: return
        if (NexoFurniture.isFurniture(itemDisplay) || !gearyEntity.has<Bonfire>()) return

        Bukkit.getScheduler().scheduleSyncDelayedTask(bonfire, { itemDisplay.adoptAsNexoFurniture() }, 1)
    }

    /**
     * Legacy bonfires were placed as Blocky furniture, which Nexo has no record of.
     * Handing Nexo the entity that is already there, instead of replacing it, keeps the uuid players' respawn points
     * refer to as well as the owner and registered players held in its geary components
     */
    private fun ItemDisplay.adoptAsNexoFurniture() {
        val furnitureId = bonfire.config.nexoFurnitureId
        if (!NexoFurniture.isFurniture(furnitureId)) return bonfire.logger.w(
            "<red>Could not repair the legacy bonfire at ${location.blockX}, ${location.blockY}, ${location.blockZ}, <yellow>$furnitureId</yellow> is not a nexo furniture".miniMsg()
        )

        persistentDataContainer.set(FurnitureMechanic.FURNITURE_KEY, PersistentDataType.STRING, furnitureId)
        NexoFurniture.updateFurniture(this)
        updateBonfireState()
    }
}
