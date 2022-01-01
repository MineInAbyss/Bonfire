@file:UseSerializers(DurationSerializer::class)

package com.mineinabyss.bonfire.config

import com.mineinabyss.bonfire.bonfirePlugin
import com.mineinabyss.bonfire.ecs.components.SoundEffect
import com.mineinabyss.idofront.config.IdofrontConfig
import com.mineinabyss.idofront.serialization.DurationSerializer
import com.mineinabyss.idofront.serialization.SerializableItemStack
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bukkit.Material
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

object BonfireConfig : IdofrontConfig<BonfireConfig.Data>(bonfirePlugin, Data.serializer()) {
    @Serializable
    data class Data(
        var bonfireItem: SerializableItemStack,
        var modelItem: SerializableItemStack = SerializableItemStack(Material.WOODEN_SHOVEL, 1, 1),
        var maxPlayerCount: Int = 4,
        var bonfireExpirationTime: Duration = 7.days,
        var respawnSetSound: SoundEffect,
        var respawnUnsetSound: SoundEffect,
        var minFallDist: Int = 3,
        var effectRadius: Double = 3.0,
        var effectStrength: Float = 10f,
        var effectRegenRate: Int = 7
    )
}
