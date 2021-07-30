package com.mineinabyss.bonfire.data

import com.mineinabyss.bonfire.config.BonfireConfig
import com.mineinabyss.bonfire.extensions.javaDuration
import com.mineinabyss.bonfire.extensions.location
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.`java-time`.CurrentDateTime
import org.jetbrains.exposed.sql.`java-time`.datetime
import org.jetbrains.exposed.sql.`java-time`.duration
import java.util.*

object Bonfire : IdTable<UUID>() {
    val uuid = uuid("uuid").uniqueIndex()
    val location = location("location")
    val stateChangedTimestamp = datetime("stateChangedTimestamp").defaultExpression(CurrentDateTime())
    val timeUntilDestroy =
        duration("timeUntilDestroy").default(BonfireConfig.data.timeUntilcampfireDespawn.javaDuration())
    override val id: Column<EntityID<UUID>>
        get() = uuid.entityId()
}