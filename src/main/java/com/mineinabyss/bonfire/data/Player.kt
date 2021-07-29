package com.mineinabyss.bonfire.data

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import java.util.*

object Player: IdTable<UUID>() {
    val playerUUID = uuid("playerUUID").uniqueIndex()
    var bonfireUUID = uuid("bonfireUUID").references(Bonfire.uuid)
    override val id: Column<EntityID<UUID>>
        get() = playerUUID.entityId()

}