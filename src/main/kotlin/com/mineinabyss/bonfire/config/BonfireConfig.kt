package com.mineinabyss.bonfire.config

import com.mineinabyss.bonfire.ecs.components.SoundEffect
import com.mineinabyss.idofront.serialization.DurationSerializer
import com.mineinabyss.idofront.serialization.SerializableItemStack
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class BonfireConfig(
    var bonfireItem: SerializableItemStack,
    var modelItem: SerializableItemStack,
    var maxPlayerCount: Int,
    var bonfireExpirationTime: @Serializable(with = DurationSerializer::class) Duration,
    var bonfireInteractCooldown: @Serializable(with = DurationSerializer::class) Duration,
    var respawnSetSound: SoundEffect,
    var respawnUnsetSound: SoundEffect,
    var minFallDist: Int,
    var effectRadius: Double,
    var effectStrength: Float,
    var effectRegenRate: Int
)
