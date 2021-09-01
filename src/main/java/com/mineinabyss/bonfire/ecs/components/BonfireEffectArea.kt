@file:UseSerializers(UUIDSerializer::class)

package com.mineinabyss.bonfire.ecs.components

import com.mineinabyss.geary.ecs.api.autoscan.AutoscanComponent
import com.mineinabyss.idofront.serialization.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.*

@Serializable
@SerialName("bonfire:effect_area")
@AutoscanComponent
class BonfireEffectArea(
    val uuid: UUID
)