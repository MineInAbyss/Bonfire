package com.mineinabyss.bonfire

import com.mineinabyss.idofront.serialization.SerializablePrefabItemService
import kotlinx.serialization.Serializable
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

@Serializable
object BonfireSerializablePrefabItemService : SerializablePrefabItemService {

    override fun encodeFromPrefab(item: ItemStack, meta: ItemMeta, prefabName: String) {
        encodeFromPrefab(item, meta, prefabName)
    }
}
