package com.mineinabyss.bonfire.data

import org.jetbrains.exposed.dao.id.IntIdTable

object Player: IntIdTable() {
    val playerUUID = uuid("playerUUID").uniqueIndex()
    var bonfireUUID = uuid("bonfireUUID").references(Bonfire.uuid)

    override val primaryKey = PrimaryKey(playerUUID, name = "PK_Player_ID")

}