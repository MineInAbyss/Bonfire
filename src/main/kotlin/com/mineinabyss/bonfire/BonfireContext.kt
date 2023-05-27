package com.mineinabyss.bonfire

import com.mineinabyss.bonfire.config.BonfireConfig
import com.mineinabyss.idofront.di.DI
import org.jetbrains.exposed.sql.Database

val bonfire by DI.observe<BonfireContext>()
interface BonfireContext {
    val plugin: BonfirePlugin
    val config: BonfireConfig
    val db: Database
}
