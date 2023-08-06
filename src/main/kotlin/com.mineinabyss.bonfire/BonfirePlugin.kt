package com.mineinabyss.bonfire

import com.comphenix.protocol.ProtocolLibrary
import com.mineinabyss.bonfire.listeners.BonfireListener
import com.mineinabyss.bonfire.listeners.PlayerListener
import com.mineinabyss.geary.autoscan.autoscan
import com.mineinabyss.geary.modules.geary
import com.mineinabyss.idofront.config.config
import com.mineinabyss.idofront.di.DI
import com.mineinabyss.idofront.platforms.Platforms
import com.mineinabyss.idofront.plugin.listeners
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class BonfirePlugin : JavaPlugin() {
    override fun onLoad() {
        Platforms.load(this, "mineinabyss")
    }

    override fun onEnable() {
        registerBonfireContext()

        BonfireCommands()

        listeners(PlayerListener(), BonfireListener())

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
        })
    }

    override fun onDisable() {
        // Plugin shutdown logic
//        ProtocolLibrary.getProtocolManager().removePacketListener(ChatPacketAdapter);

    }}
