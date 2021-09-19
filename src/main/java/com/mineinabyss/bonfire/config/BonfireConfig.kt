package com.mineinabyss.bonfire.config

import com.mineinabyss.bonfire.bonfirePlugin
import com.mineinabyss.bonfire.ecs.components.SoundEffect
import com.mineinabyss.idofront.config.IdofrontConfig
import com.mineinabyss.idofront.serialization.SerializableItemStack
import com.mineinabyss.idofront.time.TimeSpan
import com.mineinabyss.idofront.time.days
import com.mineinabyss.idofront.time.weeks
import kotlinx.serialization.Serializable
import org.bukkit.Material

object BonfireConfig : IdofrontConfig<BonfireConfig.Data>(bonfirePlugin, Data.serializer()) {
    @Serializable
    data class Data(
        var bonfireItem: SerializableItemStack,
        var modelItem: SerializableItemStack = SerializableItemStack(Material.WOODEN_SHOVEL, 1, 1),
        var maxPlayerCount: Int = 4,
        var bonfireExpirationTime: TimeSpan = 1.weeks,
        var expirationCheckInterval: TimeSpan = 1.days,
        var respawnSetSound: SoundEffect,
        var respawnUnsetSound: SoundEffect,
        var minFallDist: Int = 3,
        var effectRadius: Double = 3.0,
        var effectStrength: Float = 10f,
        var effectRegenRate: Int = 7
    )
}