package com.mineinabyss.bonfire

import com.github.shynixn.mccoroutine.bukkit.registerSuspendingEvents
import com.mineinabyss.bonfire.config.BonfireConfig
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.MessageQueue
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.bonfire.listeners.BlockListener
import com.mineinabyss.bonfire.listeners.DWListener
import com.mineinabyss.bonfire.listeners.PlayerListener
import com.mineinabyss.deeperworld.deeperWorld
import com.mineinabyss.geary.autoscan.autoscan
import com.mineinabyss.geary.modules.geary
import com.mineinabyss.idofront.config.config
import com.mineinabyss.idofront.di.DI
import com.mineinabyss.idofront.platforms.Platforms
import com.mineinabyss.idofront.plugin.listeners
import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction

class BonfirePlugin : JavaPlugin() {
    override fun onLoad() {
        Platforms.load(this, "mineinabyss")
    }

    override fun onEnable() {
        registerBonfireContext()

        transaction(bonfire.db) {
            addLogger(StdOutSqlLogger)

            SchemaUtils.createMissingTablesAndColumns(Bonfire, Players, MessageQueue)
        }

        server.pluginManager.registerSuspendingEvents(PlayerListener, this)

        listeners(BlockListener)

        geary {
            autoscan(classLoader, "com.mineinabyss.bonfire") {
                all()
            }
        }

        BonfireCommandExecutor

        if (deeperWorld.isEnabled)
            listeners(DWListener)
    }

    fun registerBonfireContext() {
        DI.remove<BonfireContext>()
        DI.add<BonfireContext>(object : BonfireContext {
            override val plugin = this@BonfirePlugin
            override val config: BonfireConfig by config("config") { fromPluginPath(loadDefault = true) }
            override val db = Database.connect("jdbc:sqlite:" + dataFolder.path + "/data.db", "org.sqlite.JDBC")
        })
    }

    override fun onDisable() {
        // Plugin shutdown logic
//        ProtocolLibrary.getProtocolManager().removePacketListener(ChatPacketAdapter);

    }
}
