@file:UseSerializers(UUIDSerializer::class)

package com.mineinabyss.bonfire.components

import com.mineinabyss.idofront.serialization.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.*

@Serializable
@SerialName("bonfire:effect_area")
data class BonfireEffectArea(val uuid: UUID)
