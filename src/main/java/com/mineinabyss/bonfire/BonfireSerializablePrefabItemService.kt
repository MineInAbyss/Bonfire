package com.mineinabyss.bonfire

import com.mineinabyss.geary.prefabs.PrefabKey
import com.mineinabyss.idofront.serialization.SerializablePrefabItemService
import com.mineinabyss.looty.LootyFactory
import kotlinx.serialization.Serializable
import org.bukkit.inventory.ItemStack

@Serializable
object BonfireSerializablePrefabItemService : SerializablePrefabItemService {
    override fun prefabToItem(prefabName: String): ItemStack? =
        LootyFactory.createFromPrefab(PrefabKey.of(prefabName))
}
