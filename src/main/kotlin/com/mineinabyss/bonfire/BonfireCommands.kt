package com.mineinabyss.bonfire

import com.mineinabyss.blocky.helpers.GenericHelpers.toBlockCenterLocation
import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.bonfire.components.BonfireDebug
import com.mineinabyss.bonfire.components.BonfireRespawn
import com.mineinabyss.bonfire.extensions.updateBonfireState
import com.mineinabyss.geary.papermc.datastore.decode
import com.mineinabyss.geary.papermc.datastore.encode
import com.mineinabyss.geary.papermc.datastore.encodeComponentsTo
import com.mineinabyss.geary.papermc.datastore.remove
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.geary.serialization.setPersisting
import com.mineinabyss.idofront.commands.brigadier.IdoCommandContext
import com.mineinabyss.idofront.commands.brigadier.commands
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.info
import com.mineinabyss.idofront.messaging.success
import com.mineinabyss.idofront.nms.nbt.editOfflinePDC
import com.mineinabyss.idofront.nms.nbt.getOfflinePDC
import com.mineinabyss.idofront.nms.nbt.saveOfflinePDC
import com.mineinabyss.idofront.util.to
import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player

object BonfireCommands {
    fun registerCommands() {
        bonfire.plugin.commands {
            "bonfire" {
                "debug" {
                    playerExecutes {
                        with(player.toGeary()) {
                            when {
                                has<BonfireDebug>() -> {
                                    remove<BonfireDebug>()
                                    sender.error("Bonfire debug mode disabled")
                                }
                                else -> {
                                    setPersisting(BonfireDebug())
                                    sender.success("Bonfire debug mode enabled")
                                }
                            }
                            encodeComponentsTo(player)
                        }

                    }
                }
                "reload" {
                    executes {
                        bonfire.plugin.registerBonfireContext()
                        sender.success("Bonfire configs have been reloaded!")
                    }
                }
                "players" {
                    fun IdoCommandContext.runPlayersCommand(location: Location) {
                        val bonfireLoc = location.toBlockCenterLocation()
                        val (x,y,z) = bonfireLoc.blockX to bonfireLoc.blockY to bonfireLoc.blockZ

                        location.world.getChunkAtAsync(location).thenAccept {
                            val bonfireEntity = bonfireLoc.getNearbyEntitiesByType(ItemDisplay::class.java, 5.0).firstOrNull()
                            bonfireEntity?.toGeary()?.get<Bonfire>()?.let { bonfire ->
                                sender.info(
                                    "Players with their respawn set at this bonfire: ${
                                        bonfire.bonfirePlayers.joinToString(", ") { Bukkit.getOfflinePlayer(it).name ?: "Unknown" }
                                    }"
                                )
                            } ?: sender.error("Could not find bonfire at $x $y $z")
                        }
                    }

                    executes { context.source.executor?.location?.let { runPlayersCommand(it) } }
                    val location by ArgumentTypes.blockPosition().suggests {
                        (context.source.executor as? Player)?.location?.let {
                            suggestFiltering("${it.blockX} ${it.blockY} ${it.blockZ}")
                        }
                    }
                    executes { runPlayersCommand(location().toLocation(context.source.location.world)!!) }
                    val world by ArgumentTypes.world().suggests {
                        suggest(Bukkit.getWorlds().map { it.key.asString() })
                    }
                    executes { runPlayersCommand(location().toLocation(world()!!)) }
                }
                "respawn" {
                    val offlinePlayer by StringArgumentType.word()
                    "get" {
                        executes {
                            val offlinePlayer = Bukkit.getOfflinePlayer(offlinePlayer())
                            val respawn = when {
                                offlinePlayer.isOnline -> offlinePlayer.player?.toGearyOrNull()?.get<BonfireRespawn>()
                                else -> offlinePlayer.getOfflinePDC()?.decode<BonfireRespawn>()
                            }?.bonfireLocation ?: return@executes sender.error("Could not find BonfireRespawn for the given Player")
                            sender.info("Bonfire-Respawn for ${offlinePlayer.name} is at ${respawn.x}, ${respawn.y}, ${respawn.z} in ${respawn.world.name}")
                        }
                    }
                    "set" {
                        val location by ArgumentTypes.blockPosition().suggests {
                            (context.source.executor as? Player)?.location?.let {
                                suggestFiltering("${it.blockX} ${it.blockY} ${it.blockZ}")
                            }
                        }

                        fun IdoCommandContext.handleRespawnSet(offlinePlayer: OfflinePlayer, location: Location) {
                            // Ensures the player has a datafile, aka joined the server before, so we can save the bonfire location
                            offlinePlayer.getOfflinePDC() ?: return sender.error("Could not find PDC for the given OfflinePlayer")
                            val world = location.world
                            val bonfireLoc = location.toBlockCenterLocation()
                            val (x,y,z) = bonfireLoc.blockX to bonfireLoc.blockY to bonfireLoc.blockZ

                            world.getChunkAtAsync(location).thenAccept {
                                val bonfireEntity = bonfireLoc.getNearbyEntitiesByType(ItemDisplay::class.java, 0.5).firstOrNull()

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
                                            sender.success("Set respawn point for ${offlinePlayer.name} to $x $y $z in ${world.name}")
                                        }
                                    }
                                } ?: sender.error("Could not find bonfire at $x $y $z")
                            }
                        }
                        executes {
                            handleRespawnSet(Bukkit.getOfflinePlayer(offlinePlayer()), location().toLocation(context.source.location.world))
                        }
                        val world by ArgumentTypes.world().suggests {
                            suggest(Bukkit.getWorlds().map { it.key.asString() })
                        }
                        executes {
                            handleRespawnSet(Bukkit.getOfflinePlayer(offlinePlayer()), location().toLocation(world()!!))
                        }
                    }
                    "remove" {
                        executes {
                            val offlinePlayer = Bukkit.getOfflinePlayer(offlinePlayer())
                            val respawn = when {
                                offlinePlayer.isOnline -> {
                                    val respawn = offlinePlayer.player?.toGearyOrNull()?.get<BonfireRespawn>()
                                    offlinePlayer.player?.toGeary()?.remove<BonfireRespawn>()
                                    respawn
                                }
                                else -> {
                                    val pdc = offlinePlayer.getOfflinePDC() ?: return@executes sender.error("Could not find PDC for the given OfflinePlayer")
                                    val respawn = pdc.decode<BonfireRespawn>() ?: return@executes sender.error("OfflinePlayer has no bonfire set")
                                    pdc.remove<BonfireRespawn>()
                                    offlinePlayer.saveOfflinePDC(pdc)
                                    respawn
                                }
                            } ?: return@executes sender.error("Player has no respawn point set")

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
            }
        }
    }
}
