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
import com.mineinabyss.geary.papermc.gearyPaper
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.geary.papermc.withGeary
import com.mineinabyss.geary.serialization.setPersisting
import com.mineinabyss.idofront.commands.brigadier.IdoCommand
import com.mineinabyss.idofront.commands.brigadier.map
import com.mineinabyss.idofront.commands.brigadier.resolve
import com.mineinabyss.idofront.commands.brigadier.suggests
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
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player

fun IdoCommand.bonfireCommands() {
    "debug" {
        executes.asPlayer {
            player.withGeary {
                val gearyPlayer = player.toGeary()
                when {
                    gearyPlayer.has<BonfireDebug>() -> {
                        gearyPlayer.remove<BonfireDebug>()
                        sender.error("Bonfire debug mode disabled")
                    }

                    else -> {
                        gearyPlayer.setPersisting(BonfireDebug())
                        sender.success("Bonfire debug mode enabled")
                    }
                }
                gearyPlayer.encodeComponentsTo(player)
            }
        }
    }
    "players" {
        executes.asPlayer().args(
            "location" to ArgumentTypes.finePosition(true).suggests {
                suggestFiltering("${location.blockX} ${location.blockY} ${location.blockZ}")
            }.resolve().map { it.toLocation(executor!!.world) },
        ) { location ->
            val (x, y, z) = location.blockX() to location.blockY() to location.blockZ()

            location.world.getChunkAtAsync(location).thenAccept { chunk ->
                chunk.addPluginChunkTicket(bonfire)
                location.getNearbyEntitiesByType(ItemDisplay::class.java, 2.0)
                    .firstOrNull()?.toGeary()?.get<Bonfire>()?.let { bonfire ->
                        val bonfireNames =
                            bonfire.bonfirePlayers.joinToString(", ") { Bukkit.getOfflinePlayer(it).name ?: "Unknown" }
                        sender.info("Players with their respawn set at this bonfire: $bonfireNames")
                    } ?: sender.error("Could not find bonfire at $x $y $z")
            }

        }
    }

    "respawn" {
        "get" {
            executes.args(
                "offlinePlayer" to StringArgumentType.word().map { Bukkit.getOfflinePlayer(it) }
                    .default { (executor as? Player) ?: fail("Must be a valid player or offlinePlayer") }
            ) { offlinePlayer ->
                val respawn = when {
                    offlinePlayer.isOnline -> offlinePlayer.player?.toGearyOrNull()?.get<BonfireRespawn>()
                    else -> with(gearyPaper.worldManager.global) {
                        offlinePlayer.getOfflinePDC()?.decode<BonfireRespawn>()
                    }
                }?.bonfireLocation
                    ?: return@args sender.error("Could not find BonfireRespawn for the given Player")
                sender.info("Bonfire-Respawn for ${offlinePlayer.name} is at ${respawn.x}, ${respawn.y}, ${respawn.z} in ${respawn.world.name}")
            }
        }
        "set" {
            executes.asPlayer().args(
                "offlinePlayer" to StringArgumentType.word().map { Bukkit.getOfflinePlayer(it) }
                    .default { (executor as? Player) ?: fail("Must be a valid player or offlinePlayer") },
                "location" to ArgumentTypes.finePosition(true).suggests {
                    suggestFiltering("${location.blockX} ${location.blockY} ${location.blockZ}")
                }.resolve().map { it.toLocation(executor!!.world) },
            ) { offlinePlayer, location ->
                // Ensures the player has a datafile, aka joined the server before, so we can save the bonfire location
                offlinePlayer.getOfflinePDC()
                    ?: return@args sender.error("Could not find PDC for the given OfflinePlayer")
                val bonfireLoc = location.toBlockCenterLocation()
                val (x, y, z) = bonfireLoc.blockX to bonfireLoc.blockY to bonfireLoc.blockZ

                player.world.getChunkAtAsync(location).thenAccept {
                    val bonfireEntity = bonfireLoc.getNearbyEntitiesByType(ItemDisplay::class.java, 0.5).firstOrNull()

                    bonfireEntity?.toGearyOrNull()?.get<Bonfire>()?.let { bonfire ->
                        when {
                            offlinePlayer.uniqueId in bonfire.bonfirePlayers ->
                                sender.error("Player is already registered to this bonfire")

                            bonfire.bonfirePlayers.size >= bonfire.maxPlayerCount ->
                                sender.error("Bonfire is full")

                            else -> with(gearyPaper.worldManager.global) {
                                offlinePlayer.editOfflinePDC {
                                    encode(BonfireRespawn(bonfireEntity.uniqueId, bonfireEntity.location))
                                }
                                bonfire.bonfirePlayers += offlinePlayer.uniqueId
                                bonfireEntity.updateBonfireState()
                                sender.success("Set respawn point for ${offlinePlayer.name} to $x $y $z in ${player.world.name}")
                            }
                        }
                    } ?: sender.error("Could not find bonfire at $x $y $z")
                }
            }
        }
        "remove" {
            executes.args(
                "offlinePlayer" to StringArgumentType.word().map { Bukkit.getOfflinePlayer(it) }
                    .default { (executor as? Player) ?: fail("Must be a valid player or offlinePlayer") }
            ) { offlinePlayer ->
                val respawn = when {
                    offlinePlayer.isOnline -> {
                        val respawn = offlinePlayer.player?.toGearyOrNull()?.get<BonfireRespawn>()
                        offlinePlayer.player?.toGeary()?.remove<BonfireRespawn>()
                        respawn
                    }

                    else -> {
                        with(gearyPaper.worldManager.global) {
                            val pdc = offlinePlayer.getOfflinePDC()
                                ?: fail("Could not find PDC for the given OfflinePlayer")
                            val respawn = pdc.decode<BonfireRespawn>()
                                ?: fail("OfflinePlayer has no bonfire set")
                            pdc.remove<BonfireRespawn>()
                            offlinePlayer.saveOfflinePDC(pdc)
                            respawn
                        }
                    }
                } ?: fail("Player has no respawn point set")

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