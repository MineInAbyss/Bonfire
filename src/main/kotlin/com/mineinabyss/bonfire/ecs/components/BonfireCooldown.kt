package com.mineinabyss.bonfire.ecs.components

import com.mineinabyss.idofront.serialization.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
@SerialName("bonfire:cooldown")
class BonfireCooldown(val bonfire: @Serializable(with = UUIDSerializer::class) UUID)
