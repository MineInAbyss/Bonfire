package com.mineinabyss.bonfire.config

import com.mineinabyss.bonfire.ecs.components.SoundEffect
import com.mineinabyss.idofront.serialization.DurationSerializer
import com.mineinabyss.idofront.serialization.SerializableItemStack
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class BonfireConfig(
    val bonfireItem: SerializableItemStack,
    val modelItem: SerializableItemStack,
    val maxPlayerCount: Int,
    val bonfireExpirationTime: @Serializable(with = DurationSerializer::class) Duration,
    val bonfireInteractCooldown: @Serializable(with = DurationSerializer::class) Duration,
    val respawnSetSound: SoundEffect,
    val respawnUnsetSound: SoundEffect,
    val minFallDist: Int,
    val effectRadius: Double,
    val effectStrength: Float,
    val effectRegenRate: Int
)
