package com.mineinabyss.bonfire

import com.comphenix.protocol.ProtocolLibrary
import com.mineinabyss.bonfire.config.BonfireConfig
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.Player
import com.mineinabyss.bonfire.listeners.BlockListener
import com.mineinabyss.bonfire.listeners.PlayerListener
import com.mineinabyss.bonfire.listeners.ChatPacketAdapter
import com.mineinabyss.geary.minecraft.dsl.attachToGeary
import com.mineinabyss.idofront.plugin.registerEvents
import com.mineinabyss.idofront.slimjar.LibraryLoaderInjector
import kotlinx.serialization.InternalSerializationApi
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction


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

            SchemaUtils.create (Bonfire, Player)
        }

        registerEvents(
            PlayerListener,
            BlockListener
        )

//        val protocolManager = ProtocolLibrary.getProtocolManager();
//        protocolManager.addPacketListener(ChatPacketAdapter)

        attachToGeary { autoscanComponents() }
    }

    override fun onDisable() {
        // Plugin shutdown logic
        Bukkit.getServer().removeRecipe(NamespacedKey.fromString(BonfireConfig.data.bonfireRecipe.key)!!)
//        ProtocolLibrary.getProtocolManager().removePacketListener(ChatPacketAdapter);

    }
}