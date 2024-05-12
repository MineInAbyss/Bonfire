package com.mineinabyss.bonfire

import com.mineinabyss.bonfire.extensions.BonfireMessages
import com.mineinabyss.bonfire.listeners.BonfireListener
import com.mineinabyss.bonfire.listeners.DebugListener
import com.mineinabyss.bonfire.listeners.FixUntrackedBonfiresListener
import com.mineinabyss.bonfire.listeners.PlayerListener
import com.mineinabyss.geary.autoscan.autoscan
import com.mineinabyss.geary.modules.geary
import com.mineinabyss.idofront.config.config
import com.mineinabyss.idofront.di.DI
import com.mineinabyss.idofront.messaging.ComponentLogger
import com.mineinabyss.idofront.messaging.observeLogger
import com.mineinabyss.idofront.plugin.listeners
import org.bukkit.plugin.java.JavaPlugin

class BonfirePlugin : JavaPlugin() {
    override fun onLoad() {
        registerBonfireContext()
        geary {
            autoscan(classLoader, "com.mineinabyss.bonfire") {
                all()
            }
        }
    }

    override fun onEnable() {
        BonfireCommands()

        listeners(
            PlayerListener(),
            BonfireListener(),
            DebugListener(),
            FixUntrackedBonfiresListener()
        )

    }

    fun registerBonfireContext() {
        DI.remove<BonfireContext>()
        DI.add<BonfireContext>(object : BonfireContext {
            override val plugin = this@BonfirePlugin
            override val config: BonfireConfig by config("config", dataFolder.toPath(), BonfireConfig())
            override val messages: BonfireMessages by config("messages", dataFolder.toPath(), BonfireMessages())
            override val logger: ComponentLogger by plugin.observeLogger()
        })
    }
}
