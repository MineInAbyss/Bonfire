package com.mineinabyss.bonfire.components

import com.charleskorn.kaml.YamlComment
import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.geary.papermc.tracking.items.ItemTrackingModule
import com.mineinabyss.geary.prefabs.PrefabKey
import com.mineinabyss.geary.prefabs.serializers.PrefabKeySerializer
import com.mineinabyss.idofront.serialization.DurationSerializer
import com.mineinabyss.idofront.serialization.UUIDSerializer
import io.papermc.paper.datacomponent.DataComponentTypes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.kyori.adventure.key.Key
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.util.*
import kotlin.time.Duration

@Serializable
@SerialName("bonfire:bonfire")
data class Bonfire(
    val bonfireOwner: @Serializable(UUIDSerializer::class) UUID? = null,
    val bonfirePlayers: MutableList<@Serializable(UUIDSerializer::class) UUID> = mutableListOf(),
    val maxPlayerCount: Int = bonfire.config.maxPlayerCount,
    val bonfireExpirationTime: @Serializable(with = DurationSerializer::class) Duration = bonfire.config.bonfireExpirationTime,
    val states: BonfireStates,
    @YamlComment("Item to spawn in addition to bonfire, mainly for visually representing player-count")
    val addons: List<@Serializable(PrefabKeySerializer::class) PrefabKey> = emptyList()
) {
    @Serializable
    data class BonfireStates(
        val unlit: @Serializable(PrefabKeySerializer::class) PrefabKey,
        val lit: @Serializable(PrefabKeySerializer::class) PrefabKey,
        val set: @Serializable(PrefabKeySerializer::class) PrefabKey,
    ) {

        fun unlitItem(itemTracking: ItemTrackingModule) = itemTracking.createItem(unlit)
            ?: ItemStack.of(Material.PAPER).apply {
                setData(DataComponentTypes.ITEM_MODEL, Key.key(unlit.full))
            }

        fun litItem(itemTracking: ItemTrackingModule) = itemTracking.createItem(lit)
        ?: ItemStack.of(Material.PAPER).apply {
            setData(DataComponentTypes.ITEM_MODEL, Key.key(lit.full))
        }

        fun setItem(itemTracking: ItemTrackingModule) = itemTracking.createItem(set)
        ?: ItemStack.of(Material.PAPER).apply {
            setData(DataComponentTypes.ITEM_MODEL, Key.key(set.full))
        }
    }
}
