package com.mineinabyss.bonfire

import com.mineinabyss.bonfire.components.destroyBonfire
import com.mineinabyss.bonfire.config.BonfireConfig
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.Bonfire.location
import com.mineinabyss.bonfire.data.Bonfire.stateChangedTimestamp
import com.mineinabyss.bonfire.data.Bonfire.timeUntilDestroy
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.bonfire.data.Players.bonfireUUID
import com.mineinabyss.bonfire.extensions.bonfireData
import com.mineinabyss.bonfire.listeners.BlockListener
import com.mineinabyss.bonfire.listeners.PlayerListener
import com.mineinabyss.bonfire.logging.BonfireLogger
import com.mineinabyss.geary.minecraft.dsl.attachToGeary
import com.mineinabyss.idofront.commands.execution.ExperimentalCommandDSL
import com.mineinabyss.idofront.plugin.registerEvents
import com.mineinabyss.idofront.slimjar.LibraryLoaderInjector
import com.okkero.skedule.schedule
import kotlinx.serialization.InternalSerializationApi
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.block.Campfire
import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime


val bonfirePlugin: BonfirePlugin by lazy { JavaPlugin.getPlugin(BonfirePlugin::class.java) }

class BonfirePlugin : JavaPlugin() {

    @InternalSerializationApi
    @ExperimentalCommandDSL
    override fun onEnable() {
        LibraryLoaderInjector.inject(this)
        saveDefaultConfig()
        BonfireConfig.load()

        Database.connect("jdbc:sqlite:" + this.dataFolder.path + "/data.db", "org.sqlite.JDBC")

        transaction {
            addLogger(StdOutSqlLogger)

            SchemaUtils.create(Bonfire, Players)
        }

        registerEvents(
            PlayerListener,
            BlockListener
        )

//        val protocolManager = ProtocolLibrary.getProtocolManager();
//        protocolManager.addPacketListener(ChatPacketAdapter)

        attachToGeary { autoscanComponents() }

        BonfireCommandExecutor

        schedule {
            repeating(BonfireConfig.data.expirationCheckInterval.inTicks)
            while (true) {
                transaction {
                    Bonfire
                        .leftJoin(Players, { entityUUID }, { bonfireUUID })
                        .select { bonfireUUID.isNull() }
                        .forEach {
                            if ((it[stateChangedTimestamp] + it[timeUntilDestroy]) <= LocalDateTime.now()) {
                                (it[location].block.state as? Campfire)?.bonfireData()?.destroyBonfire(true)
                                    ?: return@forEach
                                BonfireLogger.logBonfireExpired(it[location])
                            }
                        }
                }
                yield()
            }
        }

        schedule {
            repeating(20)
            while (true) {
                transaction {
                    Bonfire
                        .leftJoin(Players, { entityUUID }, { bonfireUUID })
                        .selectAll()
                        .forEach { row ->
                            if (!row[location].isChunkLoaded) return@forEach
                            val player = row[location].getNearbyPlayers(BonfireConfig.data.effectRadius)
                                .find { row[Players.playerUUID] == it.uniqueId } ?: return@forEach
                            if ((1..2).random() == 1) {
                                player.location.world.spawnParticle(
                                    Particle.SOUL,
                                    player.location,
                                    1,
                                    0.5,
                                    1.0,
                                    0.5,
                                    0.0
                                )
                            } else {
                                player.location.world.spawnParticle(
                                    Particle.SOUL_FIRE_FLAME,
                                    player.location,
                                    1,
                                    0.5,
                                    1.0,
                                    0.5,
                                    0.0
                                )
                            }
                            player.saturation = BonfireConfig.data.effectStrength
                            player.saturatedRegenRate = BonfireConfig.data.effectRegenRate
                        }
                }
                yield()
            }
        }
    }

    override fun onDisable() {
        // Plugin shutdown logic
        Bukkit.getServer().removeRecipe(NamespacedKey.fromString(BonfireConfig.data.bonfireRecipe.key)!!)
//        ProtocolLibrary.getProtocolManager().removePacketListener(ChatPacketAdapter);

    }
}