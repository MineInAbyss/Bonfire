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
import com.mineinabyss.geary.addon.autoscan
import com.mineinabyss.geary.papermc.dsl.gearyAddon
import com.mineinabyss.idofront.config.IdofrontConfig
import com.mineinabyss.idofront.config.config
import com.mineinabyss.idofront.platforms.Platforms
import com.mineinabyss.idofront.plugin.Services
import com.mineinabyss.idofront.plugin.listeners
import com.mineinabyss.idofront.plugin.service
import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction

val bonfirePlugin: BonfirePlugin by lazy { JavaPlugin.getPlugin(BonfirePlugin::class.java) }

interface BonfireContext {
    companion object : BonfireContext by Services.get()

    val db: Database
}

class BonfirePlugin : JavaPlugin() {
    lateinit var config: IdofrontConfig<BonfireConfig>
    override fun onLoad() {
        Platforms.load(this, "mineinabyss")
    }

    override fun onEnable() {
        config = config("config") { fromPluginPath(loadDefault = true)}
        service<BonfireContext>(object : BonfireContext {
            override val db = Database.connect("jdbc:sqlite:" + dataFolder.path + "/data.db", "org.sqlite.JDBC")
        })


        transaction(BonfireContext.db) {
            addLogger(StdOutSqlLogger)

            SchemaUtils.createMissingTablesAndColumns(Bonfire, Players, MessageQueue)
        }

        server.pluginManager.registerSuspendingEvents(PlayerListener, this)

        listeners(
            BlockListener
        )

        gearyAddon {
            autoscan("com.mineinabyss") {
                all()
            }
        }

        BonfireCommandExecutor

        if (deeperWorld.isEnabled)
            listeners(DWListener)
    }

    override fun onDisable() {
        // Plugin shutdown logic
//        ProtocolLibrary.getProtocolManager().removePacketListener(ChatPacketAdapter);

    }
}
