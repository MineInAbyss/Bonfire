package com.mineinabyss.bonfire

import com.mineinabyss.bonfire.config.BonfireConfig
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.Bonfire.location
import com.mineinabyss.bonfire.data.Bonfire.stateChangedTimestamp
import com.mineinabyss.bonfire.data.Bonfire.timeUntilDestroy
import com.mineinabyss.bonfire.data.Bonfire.uuid
import com.mineinabyss.bonfire.extensions.bonfireData
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.bonfire.listeners.BlockListener
import com.mineinabyss.bonfire.listeners.PlayerListener
import com.mineinabyss.geary.minecraft.dsl.attachToGeary
import com.mineinabyss.idofront.messaging.broadcast
import com.mineinabyss.idofront.plugin.registerEvents
import com.mineinabyss.idofront.slimjar.LibraryLoaderInjector
import com.okkero.skedule.schedule
import kotlinx.serialization.InternalSerializationApi
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Campfire
import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime


val bonfirePlugin: BonfirePlugin by lazy { JavaPlugin.getPlugin(BonfirePlugin::class.java) }

class BonfirePlugin : JavaPlugin() {

    @InternalSerializationApi
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

        schedule {
            repeating(BonfireConfig.data.campfireDestroyCheckInterval.inTicks)
            while(true) {
                transaction {
                    Bonfire
                        .leftJoin(Players, { uuid }, { bonfireUUID })
                        .select { Players.bonfireUUID.isNull() }
                        .forEach {
                            if ((it[stateChangedTimestamp] + it[timeUntilDestroy]) <= LocalDateTime.now()) {
                                if (it[location].block.state is Campfire) {
                                    it[location].block.type = Material.AIR //FIXME: is this the correct way to destroy a block?
                                    Bukkit.getEntity(it[uuid])?.remove() //FIXME: does the ECS need cleanup?

                                    Bonfire.deleteWhere { uuid eq it[uuid] }
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
        Bukkit.getServer().removeRecipe(NamespacedKey.fromString(BonfireConfig.data.bonfireRecipe.key)!!)
//        ProtocolLibrary.getProtocolManager().removePacketListener(ChatPacketAdapter);

    }
}