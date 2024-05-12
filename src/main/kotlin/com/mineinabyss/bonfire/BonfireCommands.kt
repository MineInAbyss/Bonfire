package com.mineinabyss.bonfire

import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.bonfire.components.BonfireDebug
import com.mineinabyss.bonfire.components.BonfireRespawn
import com.mineinabyss.bonfire.extensions.updateBonfireState
import com.mineinabyss.geary.papermc.datastore.decode
import com.mineinabyss.geary.papermc.datastore.encode
import com.mineinabyss.geary.papermc.datastore.remove
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.geary.serialization.setPersisting
import com.mineinabyss.idofront.commands.arguments.intArg
import com.mineinabyss.idofront.commands.arguments.offlinePlayerArg
import com.mineinabyss.idofront.commands.arguments.playerArg
import com.mineinabyss.idofront.commands.arguments.stringArg
import com.mineinabyss.idofront.commands.execution.IdofrontCommandExecutor
import com.mineinabyss.idofront.commands.extensions.actions.playerAction
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.info
import com.mineinabyss.idofront.messaging.success
import com.mineinabyss.idofront.nms.nbt.editOfflinePDC
import com.mineinabyss.idofront.nms.nbt.getOfflinePDC
import com.mineinabyss.idofront.nms.nbt.saveOfflinePDC
import com.mineinabyss.idofront.plugin.actions
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player


class BonfireCommands : IdofrontCommandExecutor(), TabCompleter {
    override val commands = commands(bonfire.plugin) {
        command("bonfire") {
            "debug" {
                playerAction {
                    when {
                        player.toGeary().has<BonfireDebug>() -> {
                            player.toGeary().remove<BonfireDebug>()
                            sender.error("Bonfire debug mode disabled")
                        }
                        else -> {
                            player.toGeary().setPersisting(BonfireDebug())
                            sender.success("Bonfire debug mode enabled")
                        }
                    }
                }
            }
            "reload" {
                actions {
                    bonfire.plugin.registerBonfireContext()
                    sender.success("Bonfire configs have been reloaded!")
                }
            }
            "respawn" {
                val offlinePlayer: OfflinePlayer by offlinePlayerArg()
                "get" {
                    action {
                        val respawn = when {
                            offlinePlayer.isOnline -> offlinePlayer.player?.toGearyOrNull()?.get<BonfireRespawn>()
                            else -> offlinePlayer.getOfflinePDC()?.decode<BonfireRespawn>()
                        }?.bonfireLocation
                            ?: return@action sender.error("Could not find BonfireRespawn for the given Player")
                        sender.info("Bonfire-Respawn for ${offlinePlayer.name} is at ${respawn.x}, ${respawn.y}, ${respawn.z} in ${respawn.world.name}")
                    }
                }
                "set" {
                    val x: Int by intArg { default = (sender as? Player)?.location?.toCenterLocation()?.blockX }
                    val y: Int by intArg { default = (sender as? Player)?.location?.toCenterLocation()?.blockY }
                    val z: Int by intArg { default = (sender as? Player)?.location?.toCenterLocation()?.blockZ }
                    val worldName: String by stringArg { default = (sender as? Player)?.world?.name ?: "world" }
                    action {
                        // Ensures the player has a datafile, aka joined the server before, so we can save the bonfire location
                        offlinePlayer.getOfflinePDC() ?: return@action sender.error("Could not find PDC for the given OfflinePlayer")
                        val world =
                            Bukkit.getWorld(worldName) ?: return@action sender.error("Could not find world $worldName")
                        val tempBonfireLoc =
                            Location(world, x.toDouble(), y.toDouble(), z.toDouble()).toCenterLocation()

                        tempBonfireLoc.world.getChunkAtAsync(tempBonfireLoc).thenAccept {
                            val bonfireEntity =
                                world.getNearbyEntitiesByType(ItemDisplay::class.java, tempBonfireLoc, 0.5)
                                    .firstOrNull()

                            bonfireEntity?.toGearyOrNull()?.get<Bonfire>()?.let { bonfire ->
                                when {
                                    offlinePlayer.uniqueId in bonfire.bonfirePlayers ->
                                        sender.error("Player is already registered to this bonfire")
                                    bonfire.bonfirePlayers.size >= bonfire.maxPlayerCount ->
                                        sender.error("Bonfire is full")
                                    else -> {
                                        offlinePlayer.editOfflinePDC {
                                            encode(BonfireRespawn(bonfireEntity.uniqueId, bonfireEntity.location))
                                        }
                                        bonfire.bonfirePlayers += offlinePlayer.uniqueId
                                        bonfireEntity.updateBonfireState()
                                        sender.success("Set respawn point for ${offlinePlayer.name} to $x $y $z in $worldName")
                                    }
                                }
                            } ?: sender.error("Could not find bonfire at $x $y $z")
                        }
                    }
                }
                "remove" {
                    action {
                        val respawn = when {
                            offlinePlayer.isOnline -> {
                                val respawn = offlinePlayer.player?.toGearyOrNull()?.get<BonfireRespawn>()
                                offlinePlayer.player?.toGeary()?.remove<BonfireRespawn>()
                                respawn
                            }
                            else -> {
                                val pdc = offlinePlayer.getOfflinePDC() ?: return@action sender.error("Could not find PDC for the given OfflinePlayer")
                                val respawn = pdc.decode<BonfireRespawn>() ?: return@action sender.error("OfflinePlayer has no bonfire set")
                                pdc.remove<BonfireRespawn>()
                                offlinePlayer.saveOfflinePDC(pdc)
                                respawn
                            }
                        } ?: return@action sender.error("Player has no respawn point set")

                        // Remove component of bonfire if it exists still
                        respawn.bonfireLocation.world.getChunkAtAsync(respawn.bonfireLocation).thenAccept {
                            val bonfireEntity = Bukkit.getEntity(respawn.bonfireUuid) as? ItemDisplay
                            bonfireEntity?.toGeary()?.get<Bonfire>()?.let { bonfire ->
                                bonfire.bonfirePlayers -= offlinePlayer.uniqueId
                                if (bonfire.bonfirePlayers.isEmpty()) bonfireEntity.updateBonfireState()
                            }
                        }
                    }
                }
            }
            "players" {
                val x: Int by intArg { default = (sender as? Player)?.location?.toCenterLocation()?.blockX }
                val y: Int by intArg { default = (sender as? Player)?.location?.toCenterLocation()?.blockY }
                val z: Int by intArg { default = (sender as? Player)?.location?.toCenterLocation()?.blockZ }
                val worldName: String by stringArg { default = (sender as? Player)?.world?.name ?: "world" }
                action {
                    val bonfireLocation = Location(
                        Bukkit.getWorld(worldName) ?: return@action sender.error("Could not find world $worldName"),
                        x.toDouble(),
                        y.toDouble(),
                        z.toDouble()
                    ).toCenterLocation()

                    bonfireLocation.world.getChunkAtAsync(bonfireLocation).thenAccept {
                        val bonfireEntity =
                            bonfireLocation.world.getNearbyEntitiesByType(ItemDisplay::class.java, bonfireLocation, 0.5)
                                .firstOrNull()
                        bonfireEntity?.toGeary()?.get<Bonfire>()?.let { bonfire ->
                            sender.info(
                                "Players with their respawn set at this bonfire: ${
                                    bonfire.bonfirePlayers.joinToString(
                                        ", "
                                    ) { Bukkit.getOfflinePlayer(it).name ?: "Unknown" }
                                }"
                            )
                        }
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
            1 -> listOf("reload", "respawn", "players", "debug").filter { it.startsWith(args[0]) }
            2 -> when (args[0]) {
                "respawn" -> listOf("get", "set", "remove").filter { it.startsWith(args[1]) }
                "players" -> listOf((sender as? Player)?.location?.blockX.toString())
                "cooldown" -> listOf("clear")
                else -> emptyList()
            }

            3 -> when (args[0]) {
                "players" -> listOf((sender as? Player)?.location?.blockY.toString())
                "respawn" -> Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[2]) }
                else -> emptyList()
            }

            4 -> when {
                args[0] == "players" -> listOf((sender as? Player)?.location?.blockZ.toString())
                args[1] == "set" -> listOf((sender as? Player)?.location?.blockX.toString())
                else -> emptyList()
            }

            5 -> when {
                args[0] == "players" -> listOf((sender as? Player)?.world?.name ?: "world")
                args[1] == "set" -> listOf((sender as? Player)?.location?.blockY.toString())
                else -> emptyList()
            }

            6 -> when(args[1]) {
                 "set" -> listOf((sender as? Player)?.location?.blockZ.toString())
                else -> emptyList()
            }

            7 -> when (args[1]) {
                "set" -> listOf((sender as? Player)?.location?.world?.name ?: "world")
                else -> emptyList()
            }

            else -> emptyList()
        }
    }
}
