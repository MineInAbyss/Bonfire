package com.mineinabyss.bonfire

import com.destroystokyo.paper.profile.PlayerProfile
import com.google.common.collect.Ordering.compound
import com.mineinabyss.bonfire.components.BonfireCooldown
import com.mineinabyss.bonfire.components.BonfireRespawn
import com.mineinabyss.bonfire.extensions.addToOfflineMessager
import com.mineinabyss.bonfire.extensions.getOfflinePDC
import com.mineinabyss.bonfire.extensions.saveOfflinePDC
import com.mineinabyss.geary.papermc.datastore.decode
import com.mineinabyss.geary.papermc.datastore.encode
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.idofront.commands.arguments.offlinePlayerArg
import com.mineinabyss.idofront.commands.arguments.playerArg
import com.mineinabyss.idofront.commands.execution.IdofrontCommandExecutor
import com.mineinabyss.idofront.messaging.*
import com.mineinabyss.idofront.nms.nbt.WrappedPDC
import com.mineinabyss.idofront.plugin.actions
import com.mojang.authlib.GameProfile
import net.minecraft.commands.arguments.UuidArgument.uuid
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.craftbukkit.v1_20_R1.CraftServer
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataContainer


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
                val offlinePlayer: OfflinePlayer by offlinePlayerArg()
                action {
                    val pdc = offlinePlayer.getOfflinePDC() ?: return@action sender.error("Could not find PDC for the given OfflinePlayer")
                    val bonfire = pdc.decode<BonfireRespawn>() ?: return@action sender.error("Player has no respawn point")
                    bonfire.bonfireLocation.let {
                        sender.info("Player ${offlinePlayer.name} has a respawn point at ${it.x}, ${it.y}, ${it.z}")
                    }
                    pdc.encode(bonfire.copy(bonfireLocation = Location(Bukkit.getWorld("world"), 0.0, 0.0, 0.0)))
                    pdc.compoundTag.allKeys.forEach { sender.info(it) }
                    offlinePlayer.saveOfflinePDC(pdc)
                }
            }
            "clearCooldowns"(desc = "Remove the cooldowns on players if they dont automatically") {
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

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (command.name != "bonfire") return emptyList()
        return when (args.size) {
            1 -> listOf("reload")
            2 -> {
                when (args[0]) {
                    "respawn" -> listOf("remove")
                    "clearCooldowns", "players" -> Bukkit.getOnlinePlayers().map { it.name }
                    else -> emptyList()
                }
            }

            else -> emptyList()
        }
    }
}
