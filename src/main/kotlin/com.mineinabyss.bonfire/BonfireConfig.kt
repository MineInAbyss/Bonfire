package com.mineinabyss.bonfire

import com.mineinabyss.idofront.serialization.DurationSerializer
import kotlinx.serialization.Serializable
import org.bukkit.SoundCategory
import java.io.Serial
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
data class BonfireConfig(
    val maxPlayerCount: Int = 4,
    val bonfireExpirationTime: @Serializable(with = DurationSerializer::class) Duration,
    val bonfireInteractCooldown: @Serializable(with = DurationSerializer::class) Duration = 2.seconds,
    val respawnSetSound: BonfireSound,
    val respawnUnsetSound: BonfireSound,
    val minFallDist: Int = 3,
    val effectRadius: Double = 3.0,
    val effectStrength: Float = 10f,
    val effectRegenRate: Int = 7
) {
    @Serializable
    data class BonfireSound(
        val sound: String,
        val volume: Float = 1f,
        val pitch: Float = 1f,
        val category: SoundCategory = SoundCategory.BLOCKS
    )
}
