package com.mineinabyss.bonfire.components

import com.mineinabyss.bonfire.bonfire
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
)
