package com.mineinabyss.bonfire

import com.mineinabyss.bonfire.config.BonfireConfig
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.MessageQueue
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.bonfire.ecs.systems.BonfireEffectSystem
import com.mineinabyss.bonfire.listeners.BlockListener
import com.mineinabyss.bonfire.listeners.DWListener
import com.mineinabyss.bonfire.listeners.PlayerListener
import com.mineinabyss.geary.addon.autoscan
import com.mineinabyss.geary.papermc.dsl.gearyAddon
import com.mineinabyss.idofront.platforms.IdofrontPlatforms
import com.mineinabyss.idofront.plugin.getService
import com.mineinabyss.idofront.plugin.isPluginEnabled
import com.mineinabyss.idofront.plugin.registerEvents
import com.mineinabyss.idofront.plugin.registerService
import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction

val bonfirePlugin: BonfirePlugin by lazy { JavaPlugin.getPlugin(BonfirePlugin::class.java) }

interface BonfireContext {
    companion object : BonfireContext by getService()

    val db: Database
}

class BonfirePlugin : JavaPlugin() {
    override fun onLoad() {
        IdofrontPlatforms.load(this, "mineinabyss")
    }

    override fun onEnable() {
        saveDefaultConfig()
        BonfireConfig.load()
        registerService<BonfireContext>(object : BonfireContext {
            override val db = Database.connect("jdbc:sqlite:" + dataFolder.path + "/data.db", "org.sqlite.JDBC")
        })


        transaction(BonfireContext.db) {
            addLogger(StdOutSqlLogger)

            SchemaUtils.createMissingTablesAndColumns(Bonfire, Players, MessageQueue)
        }

        registerEvents(
            PlayerListener,
            BlockListener
        )

        gearyAddon {
            autoscan("com.mineinabyss") {
                all()
            }
        }

        BonfireCommandExecutor

        if (isPluginEnabled("DeeperWorld")) {
            registerEvents(DWListener)
        }
    }

    override fun onDisable() {
        // Plugin shutdown logic
//        ProtocolLibrary.getProtocolManager().removePacketListener(ChatPacketAdapter);

    }
}
