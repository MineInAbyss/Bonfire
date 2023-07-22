package com.mineinabyss.bonfire.components

import com.mineinabyss.idofront.serialization.LocationSerializer
import com.mineinabyss.idofront.serialization.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bukkit.Location
import java.util.UUID

@Serializable
@SerialName("bonfire:bonfire_respawn")
data class BonfireRespawn(
    val bonfireUuid: @Serializable(UUIDSerializer::class) UUID,
    val bonfireLocation: @Serializable(LocationSerializer::class) Location,
)
