package com.mineinabyss.bonfire.data

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import java.util.*

object Players : IdTable<UUID>() {
    val playerUUID = uuid("playerUUID").uniqueIndex()
    var bonfireUUID = uuid("bonfireUUID").references(Bonfire.entityUUID)
    override val id: Column<EntityID<UUID>>
        get() = playerUUID.entityId()

}