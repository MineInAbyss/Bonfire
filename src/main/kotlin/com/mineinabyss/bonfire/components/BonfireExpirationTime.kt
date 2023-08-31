package com.mineinabyss.bonfire.components

import com.mineinabyss.idofront.serialization.DurationSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
@SerialName("bonfire:expiration_time")
data class BonfireExpirationTime(
    val totalUnlitTime: @Serializable(DurationSerializer::class) Duration,
    val lastUnlitTimeStamp: Long,
)
