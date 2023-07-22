package com.mineinabyss.bonfire.components

import com.mineinabyss.blocky.components.core.BlockyFurniture
import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.geary.prefabs.PrefabKey
import com.mineinabyss.geary.prefabs.serializers.PrefabKeySerializer
import com.mineinabyss.idofront.serialization.DurationSerializer
import com.mineinabyss.idofront.serialization.SerializableItemStack
import com.mineinabyss.idofront.serialization.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.time.Duration

@Serializable
@SerialName("bonfire:bonfire")
data class Bonfire(
    val bonfireOwner: @Serializable(UUIDSerializer::class) UUID? = null,
    val bonfirePlayers: List<@Serializable(UUIDSerializer::class) UUID> = emptyList(),
    val maxPlayerCount: Int = bonfire.config.maxPlayerCount,
    //val bonfireExpirationTime: @Serializable(with = DurationSerializer::class) Duration = bonfire.config.bonfireExpirationTime,
    val states: BonfireState,
) {
    @Serializable
    data class BonfireState(
        val unlit: @Serializable(PrefabKeySerializer::class) PrefabKey,
        val lit: @Serializable(PrefabKeySerializer::class) PrefabKey,
    )
}
