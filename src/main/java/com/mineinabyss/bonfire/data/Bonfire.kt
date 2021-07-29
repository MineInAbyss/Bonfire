package com.mineinabyss.bonfire.data

import com.mineinabyss.bonfire.extensions.location
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import java.util.*

object Bonfire: IdTable<UUID>() {
    val uuid = uuid("uuid").uniqueIndex()
    val location = location("location")
    override val id: Column<EntityID<UUID>>
        get() = uuid.entityId()
}