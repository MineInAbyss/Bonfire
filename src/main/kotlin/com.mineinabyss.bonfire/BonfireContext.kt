package com.mineinabyss.bonfire

import com.mineinabyss.bonfire.extensions.BonfireMessages
import com.mineinabyss.idofront.di.DI

val bonfire by DI.observe<BonfireContext>()
interface BonfireContext {
    val plugin: BonfirePlugin
    val config: BonfireConfig
    val messages: BonfireMessages
}
