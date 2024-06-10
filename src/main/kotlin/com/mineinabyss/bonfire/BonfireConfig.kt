package com.mineinabyss.bonfire

import com.mineinabyss.idofront.serialization.DurationSerializer
import com.mineinabyss.idofront.serialization.VectorSerializer
import kotlinx.serialization.Serializable
import org.bukkit.Bukkit
import org.bukkit.SoundCategory
import org.bukkit.util.Vector
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@Serializable
data class BonfireConfig(
    val maxPlayerCount: Int = 4,
    val bonfireExpirationTime: @Serializable(with = DurationSerializer::class) Duration = 7.days,
    val bonfireInteractCooldown: @Serializable(with = DurationSerializer::class) Duration = 2.seconds,
    val respawnSetSound: BonfireSound = BonfireSound("block.ladder.step", 1f, 1.5f, SoundCategory.BLOCKS),
    val respawnUnsetSound: BonfireSound = BonfireSound("block.ladder.step", 1f, 0.5f, SoundCategory.BLOCKS),
    val minFallDist: Int = 3,
    val effectRadius: Double = 3.0,
    val effectStrength: Float = 10f,
    val effectRegenRate: Int = 7,
    val allowSettingBedRespawns: Boolean = false,
    val debugTextOffset: @Serializable(VectorSerializer::class) Vector = Vector(0.0, 1.7, 0.0),
) {
    @Serializable
    data class BonfireSound(
        val sound: String,
        val volume: Float = 1f,
        val pitch: Float = 1f,
        val category: SoundCategory = SoundCategory.BLOCKS
    )
}
