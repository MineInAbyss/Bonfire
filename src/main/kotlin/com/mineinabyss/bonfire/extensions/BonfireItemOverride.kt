package com.mineinabyss.bonfire.extensions

import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.geary.papermc.withGeary
import com.nexomc.nexo.api.FurnitureItemOverride
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData
import org.bukkit.inventory.ItemStack

/**
 * Decides which lit variant of a bonfire each player sees.
 *
 * Nexo asks this every time it sends a bonfire, so the lit state survives it rebuilding the furniture
 * on chunk load, and there is no packet of ours to be overwritten by one of its own.
 */
object BonfireItemOverride : FurnitureItemOverride {
    override fun itemFor(furniture: FurnitureItemOverride.Furniture): ItemStack? {
        val registered = furniture.baseEntity.withGeary { furniture.baseEntity.toGearyOrNull()?.get<Bonfire>() }
            ?.bonfirePlayers?.takeIf { it.isNotEmpty() } ?: return null

        return furniture.item.clone().apply {
            setData(
                DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelData.customModelData()
                    .addFloat(registered.size.toFloat())
                    .addFlag(true)
                    .addFlag(furniture.player.uniqueId in registered)
                    .build()
            )
        }
    }
}
