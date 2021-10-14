package com.mineinabyss.bonfire

import com.mineinabyss.bonfire.config.BonfireConfig
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.Bonfire.location
import com.mineinabyss.bonfire.data.Bonfire.stateChangedTimestamp
import com.mineinabyss.bonfire.data.Bonfire.timeUntilDestroy
import com.mineinabyss.bonfire.data.MessageQueue
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.bonfire.data.Players.bonfireUUID
import com.mineinabyss.bonfire.ecs.systems.BonfireEffectSystem
import com.mineinabyss.bonfire.extensions.destroy
import com.mineinabyss.bonfire.listeners.BlockListener
import com.mineinabyss.bonfire.listeners.DWListener
import com.mineinabyss.bonfire.listeners.PlayerListener
import com.mineinabyss.bonfire.logging.BonfireLogger
import com.mineinabyss.geary.minecraft.dsl.gearyAddon
import com.mineinabyss.idofront.commands.execution.ExperimentalCommandDSL
import com.mineinabyss.idofront.plugin.isPluginEnabled
import com.mineinabyss.idofront.plugin.registerEvents
import com.mineinabyss.idofront.plugin.registerService
import com.mineinabyss.idofront.serialization.SerializablePrefabItemService
import com.mineinabyss.idofront.slimjar.IdofrontSlimjar
import com.okkero.skedule.schedule
import kotlinx.serialization.InternalSerializationApi
import org.bukkit.block.Campfire
import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

val bonfirePlugin: BonfirePlugin by lazy { JavaPlugin.getPlugin(BonfirePlugin::class.java) }
var pauseExpirationChecks = false

class BonfirePlugin : JavaPlugin() {

    @InternalSerializationApi
    @ExperimentalCommandDSL
    override fun onEnable() {
        IdofrontSlimjar.loadToLibraryLoader(this)
        saveDefaultConfig()
        BonfireConfig.load()

        Database.connect("jdbc:sqlite:" + this.dataFolder.path + "/data.db", "org.sqlite.JDBC")

        transaction {
            addLogger(StdOutSqlLogger)

            SchemaUtils.createMissingTablesAndColumns(Bonfire, Players, MessageQueue)
        }

        registerEvents(
            PlayerListener,
            BlockListener
        )

        gearyAddon {
            autoscanComponents()
            systems(
                BonfireEffectSystem()
            )
        }

        BonfireCommandExecutor

        if (isPluginEnabled("Looty")) {
            registerService<SerializablePrefabItemService>(BonfireSerializablePrefabItemService)
        }

        if (isPluginEnabled("DeeperWorld")) {
            registerEvents(DWListener)
        }

        schedule {
            repeating(BonfireConfig.data.expirationCheckInterval.inTicks)
            while (true) {
                if(!pauseExpirationChecks) {
                    transaction {
                        Bonfire
                            .leftJoin(Players, { entityUUID }, { bonfireUUID })
                            .select { bonfireUUID.isNull() }
                            .forEach {
                                if ((it[stateChangedTimestamp] + it[timeUntilDestroy]) <= LocalDateTime.now()) {
                                    (it[location].block.state as? Campfire)?.destroy(true)
                                        ?: return@forEach
                                    BonfireLogger.logBonfireExpired(it[location])
                                }
                            }
                    }
                }
                yield()
            }
        }
    }

    override fun onDisable() {
        // Plugin shutdown logic
//        ProtocolLibrary.getProtocolManager().removePacketListener(ChatPacketAdapter);

    }
}