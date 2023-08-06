package com.mineinabyss.bonfire

import com.destroystokyo.paper.profile.PlayerProfile
import com.google.common.collect.Ordering.compound
import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.bonfire.components.BonfireCooldown
import com.mineinabyss.bonfire.components.BonfireRespawn
import com.mineinabyss.bonfire.extensions.addToOfflineMessager
import com.mineinabyss.bonfire.extensions.getOfflinePDC
import com.mineinabyss.bonfire.extensions.saveOfflinePDC
import com.mineinabyss.bonfire.extensions.updateBonfireState
import com.mineinabyss.geary.papermc.datastore.decode
import com.mineinabyss.geary.papermc.datastore.encode
import com.mineinabyss.geary.papermc.datastore.remove
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.idofront.commands.arguments.intArg
import com.mineinabyss.idofront.commands.arguments.offlinePlayerArg
import com.mineinabyss.idofront.commands.arguments.playerArg
import com.mineinabyss.idofront.commands.arguments.stringArg
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
import org.bukkit.entity.ItemDisplay
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
                val offlinePlayer: OfflinePlayer by offlinePlayerArg()
                "get" {
                    action {
                        val respawn = when {
                            offlinePlayer.isOnline -> offlinePlayer.player?.toGearyOrNull()?.get<BonfireRespawn>()
                            else -> offlinePlayer.getOfflinePDC()?.decode<BonfireRespawn>()
                        }?.bonfireLocation ?: return@action sender.error("Could not find BonfireRespawn for the given OfflinePlayer")
                        sender.info("Bonfire-Respawn for ${offlinePlayer.name} is at ${respawn.x}, ${respawn.y}, ${respawn.z} in ${respawn.world.name}")
                    }
                }
                "set" {
                    val x: Int by intArg { default = (sender as? Player)?.location?.toCenterLocation()?.blockX }
                    val y: Int by intArg { default = (sender as? Player)?.location?.toCenterLocation()?.blockY }
                    val z: Int by intArg { default = (sender as? Player)?.location?.toCenterLocation()?.blockZ }
                    val worldName: String by stringArg { default = (sender as? Player)?.world?.name ?: "world" }
                    action {
                        val pdc = offlinePlayer.getOfflinePDC() ?: return@action sender.error("Could not find PDC for the given OfflinePlayer")
                        val world = Bukkit.getWorld(worldName) ?: return@action sender.error("Could not find world $worldName")
                        val tempBonfireLoc = Location(world, x.toDouble(), y.toDouble(), z.toDouble()).toCenterLocation()
                        if (!tempBonfireLoc.isChunkLoaded) tempBonfireLoc.world.getChunkAtAsync(tempBonfireLoc)
                        val bonfireEntity = world.getNearbyEntitiesByType(ItemDisplay::class.java, tempBonfireLoc, 0.5).firstOrNull()

                        bonfireEntity?.toGearyOrNull()?.get<Bonfire>()?.let { bonfire ->
                            when {
                                offlinePlayer.uniqueId in bonfire.bonfirePlayers -> return@action sender.error("Player is already registered to this bonfire")
                                bonfire.bonfirePlayers.size >= bonfire.maxPlayerCount -> return@action sender.error("Bonfire is full")
                                else -> {
                                    bonfireEntity.toGeary().setPersisting(bonfire.copy(bonfirePlayers = bonfire.bonfirePlayers + offlinePlayer.uniqueId))
                                    pdc.encode(BonfireRespawn(bonfireEntity.uniqueId, bonfireEntity.location))
                                    offlinePlayer.saveOfflinePDC(pdc)
                                    bonfireEntity.updateBonfireState()
                                    sender.success("Set respawn point for ${offlinePlayer.name} to $tempBonfireLoc")
                                }
                            }
                        } ?: sender.error("Could not find bonfire at $x $y $z")
                    }
                }
                "remove" {
                    action {
                        val respawn = when {
                            offlinePlayer.isOnline -> offlinePlayer.player?.toGearyOrNull()?.get<BonfireRespawn>()
                            else -> {
                                val pdc = offlinePlayer.getOfflinePDC() ?: return@action sender.error("Could not find PDC for the given OfflinePlayer")
                                val respawn = pdc.decode<BonfireRespawn>() ?: return@action sender.error("Could not find PDC for the given OfflinePlayer")
                                pdc.remove<BonfireRespawn>()
                                offlinePlayer.saveOfflinePDC(pdc)
                                respawn
                            }
                        } ?: return@action sender.error("Player has no respawn point set")

                        if (offlinePlayer.isOnline) offlinePlayer.player?.toGearyOrNull()?.remove<BonfireRespawn>()

                        // Remove component of bonfire if it exists still
                        if (!respawn.bonfireLocation.isChunkLoaded) respawn.bonfireLocation.world.getChunkAtAsync(respawn.bonfireLocation).thenAccept {
                            val bonfireEntity = Bukkit.getEntity(respawn.bonfireUuid) as? ItemDisplay
                            bonfireEntity?.toGeary()?.get<Bonfire>()?.let { bonfire ->
                                bonfireEntity.toGeary().setPersisting(bonfire.copy(bonfirePlayers = bonfire.bonfirePlayers - offlinePlayer.uniqueId))
                                if (bonfire.bonfirePlayers.isEmpty()) bonfireEntity.updateBonfireState()
                            }
                        }
                    }
                }
            }
            "players" {
                val offlinePlayer: OfflinePlayer by offlinePlayerArg()
                val x: Int by intArg { default = (sender as? Player)?.location?.toCenterLocation()?.blockX }
                val y: Int by intArg { default = (sender as? Player)?.location?.toCenterLocation()?.blockY }
                val z: Int by intArg { default = (sender as? Player)?.location?.toCenterLocation()?.blockZ }
                val worldName: String by stringArg { default = (sender as? Player)?.world?.name ?: "world" }
                action {

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
