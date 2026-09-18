package com.mineinabyss.bonfire

import com.mineinabyss.bonfire.extensions.BonfireMessages
import com.mineinabyss.bonfire.listeners.BonfireListener
import com.mineinabyss.bonfire.listeners.DebugListener
import com.mineinabyss.bonfire.listeners.FixUntrackedBonfiresListener
import com.mineinabyss.bonfire.listeners.NexoBonfireListener
import com.mineinabyss.bonfire.extensions.BonfireItemOverride
import com.mineinabyss.bonfire.listeners.PlayerListener
import com.nexomc.nexo.api.NexoFurniture
import com.mineinabyss.dependencies.DI
import com.mineinabyss.dependencies.get
import com.mineinabyss.dependencies.getLazy
import com.mineinabyss.dependencies.loadCatching
import com.mineinabyss.dependencies.scope
import com.mineinabyss.dependencies.single
import com.mineinabyss.geary.autoscan.autoscan
import com.mineinabyss.geary.papermc.gearyPaper
import com.mineinabyss.idofront.config.SingleConfig
import com.mineinabyss.idofront.features.MainCommand
import com.mineinabyss.idofront.features.MainCommandFeature
import com.mineinabyss.idofront.features.singleConfig
import com.mineinabyss.idofront.features.singlePluginLogger
import com.mineinabyss.idofront.messaging.ComponentLogger
import com.mineinabyss.idofront.plugin.Plugins
import com.mineinabyss.idofront.plugin.listeners
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin

val bonfire get() = BonfirePlugin.instance ?: error("Bonfire not loaded")

class BonfirePlugin : JavaPlugin(), DI {
    override val di = DI {
        single<Plugin> { this@BonfirePlugin }
        singlePluginLogger(this@BonfirePlugin)
        singleConfig<BonfireConfig>("config.yml") { default = BonfireConfig() }
        singleConfig<BonfireMessages>("messages.yml") { default = BonfireMessages() }
        single {
            MainCommand(
                names = listOf("bonfire"),
                description = "The main command for Bonfire",
                reloadCommandName = "reload",
                onBeforeReload = {
                    get<SingleConfig<BonfireConfig>>().updateCached()
                    get<SingleConfig<BonfireMessages>>().updateCached()
                }
            )
        }
    }

    val logger by di.getLazy<ComponentLogger>()
    val config by di.getLazy<BonfireConfig>()
    val messages by di.getLazy<BonfireMessages>()

    override fun onLoad() {
        instance = this@BonfirePlugin
    }

    override fun onEnable() {
        gearyPaper.configure {
            world.autoscan {
                scan(BonfirePlugin::class.java.classLoader, listOf("com.mineinabyss.bonfire")) {
                    all()
                }
            }
        }
        scope.loadCatching(BonfireAddon)
        scope.loadCatching(MainCommandFeature)

        listeners(
            PlayerListener(),
            BonfireListener(),
            DebugListener(),
        )

        // Both listeners talk to Nexo's furniture API, without it there is no furniture to place or adopt
        if (Plugins.isEnabled("Nexo")) {
            listeners(FixUntrackedBonfiresListener(), NexoBonfireListener())
            // Scoped, so Nexo keeps sending one shared packet for everyone else's furniture
            NexoFurniture.registerItemOverride(this, BonfireItemOverride, setOf(config.nexoFurnitureId))
        }
    }

    override fun onDisable() {
        // Not gated on Nexo being enabled, it may already be disabling and would then keep us registered
        runCatching { NexoFurniture.unregisterItemOverrides(this) }
        di.close()
    }

    companion object {
        var instance: BonfirePlugin? = null
    }
}
