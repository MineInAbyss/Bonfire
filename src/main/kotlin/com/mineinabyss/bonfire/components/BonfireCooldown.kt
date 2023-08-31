package com.mineinabyss.bonfire.components

import com.mineinabyss.idofront.serialization.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
@SerialName("bonfire:cooldown")
data class BonfireCooldown(val bonfire: @Serializable(with = UUIDSerializer::class) UUID)
