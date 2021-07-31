package com.mineinabyss.bonfire.extensions

import com.mineinabyss.bonfire.components.save
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.idofront.messaging.success
import org.bukkit.block.Campfire
import org.bukkit.entity.Player
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

fun Player.setRespawnLocation(bonfireUUID: UUID) {
    val playerUUID = uniqueId

    transaction {
        val playerRow = Players
            .select { Players.playerUUID eq playerUUID }
            .firstOrNull()

        if (playerRow != null && playerRow[Players.bonfireUUID] != bonfireUUID) {
            val oldBonfireBlock = Bonfire
                .select { Bonfire.entityUUID eq playerRow[Players.bonfireUUID] }
                .first()[Bonfire.location]
                .block.state as? Campfire ?: return@transaction

            Players.update({ Players.playerUUID eq playerRow[Players.playerUUID] }) {
                it[Players.bonfireUUID] = bonfireUUID
            }

            oldBonfireBlock.bonfireData()?.save()
        } else if (playerRow == null) {
            Players.insert {
                it[Players.playerUUID] = playerUUID
                it[Players.bonfireUUID] = bonfireUUID
            }
        }

        val newBonfire = Bonfire
            .select { Bonfire.entityUUID eq bonfireUUID }
            .first()[Bonfire.location]
            .block.state as? Campfire ?: return@transaction

        newBonfire.bonfireData()?.save()

        success("Respawn point set")
    }
}


fun Player.removeBonfireSpawnLocation(bonfireUUID: UUID): Boolean {
    val playerUUID = uniqueId
    transaction {
        val deleteReturnCode = Players
            .deleteWhere {
                (Players.playerUUID eq playerUUID) and (Players.bonfireUUID eq bonfireUUID)
            }
        if (deleteReturnCode == 0) return@transaction false
        success("Respawn point has been removed")
        val bonfire = Bonfire
            .select { Bonfire.entityUUID eq bonfireUUID }
            .first()[Bonfire.location].block.state as? Campfire ?: return@transaction false
        bonfire.bonfireData()?.save() ?: return@transaction false
    }
    return true
}