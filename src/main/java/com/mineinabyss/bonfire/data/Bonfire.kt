package com.mineinabyss.bonfire.data

import com.mineinabyss.bonfire.extensions.location
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Table
import java.util.*

object Bonfire: IntIdTable() {
    val uuid = uuid("uuid")
    val location = location("location")
}