package com.mineinabyss.bonfire

import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.bonfire.components.BonfireCooldown
import com.mineinabyss.bonfire.components.BonfireRespawn
import com.mineinabyss.bonfire.extensions.addToOfflineMessager
import com.mineinabyss.bonfire.extensions.removeFromOfflineMessager
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.idofront.commands.arguments.offlinePlayerArg
import com.mineinabyss.idofront.commands.arguments.playerArg
import com.mineinabyss.idofront.commands.execution.IdofrontCommandExecutor
import com.mineinabyss.idofront.commands.extensions.actions.playerAction
import com.mineinabyss.idofront.entities.toOfflinePlayer
import com.mineinabyss.idofront.messaging.broadcast
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.logError
import com.mineinabyss.idofront.messaging.success
import com.mineinabyss.idofront.plugin.actions
import net.quazar.offlinemanager.api.nbt.TagValue
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class BonfireCommands : IdofrontCommandExecutor(), TabCompleter {
    override val commands = commands(bonfire.plugin) {
        command("bonfire") {
            "reload" {
                actions {
                    bonfire.plugin.registerBonfireContext()
                    sender.success("Bonfire configs have been reloaded!")
                }
            }
            "respawn" {
                "remove" {
                    val player: OfflinePlayer by offlinePlayerArg()
                    action {
                        player.player?.let {
                            it.toGeary().remove<BonfireRespawn>()
                            sender.success("Removed respawn point for ${player.name}")
                        } ?: player.uniqueId.addToOfflineMessager()
                    }
                }
            }
            "players" {

            }
            "clearCooldowns"(desc="Remove the cooldowns on players if they dont automatically") {
                val player: Player? by playerArg { default = null }
                action {
                    player?.let {
                        it.toGeary().remove<BonfireCooldown>()
                        sender.success("Removed cooldowns from player ${it.name}")
                    } ?: run {
                        Bukkit.getOnlinePlayers().forEach {
                            it.toGeary().remove<BonfireCooldown>()
                        }
                        sender.success("Removed cooldowns from all players")
                    }
                }
            }

        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (command.name != "bonfire") return emptyList()
        return when (args.size) {
            1 -> listOf("reload")
            else -> emptyList()
        }
    }
}
