@file:UseSerializers(UUIDSerializer::class)

package com.mineinabyss.bonfire.ecs.components

import com.mineinabyss.geary.ecs.api.autoscan.AutoscanComponent
import com.mineinabyss.idofront.serialization.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.*

@Serializable
@SerialName("bonfire:data")
@AutoscanComponent
class BonfireData(
    var uuid: UUID,
) {
    fun updateUUID(value: UUID) {
        this.uuid = value
    }
}