package com.mineinabyss.bonfire.components

import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.geary.prefabs.PrefabKey
import com.mineinabyss.geary.prefabs.serializers.PrefabKeySerializer
import com.mineinabyss.idofront.serialization.DurationSerializer
import com.mineinabyss.idofront.serialization.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
) {
    @Serializable
    data class BonfireStates(
        val unlit: @Serializable(PrefabKeySerializer::class) PrefabKey,
        val lit: @Serializable(PrefabKeySerializer::class) PrefabKey,
        val set: @Serializable(PrefabKeySerializer::class) PrefabKey,
    )
}
