package com.mineinabyss.bonfire

import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.bonfire.extensions.BonfireMessages
import com.mineinabyss.bonfire.listeners.BonfireListener
import com.mineinabyss.bonfire.listeners.DebugListener
import com.mineinabyss.bonfire.listeners.FixUntrackedBonfiresListener
import com.mineinabyss.bonfire.listeners.PlayerListener
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
import com.mineinabyss.idofront.plugin.listeners
import org.bukkit.plugin.java.JavaPlugin

val bonfire = BonfirePlugin.instance ?: error("Bonfire not loaded")

class BonfirePlugin : JavaPlugin(), DI {
    override val di = DI {
        singlePluginLogger(this@BonfirePlugin)
        singleConfig<BonfireConfig>("config.yml") { default = BonfireConfig() }
        singleConfig<BonfireMessages>("messages.yml") { default = BonfireMessages() }
        single {
            MainCommand(
                names = listOf("deeperworld", "dw"),
                description = "The main command for DeeperWorld",
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
            FixUntrackedBonfiresListener()
        )
    }

    override fun onDisable() {
        di.close()
    }

    companion object {
        var instance: BonfirePlugin? = null
    }
}
