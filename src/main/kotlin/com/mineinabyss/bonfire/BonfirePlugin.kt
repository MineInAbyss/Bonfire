package com.mineinabyss.bonfire

import com.mineinabyss.bonfire.extensions.BonfireMessages
import com.mineinabyss.bonfire.listeners.BonfireListener
import com.mineinabyss.bonfire.listeners.DebugListener
import com.mineinabyss.bonfire.listeners.PlayerListener
import com.mineinabyss.geary.autoscan.autoscan
import com.mineinabyss.geary.modules.geary
import com.mineinabyss.idofront.config.config
import com.mineinabyss.idofront.di.DI
import com.mineinabyss.idofront.platforms.Platforms
import com.mineinabyss.idofront.plugin.listeners
import org.bukkit.plugin.java.JavaPlugin

class BonfirePlugin : JavaPlugin() {
    override fun onLoad() {
        Platforms.load(this, "mineinabyss")
    }

    override fun onEnable() {
        registerBonfireContext()

        BonfireCommands()

        listeners(PlayerListener(), BonfireListener(), DebugListener())

        geary {
            autoscan(classLoader, "com.mineinabyss.bonfire") {
                all()
            }
        }
    }

    fun registerBonfireContext() {
        DI.remove<BonfireContext>()
        DI.add<BonfireContext>(object : BonfireContext {
            override val plugin = this@BonfirePlugin
            override val config: BonfireConfig by config("config") { fromPluginPath(loadDefault = true) }
            override val messages: BonfireMessages by config("messages") { fromPluginPath(loadDefault = true) }
        })
    }

    override fun onDisable() {
        // Plugin shutdown logic
//        ProtocolLibrary.getProtocolManager().removePacketListener(ChatPacketAdapter);

    }}
