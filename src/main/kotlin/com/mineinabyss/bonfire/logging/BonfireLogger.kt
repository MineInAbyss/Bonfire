package com.mineinabyss.bonfire.logging

import com.mineinabyss.bonfire.bonfirePlugin
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.Path

object BonfireLogger {
    private val logFile: Path = Path(bonfirePlugin.dataFolder.path, "log.log")

    fun logBonfireBreak(location: Location, source: Player) {
        internalLog("The player ${source.name} broke a bonfire at $location", BonfireLogEvent.BREAK)
    }

    fun logBonfirePlace(location: Location, source: Player) {
        internalLog("The player ${source.name} placed a bonfire at $location", BonfireLogEvent.PLACE)
    }

    fun logRespawnSet(location: Location, player: OfflinePlayer) {
        internalLog("Respawn point set for player ${player.name} at $location", BonfireLogEvent.RESPAWN_SET)
    }

    fun logRespawnUnset(location: Location, player: OfflinePlayer) {
        internalLog("Respawn point unset for player ${player.name} at $location", BonfireLogEvent.RESPAWN_UNSET)
    }

    fun logBonfireExpired(location: Location) {
        internalLog("The bonfire at $location expired and has been removed", BonfireLogEvent.EXPIRED)
    }

    fun logRespawnFailed(player: Player, location: Location) {
        internalLog(
            "The player ${player.name} tried to respawn, but bonfire was missing at $location",
            BonfireLogEvent.RESPAWN_FAILED
        )
    }

    fun logRespawnAtBonfire(player: Player, location: Location) {
        internalLog("The player ${player.name} respawned at their bonfire at $location", BonfireLogEvent.RESPAWN)
    }

    fun logRespawnAtWorldSpawn(player: Player) {
        internalLog(
            "The player ${player.name} respawned at world spawn (${player.server.worlds.first().spawnLocation})",
            BonfireLogEvent.RESPAWN
        )
    }

    private fun internalLog(message: String, event: BonfireLogEvent) {
        val logOutput = "[${
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        } ${event.name}] $message\n"

        logFile.toFile().appendText(logOutput)
    }

    private enum class BonfireLogEvent {
        BREAK,
        PLACE,
        RESPAWN_SET,
        RESPAWN_UNSET,
        EXPIRED,
        RESPAWN_FAILED,
        RESPAWN,
    }
}