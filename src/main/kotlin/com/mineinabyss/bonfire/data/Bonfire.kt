package com.mineinabyss.bonfire.data

import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.bonfire.extensions.location
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.javatime.duration
import java.time.LocalDateTime
import java.util.*
import kotlin.time.toJavaDuration

object Bonfire : IdTable<UUID>() {
    val entityUUID = uuid("entityUUID").uniqueIndex()
    val ownerUUID = uuid("ownerUUID").nullable()
    val location = location("location")
    val stateChangedTimestamp = datetime("stateChangedTimestamp").clientDefault { LocalDateTime.now() }
    val timeUntilDestroy = duration("timeUntilDestroy")
        .default(bonfire.config.bonfireExpirationTime.toJavaDuration())
    override val id: Column<EntityID<UUID>>
        get() = entityUUID.entityId()
}
