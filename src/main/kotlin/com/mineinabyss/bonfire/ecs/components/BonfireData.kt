@file:UseSerializers(UUIDSerializer::class)

package com.mineinabyss.bonfire.ecs.components

import com.mineinabyss.geary.datatypes.GearyEntity
import com.mineinabyss.idofront.serialization.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.*

@Serializable
@SerialName("bonfire:data")
class BonfireData(
    val uuid: @Serializable(UUIDSerializer::class) UUID,
) {
    fun GearyEntity.updateUUID(value: UUID) = setPersisting(BonfireData(value))
}
