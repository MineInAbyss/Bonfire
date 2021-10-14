package com.mineinabyss.bonfire

import com.mineinabyss.bonfire.config.BonfireConfig
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.bonfire.extensions.removeBonfireSpawnLocation
import com.mineinabyss.bonfire.extensions.setRespawnLocation
import com.mineinabyss.bonfire.extensions.updateBonfire
import com.mineinabyss.bonfire.extensions.uuid
import com.mineinabyss.idofront.commands.CommandHolder
import com.mineinabyss.idofront.commands.arguments.intArg
import com.mineinabyss.idofront.commands.arguments.stringArg
import com.mineinabyss.idofront.commands.execution.ExperimentalCommandDSL
import com.mineinabyss.idofront.commands.execution.IdofrontCommandExecutor
import com.mineinabyss.idofront.commands.execution.stopCommand
import com.mineinabyss.idofront.commands.extensions.actions.ensureSenderIsPlayer
import com.mineinabyss.idofront.commands.extensions.actions.playerAction
import com.mineinabyss.idofront.messaging.*
import com.okkero.skedule.BukkitSchedulerController
import com.okkero.skedule.CoroutineTask
import com.okkero.skedule.schedule
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.block.Campfire
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

@ExperimentalCommandDSL
object BonfireCommandExecutor : IdofrontCommandExecutor() {
    override val commands: CommandHolder = commands(bonfirePlugin) {
        ("bonfire" / "bf")(desc = "Commands for Bonfire") {
            "respawn"(desc = "Commands to manipulate the Bonfire respawn of players") {
                val targetPlayerStr by stringArg { name = "player" }

                "get"(desc = "Get the player respawn from the database") {
                    playerAction {
                        val offlineTargets = Bukkit.getOfflinePlayers()
                            .filter { it.name == targetPlayerStr }
                            .distinctBy { it.uniqueId }
                        val offlineTargetsUUIDs = offlineTargets.map { it.uniqueId }

                        if (offlineTargets.isEmpty()) {
                            command.stopCommand("No player found with that name")
                        }

                        if (offlineTargets.size > 1) {
                            sender.info("Multiple players found with that name, checking respawn for all.")
                        }

                        transaction {
                            val dbPlayers = Players
                                .leftJoin(Bonfire, { bonfireUUID }, { entityUUID })
                                .select { Players.playerUUID inList offlineTargetsUUIDs }

                            offlineTargets.forEach { player ->
                                val dbPlayer = dbPlayers.firstOrNull { it[Players.playerUUID] == player.uniqueId }

                                if (dbPlayer == null) {
                                    sender.info("Player ${player.name} does not have a bonfire respawn set.")
                                } else {
                                    if (!dbPlayer.hasValue(Bonfire.entityUUID)) {
                                        sender.error("Bonfire for player ${player.name} not found in the database. This is bad and should not happen!")
                                    } else {
                                        sender.info("Bonfire for player ${player.name} is at ${dbPlayer[Bonfire.location]}.")
                                    }
                                }
                            }
                        }
                    }
                }
                "set"(desc = "Set the player bonfire respawn point in the database. Ignores bonfire max player limit. Need to be in the world of the bonfire!") {
                    val bonfireLocX by intArg { name = "X" }
                    val bonfireLocY by intArg { name = "Y" }
                    val bonfireLocZ by intArg { name = "Z" }

                    playerAction {
                        val offlineTargets = Bukkit.getOfflinePlayers()
                            .filter { it.name == targetPlayerStr }
                            .distinctBy { it.uniqueId }

                        if (offlineTargets.isEmpty()) {
                            command.stopCommand("No player found with that name")
                        }

                        if (offlineTargets.size > 1) {
                            command.stopCommand("Multiple players found with that name, not setting respawn.")
                        }

                        val bonfireUUID = (Location(
                            player.world,
                            bonfireLocX.toDouble(),
                            bonfireLocY.toDouble(),
                            bonfireLocZ.toDouble()
                        ).block.state as? Campfire)?.uuid

                        if (bonfireUUID == null) {
                            command.stopCommand("No bonfire found at this location.")
                        } else {
                            val targetPlayer = offlineTargets.first()
                            targetPlayer.setRespawnLocation(bonfireUUID)
                            sender.info("Respawn set for player ${targetPlayer.name}")
                        }
                    }
                }
                "remove"(desc = "Remove the player bonfire respawn point.") {
                    playerAction {
                        val offlineTargets = Bukkit.getOfflinePlayers()
                            .filter { it.name == targetPlayerStr }
                            .distinctBy { it.uniqueId }

                        if (offlineTargets.isEmpty()) {
                            command.stopCommand("No player found with that name.")
                        }

                        if (offlineTargets.size > 1) {
                            command.stopCommand("Multiple players found with that name, not removing respawn.")
                        }

                        val targetPlayer = offlineTargets.first()

                        transaction {
                            val bonfireUUID = Players
                                .select { Players.playerUUID eq targetPlayer.uniqueId }
                                .firstOrNull()?.get(Players.bonfireUUID)

                            if (bonfireUUID == null) {
                                command.stopCommand("Player does not have a respawn set.")
                            } else {
                                if (targetPlayer.removeBonfireSpawnLocation(bonfireUUID)) {
                                    sender.info("Respawn removed from player ${targetPlayer.name}.")
                                } else {
                                    sender.error("Failed to remove respawn from player ${targetPlayer.name}.")
                                }
                            }
                        }
                    }
                }
            }
            "info"(desc = "Commands to get bonfire info") {
                val bonfireLocX by intArg { name = "X" }
                val bonfireLocY by intArg { name = "Y" }
                val bonfireLocZ by intArg { name = "Z" }
                "dbcheck"(desc = "Check if a bonfire at location is stored in the database") {
                    ensureSenderIsPlayer()
                    val player = sender as Player
                    val bonfireUUID = (Location(
                        player.world,
                        bonfireLocX.toDouble(),
                        bonfireLocY.toDouble(),
                        bonfireLocZ.toDouble()
                    ).block.state as? Campfire)?.uuid

                    if (bonfireUUID == null) {
                        stopCommand("No bonfire found at this location.")
                    } else {
                        transaction {
                            if (Bonfire.select { Bonfire.entityUUID eq bonfireUUID }.any()) {
                                sender.success("Bonfire is registered in the database.")
                            } else {
                                sender.error("Bonfire is not registered in the database.")
                            }
                        }

                    }
                }
                "players"(desc = "Get the players registered with the bonfire at location") {
                    ensureSenderIsPlayer()
                    val player = sender as Player
                    val bonfireUUID = (Location(
                        player.world,
                        bonfireLocX.toDouble(),
                        bonfireLocY.toDouble(),
                        bonfireLocZ.toDouble()
                    ).block.state as? Campfire)?.uuid

                    if (bonfireUUID == null) {
                        stopCommand("No bonfire found at this location.")
                    } else {
                        transaction {
                            val registeredPlayers = Players.select { Players.bonfireUUID eq bonfireUUID }

                            if (registeredPlayers.empty()) {
                                sender.info("No players registered with this bonfire.")
                            } else {
                                val playersString = registeredPlayers.map { registeredPlayer ->
                                    Bukkit.getOfflinePlayers().first {
                                        it.uniqueId == registeredPlayer[Players.playerUUID]
                                    }.name
                                }.joinToString(",")
                                sender.info("The following players are registered with this bonfire: $playersString")
                            }
                        }
                    }

                }
            }
            "give"(desc = "Give yourself a bonfire") { //TODO: Add this command to idofront/MiA for any custom item
                playerAction {
                    player.inventory.addItem(BonfireConfig.data.bonfireItem.toItemStack())
                }
            }
            "updateAllModels"(desc = "Clear any armorstands associated with bonfires and update model of all bonfires.") {
                pauseExpirationChecks = true
                transaction {
                    val bonfireLocations = Bonfire.slice(Bonfire.location).selectAll()
                        .groupBy(keySelector = { it[Bonfire.location].chunk },
                            valueTransform = { it[Bonfire.location] })

                    sender.warn("Starting chunk scan. &l&4DO NOT MESS WITH BONFIRES UNTIL DONE".color())
                    sender.warn("Total chunks to scan: " + bonfireLocations.keys.size)

                    val tasks = mutableListOf<CoroutineTask>()
                    bonfireLocations.forEach { (chunk, locations) ->
                        tasks.add(bonfirePlugin.schedule {
                            sender.warn("Processing chunk $chunk")
                            updateChunkBonfires(chunk, locations)
                        })
                    }

                    bonfirePlugin.schedule {
                        repeating(20)
                        val scheduler = Bukkit.getScheduler()
                        var tasksAreFinished = false
                        while (!tasksAreFinished) {
                            yield()
                            tasks.forEach {
                                val currentTask = it.currentTask
                                if (currentTask != null && scheduler.isCurrentlyRunning(currentTask.taskId)) {
                                    tasksAreFinished = false
                                    return@forEach
                                }
                            }
                            tasksAreFinished = true
                        }
                        sender.success("Chunk scan finished.")
                        pauseExpirationChecks = false
                    }
                }
            }
        }
    }

    private suspend fun BukkitSchedulerController.updateChunkBonfires(chunk: Chunk, bfLocations: List<Location>) {
        chunk.load()

        val bonfireArmorstands = chunk.entities
            .filterIsInstance<ArmorStand>()
            .filter { it.isMarker && it.location in bfLocations }

        bonfireArmorstands.forEach {
            it.remove()
            yield()
        }

        bfLocations.forEach {
            (it.block.state as? Campfire)?.updateBonfire()
            yield()
        }
    }
}